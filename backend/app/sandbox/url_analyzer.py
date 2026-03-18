"""
VeritasGuard — URL Sandbox Analyzer

Performs multi-signal phishing/malware analysis on URLs using:
  1. Domain entropy calculation (Shannon entropy)
  2. Headless browser visit in sandboxed Playwright context
  3. Redirect chain analysis
  4. Page content heuristics (login forms, credential harvesting)
  5. Aggregate risk scoring (0-100 scale)

All analysis is performed in-memory — no data written to disk.
"""

import math
import re
import logging
from datetime import datetime, timezone
from urllib.parse import urlparse
from typing import Optional

from playwright.async_api import async_playwright, Browser, BrowserContext

logger = logging.getLogger("veritasguard.sandbox")


# =============================================================================
# Domain Entropy Calculator
# =============================================================================

def calculate_domain_entropy(domain: str) -> float:
    """
    Calculate Shannon entropy of a domain name.

    High entropy (>4.0) often indicates randomly generated phishing domains
    (e.g., "x7k2m9p4.tk") vs legitimate domains (e.g., "google.com").

    Returns:
        Float entropy value. Higher = more suspicious.
    """
    if not domain:
        return 0.0

    freq: dict[str, int] = {}
    for char in domain:
        freq[char] = freq.get(char, 0) + 1

    entropy = 0.0
    length = len(domain)
    for count in freq.values():
        p = count / length
        entropy -= p * math.log2(p)

    return round(entropy, 4)


# =============================================================================
# Suspicious Domain Indicators
# =============================================================================

SUSPICIOUS_TLDS = {
    ".tk", ".ml", ".ga", ".cf", ".gq", ".xyz", ".top", ".work",
    ".click", ".link", ".info", ".buzz", ".surf", ".rest", ".icu",
    ".cam", ".monster", ".sbs",
}

TRUSTED_DOMAINS = {
    "google.com", "facebook.com", "twitter.com", "instagram.com",
    "youtube.com", "linkedin.com", "github.com", "microsoft.com",
    "apple.com", "amazon.com", "wikipedia.org", "reuters.com",
    "apnews.com", "bbc.com", "bbc.co.uk",
}

# Patterns indicating credential harvesting
LOGIN_FORM_PATTERNS = [
    r'<input[^>]*type=["\']password["\']',
    r'<form[^>]*action=["\'][^"\']*login',
    r'<form[^>]*action=["\'][^"\']*signin',
    r'<form[^>]*action=["\'][^"\']*auth',
    r'name=["\']username["\']',
    r'name=["\']email["\'].*type=["\']password["\']',
    r'placeholder=["\'][^"\']*password["\']',
]


# =============================================================================
# URL Analyzer Service
# =============================================================================

class URLAnalyzer:
    """Sandboxed URL analysis engine using Playwright headless browser."""

    def __init__(self):
        self._playwright = None
        self._browser: Optional[Browser] = None

    async def _ensure_browser(self):
        """Lazy-initialize the Playwright browser."""
        if self._browser is None:
            self._playwright = await async_playwright().start()
            self._browser = await self._playwright.chromium.launch(
                headless=True,
                args=[
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                    "--disable-extensions",
                    "--disable-plugins",
                    "--disable-background-networking",
                ]
            )
            logger.info("Playwright browser initialized")

    async def close(self):
        """Clean up browser resources."""
        if self._browser:
            await self._browser.close()
        if self._playwright:
            await self._playwright.stop()
        logger.info("URL analyzer browser closed")

    async def analyze(self, url: str) -> dict:
        """
        Perform full URL analysis.

        Returns:
            Dict with: risk_score, verdict, domain_entropy, redirect_count,
                       has_login_form, details, analyzed_at
        """
        parsed = urlparse(url)
        domain = parsed.hostname or ""

        # Signal 1: Domain entropy
        entropy = calculate_domain_entropy(domain)

        # Signal 2: Suspicious TLD
        suspicious_tld = any(domain.endswith(tld) for tld in SUSPICIOUS_TLDS)

        # Signal 3: IP-based URL
        ip_based = bool(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", domain))

        # Signal 4: Trusted domain check
        is_trusted = any(domain.endswith(td) for td in TRUSTED_DOMAINS)

        # Signal 5: Excessive subdomains
        subdomain_count = len(domain.split("."))
        excessive_subdomains = subdomain_count > 4

        # Signal 6: Browser-based analysis
        redirect_count = 0
        has_login_form = False
        page_details = []

        try:
            await self._ensure_browser()
            context: BrowserContext = await self._browser.new_context(
                ignore_https_errors=False,
                java_script_enabled=True,
                user_agent="Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )

            page = await context.new_page()

            # Track redirects
            redirects: list[str] = []
            page.on("response", lambda response: redirects.append(response.url)
                     if response.status in (301, 302, 303, 307, 308) else None)

            try:
                response = await page.goto(url, wait_until="domcontentloaded", timeout=10000)
                redirect_count = len(redirects)

                if response:
                    # Get page content for analysis
                    content = await page.content()

                    # Check for login forms / credential harvesting
                    for pattern in LOGIN_FORM_PATTERNS:
                        if re.search(pattern, content, re.IGNORECASE):
                            has_login_form = True
                            break

                    # Check page title for suspicious keywords
                    title = await page.title()
                    suspicious_titles = ["login", "verify", "confirm", "secure", "update", "suspended"]
                    if any(kw in title.lower() for kw in suspicious_titles):
                        page_details.append(f"Suspicious page title: {title[:50]}")

            except Exception as e:
                page_details.append(f"Page load error: {str(e)[:100]}")

            finally:
                await context.close()

        except Exception as e:
            logger.warning(f"Browser analysis failed: {e}")
            page_details.append(f"Browser unavailable: {str(e)[:100]}")

        # =================================================================
        # Risk Score Aggregation (0-100)
        # =================================================================
        risk_score = 0

        if is_trusted:
            risk_score = max(0, risk_score - 30)
        else:
            if entropy > 4.5:
                risk_score += 25
            elif entropy > 3.8:
                risk_score += 15

            if suspicious_tld:
                risk_score += 20
            if ip_based:
                risk_score += 30
            if excessive_subdomains:
                risk_score += 15
            if has_login_form:
                risk_score += 20
            if redirect_count > 3:
                risk_score += 15
            elif redirect_count > 1:
                risk_score += 5

        risk_score = min(100, max(0, risk_score))

        # Determine verdict
        if risk_score >= 70:
            verdict = "phishing"
        elif risk_score >= 40:
            verdict = "suspicious"
        else:
            verdict = "safe"

        # Build details string
        signals = []
        if suspicious_tld:
            signals.append("suspicious TLD")
        if ip_based:
            signals.append("IP-based URL")
        if excessive_subdomains:
            signals.append(f"excessive subdomains ({subdomain_count})")
        if has_login_form:
            signals.append("credential harvesting form detected")
        if redirect_count > 1:
            signals.append(f"{redirect_count} redirects")
        if entropy > 4.0:
            signals.append(f"high domain entropy ({entropy:.2f})")
        if is_trusted:
            signals.append("trusted domain")

        details = "; ".join(signals + page_details) if signals or page_details else "No significant signals detected"

        return {
            "risk_score": risk_score,
            "verdict": verdict,
            "domain_entropy": entropy,
            "redirect_count": redirect_count,
            "has_login_form": has_login_form,
            "details": details,
            "analyzed_at": datetime.now(timezone.utc).isoformat(),
        }
