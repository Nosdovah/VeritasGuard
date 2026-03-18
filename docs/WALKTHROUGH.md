# VeritasGuard — Architecture Walkthrough

**Project:** VeritasGuard (Security-Focused Mobile App)
**Stack:** React Native, Kotlin, FastAPI, Redis, Gemini 1.5 Pro
**Date:** February 10, 2026

---

## 1. Summary

Built a complete **25-file architecture** for a persistent background security system using React Native with native Android Kotlin modules and a FastAPI backend. The system provides:

- **Real-time phishing URL detection** via clipboard monitoring
- **Live misinformation/hoax checking** via accessibility service text scraping
- **Non-blocking security alerts** via floating overlay badges
- **Zero-knowledge data processing** with client-side PII scrubbing

---

## 2. Architecture Layers

```
  LAYER 1: Android Native (Kotlin)
  ─────────────────────────────────
  ClipboardListenerService ──▶ HeadlessJS Bridge
  AccessibilityService     ──▶ DeviceEventEmitter
  OverlayModule            ──▶ WindowManager

         │                          │
         ▼                          ▼

  LAYER 2: React Native (TypeScript)
  ─────────────────────────────────
  PhishingDetectorTask (Headless JS, no UI)
  HoaxChecker + PII Scrubber (event pipeline)
  SSL-Pinned API Client (react-native-ssl-pinning)

         │
         ▼

  LAYER 3: FastAPI Backend (Python)
  ─────────────────────────────────
  URL Sandbox Analyzer (Playwright headless browser)
  RAG Hoax Checker (Gemini 1.5 Pro)
  Redis Volatile Queue (no disk persistence)
```

---

## 3. Files Created (25 total)

### 3.1 Android Native Layer (12 files)

| # | File | Purpose |
|---|---|---|
| 1 | `AndroidManifest.xml` | Permissions, FGS types, service declarations |
| 2 | `accessibility_service_config.xml` | Scoped to 6 social media packages |
| 3 | `network_security_config.xml` | TLS 1.3 + certificate pinning |
| 4 | `ClipboardListenerModule.kt` | Native Module bridge to JS |
| 5 | `ClipboardListenerService.kt` | Foreground Service + clipboard listener |
| 6 | `PhishingTaskService.kt` | HeadlessJsTaskService bridge |
| 7 | `ClipboardListenerPackage.kt` | ReactPackage registration |
| 8 | `ScreenTextAccessibilityService.kt` | Text scraping from scoped apps |
| 9 | `OverlayModule.kt` | Floating security badge overlay |
| 10 | `OverlayPackage.kt` | ReactPackage registration |
| 11 | `BootReceiver.kt` | Restart services after reboot |

### 3.2 React Native Layer (6 files)

| # | File | Purpose |
|---|---|---|
| 12 | `App.tsx` | Dashboard UI, permissions, service init |
| 13 | `index.js` | Entry point + Headless task registration |
| 14 | `PhishingDetectorTask.ts` | Background URL analysis task |
| 15 | `piiScrubber.ts` | Client-side PII removal (7 patterns) |
| 16 | `apiClient.ts` | SSL-pinned HTTP client |
| 17 | `hoaxChecker.ts` | Accessibility text → PII scrub → API pipeline |

### 3.3 FastAPI Backend (7 files)

| # | File | Purpose |
|---|---|---|
| 18 | `main.py` | Gateway with Redis queue + rate limiter |
| 19 | `url_analyzer.py` | 6-signal URL sandbox with Playwright |
| 20 | `hoax_checker.py` | Gemini 1.5 Pro RAG engine |
| 21 | `requirements.txt` | Python dependencies |
| 22–25 | `__init__.py`, `app.json` | Package inits + app config |

---

## 4. Data Flow: Phishing Detection

