"""
VeritasGuard — FastAPI Backend Gateway

Endpoints:
    POST /api/v1/analyze-url   → URL phishing/malware analysis via sandboxed browser
    POST /api/v1/check-hoax    → Text fact-verification via Gemini 1.5 Pro RAG engine

Infrastructure:
    - Redis volatile queue (no disk persistence)
    - Rate limiting via sliding window
    - mTLS-ready Uvicorn configuration
    - CORS middleware for mobile client

Run:
    uvicorn app.main:app --host 0.0.0.0 --port 8000 --ssl-keyfile key.pem --ssl-certfile cert.pem
"""

import os
import time
import uuid
import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, HttpUrl
import redis.asyncio as aioredis

from app.sandbox.url_analyzer import URLAnalyzer
from app.rag.hoax_checker import HoaxChecker


# =============================================================================
# Logging
# =============================================================================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
)
logger = logging.getLogger("veritasguard")


# =============================================================================
# Configuration
# =============================================================================

REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")
RATE_LIMIT_WINDOW = 60  # seconds
RATE_LIMIT_MAX_REQUESTS = 30  # per window
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")


# =============================================================================
# Lifespan (Startup / Shutdown)
# =============================================================================

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize Redis connection and services on startup."""
    logger.info("🚀 VeritasGuard backend starting...")

    # Redis: volatile mode (no disk persistence)
    app.state.redis = aioredis.from_url(
        REDIS_URL,
        encoding="utf-8",
        decode_responses=True,
    )

    # Configure Redis for volatile, memory-only operation
    try:
        await app.state.redis.config_set("save", "")
        await app.state.redis.config_set("appendonly", "no")
        await app.state.redis.config_set("maxmemory-policy", "allkeys-lru")
        logger.info("✅ Redis configured for volatile (no-disk) operation")
    except Exception as e:
        logger.warning(f"⚠️ Could not configure Redis volatile mode: {e}")

    # Initialize services
    app.state.url_analyzer = URLAnalyzer()
    app.state.hoax_checker = HoaxChecker(api_key=GEMINI_API_KEY)

    logger.info("✅ VeritasGuard backend ready")

    yield

    # Shutdown
    logger.info("🔻 Shutting down VeritasGuard backend...")
    await app.state.redis.close()
    await app.state.url_analyzer.close()


# =============================================================================
# FastAPI Application
# =============================================================================

app = FastAPI(
    title="VeritasGuard API",
    description="Security analysis API for phishing URL detection and misinformation verification",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url=None,
)

# CORS (restrict in production)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # TODO: Restrict to mobile app origin
    allow_credentials=True,
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)


# =============================================================================
# Rate Limiting Middleware
# =============================================================================

@app.middleware("http")
async def rate_limit_middleware(request: Request, call_next):
    """Sliding window rate limiter using Redis."""
    if request.url.path.startswith("/docs") or request.url.path == "/health":
        return await call_next(request)

    client_ip = request.client.host if request.client else "unknown"
    rate_key = f"rate:{client_ip}"

    try:
        redis: aioredis.Redis = request.app.state.redis
        current = await redis.get(rate_key)

        if current and int(current) >= RATE_LIMIT_MAX_REQUESTS:
            return JSONResponse(
                status_code=429,
                content={"error": "Rate limit exceeded. Try again later."},
            )

        pipe = redis.pipeline()
        pipe.incr(rate_key)
        pipe.expire(rate_key, RATE_LIMIT_WINDOW)
        await pipe.execute()

    except Exception as e:
        logger.warning(f"Rate limiter error (allowing request): {e}")

    return await call_next(request)


# =============================================================================
# Request / Response Models
# =============================================================================

class UrlAnalysisRequest(BaseModel):
    url: str = Field(..., description="URL to analyze for phishing/malware")
    metadata: Optional[dict] = Field(default=None, description="Client metadata")
    client_timestamp: Optional[str] = None


class UrlAnalysisResponse(BaseModel):
    url: str
    risk_score: int = Field(..., ge=0, le=100)
    verdict: str  # safe | suspicious | phishing | malware
    domain_entropy: float
    redirect_count: int
    has_login_form: bool
    details: str
    analyzed_at: str
    job_id: str


class HoaxCheckRequest(BaseModel):
    text: str = Field(..., min_length=10, max_length=5000, description="Text to verify")
    source_package: Optional[str] = None
    client_timestamp: Optional[str] = None


class HoaxCheckResponse(BaseModel):
    verdict: str  # verified | likely_true | unverified | likely_false | false
    confidence: float = Field(..., ge=0.0, le=1.0)
    explanation: str
    sources: list[str]
    analyzed_at: str
    job_id: str


# =============================================================================
# Endpoints
# =============================================================================

@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "service": "veritasguard", "timestamp": time.time()}


@app.post("/api/v1/analyze-url", response_model=UrlAnalysisResponse)
async def analyze_url(request: UrlAnalysisRequest, req: Request):
    """
    Analyze a URL for phishing / malware indicators.

    Process:
    1. Queue job in Redis (volatile)
    2. Run domain entropy analysis
    3. Visit URL in sandboxed headless browser
    4. Analyze redirect chain, page content, login forms
    5. Compute aggregate risk score
    6. Return verdict and details
    """
    job_id = str(uuid.uuid4())
    logger.info(f"[{job_id}] URL analysis requested: {request.url[:100]}")

    try:
        # Queue in Redis with TTL (zero-knowledge: auto-expire)
        redis: aioredis.Redis = req.app.state.redis
        await redis.setex(f"job:{job_id}", 300, "processing")

        # Run analysis
        analyzer: URLAnalyzer = req.app.state.url_analyzer
        result = await analyzer.analyze(request.url)

        # Update job status and auto-expire
        await redis.setex(f"job:{job_id}", 60, "completed")

        logger.info(
            f"[{job_id}] Analysis complete: verdict={result['verdict']}, "
            f"risk={result['risk_score']}"
        )

        return UrlAnalysisResponse(
            url=request.url,
            risk_score=result["risk_score"],
            verdict=result["verdict"],
            domain_entropy=result["domain_entropy"],
            redirect_count=result["redirect_count"],
            has_login_form=result["has_login_form"],
            details=result["details"],
            analyzed_at=result["analyzed_at"],
            job_id=job_id,
        )

    except Exception as e:
        logger.error(f"[{job_id}] URL analysis failed: {e}")
        raise HTTPException(status_code=500, detail=f"Analysis failed: {str(e)}")


@app.post("/api/v1/check-hoax", response_model=HoaxCheckResponse)
async def check_hoax(request: HoaxCheckRequest, req: Request):
    """
    Verify text content for misinformation using RAG + Gemini 1.5 Pro.

    Process:
    1. Queue job in Redis (volatile)
    2. Send sanitized text to Gemini with fact-checking system prompt
    3. Cross-reference with trusted source embeddings
    4. Return verdict with confidence score and source citations
    """
    job_id = str(uuid.uuid4())
    logger.info(
        f"[{job_id}] Hoax check requested: {len(request.text)} chars "
        f"from {request.source_package or 'unknown'}"
    )

    try:
        # Queue in Redis with TTL
        redis: aioredis.Redis = req.app.state.redis
        await redis.setex(f"job:{job_id}", 300, "processing")

        # Run fact verification
        checker: HoaxChecker = req.app.state.hoax_checker
        result = await checker.verify(request.text, request.source_package)

        # Update job status
        await redis.setex(f"job:{job_id}", 60, "completed")

        logger.info(
            f"[{job_id}] Hoax check complete: verdict={result['verdict']}, "
            f"confidence={result['confidence']:.2f}"
        )

        return HoaxCheckResponse(
            verdict=result["verdict"],
            confidence=result["confidence"],
            explanation=result["explanation"],
            sources=result["sources"],
            analyzed_at=result["analyzed_at"],
            job_id=job_id,
        )

    except Exception as e:
        logger.error(f"[{job_id}] Hoax check failed: {e}")
        raise HTTPException(status_code=500, detail=f"Verification failed: {str(e)}")


# =============================================================================
# Run with: uvicorn app.main:app --reload
# Production: uvicorn app.main:app --host 0.0.0.0 --port 8000 \
#             --ssl-keyfile server.key --ssl-certfile server.crt
# =============================================================================
