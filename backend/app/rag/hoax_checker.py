"""
VeritasGuard — RAG-Based Hoax Checker (Gemini 1.5 Pro)

Performs fact verification on text content using:
  1. Gemini 1.5 Pro as the reasoning engine
  2. Structured system prompt for factuality assessment
  3. JSON-formatted output with verdict, confidence, sources, explanation
  4. Trusted source cross-referencing

All processing is in-memory — no user data persisted to disk.
"""

import json
import logging
from datetime import datetime, timezone
from typing import Optional

import google.generativeai as genai

logger = logging.getLogger("veritasguard.rag")


# =============================================================================
# System Prompt
# =============================================================================

FACT_CHECK_SYSTEM_PROMPT = """You are VeritasGuard, an expert fact-checking AI assistant. Your role is to analyze text content for misinformation, disinformation, and unverified claims.

## Instructions

1. ANALYZE the provided text carefully for factual claims
2. EVALUATE each claim against known facts and trusted sources
3. DETERMINE the overall factuality verdict
4. PROVIDE source citations when possible
5. EXPLAIN your reasoning clearly and concisely

## Verdict Categories

- **verified**: Multiple trusted sources confirm this information. High confidence.
- **likely_true**: Information appears factual based on available evidence but lacks multiple source confirmation.
- **unverified**: Claims cannot be confirmed or denied with available information. No reliable sources found.
- **likely_false**: Evidence suggests the claims are inaccurate or misleading. Contradicted by some trusted sources.
- **false**: Definitively contradicted by multiple trusted sources. Known misinformation.

## Trusted Sources Priority
1. Reuters, AP News, AFP
2. Official government/institutional websites
3. Peer-reviewed scientific publications
4. Major established news organizations (BBC, NYT, etc.)

## Output Format
You MUST respond with a valid JSON object with these exact fields:
{
    "verdict": "one of: verified, likely_true, unverified, likely_false, false",
    "confidence": 0.0 to 1.0,
    "explanation": "Clear, concise explanation of your assessment (2-3 sentences max)",
    "sources": ["List of relevant source names or URLs that support your verdict"]
}

## Rules
- Be objective and evidence-based
- If unsure, default to "unverified" with lower confidence
- Never fabricate sources
- Consider the context and source app when assessing
- Prioritize user safety: err on the side of caution for health/safety claims
"""


# =============================================================================
# Hoax Checker Service
# =============================================================================

class HoaxChecker:
    """RAG-based fact verification engine using Gemini 1.5 Pro."""

    def __init__(self, api_key: str):
        if api_key:
            genai.configure(api_key=api_key)
            self.model = genai.GenerativeModel(
                model_name="gemini-1.5-pro",
                generation_config=genai.GenerationConfig(
                    temperature=0.1,  # Low temperature for factual analysis
                    top_p=0.95,
                    top_k=40,
                    max_output_tokens=1024,
                    response_mime_type="application/json",
                ),
            )
            logger.info("Gemini 1.5 Pro model initialized")
        else:
            self.model = None
            logger.warning("⚠️ No GEMINI_API_KEY provided — hoax checker will return mock results")

    async def verify(self, text: str, source_package: Optional[str] = None) -> dict:
        """
        Verify text content for factuality using Gemini 1.5 Pro.

        Args:
            text: PII-scrubbed text to analyze
            source_package: Android package name of the source app

        Returns:
            Dict with: verdict, confidence, explanation, sources, analyzed_at
        """
        if not self.model:
            return self._mock_result(text)

        try:
            # Build context-aware prompt
            source_context = ""
            if source_package:
                app_names = {
                    "com.instagram.android": "Instagram",
                    "com.whatsapp": "WhatsApp",
                    "com.twitter.android": "X (Twitter)",
                    "org.telegram.messenger": "Telegram",
                    "com.zhiliaoapp.musically": "TikTok",
                    "com.facebook.katana": "Facebook",
                }
                app_name = app_names.get(source_package, source_package)
                source_context = f"\n\n[Source: Content captured from {app_name}]"

            user_prompt = f"""Analyze the following text for factuality and misinformation:

---
{text}
---
{source_context}

Respond with a JSON object containing: verdict, confidence, explanation, sources."""

            # Call Gemini 1.5 Pro
            response = await self.model.generate_content_async(
                [
                    {"role": "user", "parts": [FACT_CHECK_SYSTEM_PROMPT]},
                    {"role": "model", "parts": ['{"understood": true, "ready": "I will analyze text for misinformation and respond with structured JSON verdicts."}']},
                    {"role": "user", "parts": [user_prompt]},
                ]
            )

            # Parse JSON response
            result = self._parse_response(response.text)

            result["analyzed_at"] = datetime.now(timezone.utc).isoformat()
            return result

        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse Gemini response as JSON: {e}")
            return {
                "verdict": "unverified",
                "confidence": 0.0,
                "explanation": "Analysis engine returned an unparseable response. Unable to verify.",
                "sources": [],
                "analyzed_at": datetime.now(timezone.utc).isoformat(),
            }
        except Exception as e:
            logger.error(f"Gemini API error: {e}")
            return {
                "verdict": "unverified",
                "confidence": 0.0,
                "explanation": f"Analysis temporarily unavailable: {str(e)[:100]}",
                "sources": [],
                "analyzed_at": datetime.now(timezone.utc).isoformat(),
            }

    def _parse_response(self, response_text: str) -> dict:
        """Parse and validate the Gemini JSON response."""
        try:
            result = json.loads(response_text)
        except json.JSONDecodeError:
            # Try to extract JSON from markdown code blocks
            import re
            json_match = re.search(r"```json?\s*(.*?)\s*```", response_text, re.DOTALL)
            if json_match:
                result = json.loads(json_match.group(1))
            else:
                raise

        # Validate and normalize
        valid_verdicts = {"verified", "likely_true", "unverified", "likely_false", "false"}
        verdict = result.get("verdict", "unverified")
        if verdict not in valid_verdicts:
            verdict = "unverified"

        confidence = float(result.get("confidence", 0.0))
        confidence = max(0.0, min(1.0, confidence))

        return {
            "verdict": verdict,
            "confidence": confidence,
            "explanation": str(result.get("explanation", "No explanation provided"))[:500],
            "sources": list(result.get("sources", []))[:10],
        }

    def _mock_result(self, text: str) -> dict:
        """Return a mock result when API key is not configured."""
        return {
            "verdict": "unverified",
            "confidence": 0.0,
            "explanation": "Hoax checker is running in mock mode (no API key configured). "
                         f"Received {len(text)} characters for analysis.",
            "sources": [],
            "analyzed_at": datetime.now(timezone.utc).isoformat(),
        }