```
  Step 1: USER copies a suspicious URL
         │
         ▼
  Step 2: Android ClipboardManager fires OnPrimaryClipChangedListener
         │
         ▼
  Step 3: ClipboardListenerService (Foreground Service) receives event
         │
         ▼
  Step 4: Service extracts clip text, creates metadata bundle
         │
         ▼
  Step 5: PhishingTaskService starts HeadlessJsTask with payload
         │
         ▼
  Step 6: PhishingDetectorTask.ts runs in background JS context:
         ├── Extract URLs via regex
         ├── Compute client-side entropy & heuristics
         └── POST to /api/v1/analyze-url (SSL-pinned)
         │
         ▼
  Step 7: FastAPI backend:
         ├── Queues job in Redis (volatile, auto-expire)
         ├── Runs Playwright sandbox visit
         ├── Analyzes redirect chain + login forms
         └── Returns {riskScore: 85, verdict: "phishing"}
         │
         ▼
  Step 8: OverlayModule.showBadge("danger", "Phishing detected!")
         │
         ▼
  Step 9: Floating red badge appears over the current app
```

---

## 5. Data Flow: Hoax Detection

```
  Step 1: User views content on Instagram/WhatsApp/X/Telegram
         │
         ▼
  Step 2: AccessibilityService detects TYPE_WINDOW_CONTENT_CHANGED
         │
         ▼
  Step 3: Service traverses AccessibilityNodeInfo tree
         ├── Extracts all visible text
         ├── Debounces (2s cooldown)
         └── Deduplicates (skip identical text)
         │
         ▼
  Step 4: Sends text to React Native via DeviceEventEmitter
         │
         ▼
  Step 5: HoaxChecker.ts receives event:
         ├── Per-package cooldown (10s throttle)
         └── Calls PII Scrubber
         │
         ▼
  Step 6: PII Scrubber removes:
         ├── Email addresses      → [EMAIL_REDACTED]
         ├── Phone numbers        → [PHONE_REDACTED]
         ├── Indonesian NIK       → [NIK_REDACTED]
         ├── Credit card numbers  → [CARD_REDACTED]
         ├── IP addresses         → [IP_REDACTED]
         └── Credential URLs      → [CREDENTIAL_URL_REDACTED]
         │
         ▼
  Step 7: POST sanitized text to /api/v1/check-hoax (SSL-pinned)
         │
         ▼
  Step 8: FastAPI backend → Gemini 1.5 Pro RAG engine:
         ├── System prompt for factuality assessment
         ├── Cross-references trusted sources (Reuters, AP, AFP)
         └── Returns {verdict: "false", confidence: 0.92, sources: [...]}
         │
         ▼
  Step 9: OverlayModule.showBadge("danger", "Misinformation detected!")
         │
         ▼
  Step 10: Floating red badge appears over the social media app
```

---

## 6. Security Compliance Summary

| Requirement | Implementation |
|---|---|
| Android 16+ FGS | `dataSync\|specialUse` types + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` |
| Accessibility Scope | XML config scopes to 6 packages only |
| TLS 1.3 / SSL Pinning | `network_security_config.xml` + `react-native-ssl-pinning` |
| Zero-Knowledge | PII scrubbed client-side; Redis volatile (no disk) |
| POST_NOTIFICATIONS | Runtime permission request on Android 13+ |
| Anti-Spyware | Scoped packages, transparent FGS notification, no disk writes |

---

## 7. Next Steps

1. **Initialize React Native project:** `npx -y @react-native-community/cli init VeritasGuard`
2. **Install dependencies:** `npm install react-native-ssl-pinning`
3. **Register native packages** in `MainApplication.kt`:
   - `ClipboardListenerPackage`
   - `OverlayPackage`
4. **Replace SSL pin hashes** in `network_security_config.xml` with actual certificate pins
5. **Set environment variable:** `GEMINI_API_KEY=your_key_here`
6. **Install Playwright:** `pip install -r requirements.txt && playwright install chromium`
7. **Build and test:** `npx react-native run-android --mode=debug`
