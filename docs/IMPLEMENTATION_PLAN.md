# VeritasGuard — Implementation Plan

**Project:** VeritasGuard (Security-Focused Mobile App)
**Stack:** React Native (New Architecture), Kotlin Native Modules, FastAPI, Redis, Gemini 1.5 Pro
**Platform:** Android 14+ (with Android 16 FGS compliance)
**Date:** February 10, 2026

---

## 1. System Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    ANDROID DEVICE                       │
│                                                         │
│  ┌──────────────┐    ┌───────────────────┐              │
│  │ React Native │───▶│  Native Modules   │              │
│  │   UI Layer   │    │     Bridge        │              │
│  └──────┬───────┘    └───┬───────┬───────┘              │
│         │                │       │                      │
│         ▼                ▼       ▼                      │
│  ┌─────────────┐  ┌──────────┐ ┌──────────────────┐    │
│  │  Overlay    │  │ FGS:     │ │ Accessibility    │    │
│  │  Module     │  │ Clipboard│ │ Service: Text    │    │
│  │  (Badges)   │  │ Monitor  │ │ Extractor        │    │
│  └─────────────┘  └────┬─────┘ └────────┬─────────┘    │
│                         │                │              │
│                         ▼                ▼              │
│                   ┌──────────┐    ┌────────────┐        │
│                   │ Headless │    │ PII        │        │
│                   │ JS Task  │    │ Scrubber   │        │
│                   └────┬─────┘    └─────┬──────┘        │
│                        │                │               │
│                        ▼                ▼               │
│                   ┌─────────────────────────┐           │
│                   │ TLS 1.3 SSL-Pinned      │           │
│                   │ HTTP Client              │           │
│                   └────────────┬─────────────┘           │
└────────────────────────────────┼─────────────────────────┘
                                 │
                                 ▼
┌────────────────────────────────────────────────────────┐
│               BACKEND INFRASTRUCTURE                   │
│                                                        │
│  ┌────────────────────────────────────┐                │
│  │ FastAPI Gateway (mTLS + Rate Limit)│                │
│  └──────────┬─────────────────┬───────┘                │
│             │                 │                         │
│             ▼                 ▼                         │
│  ┌────────────────┐  ┌───────────────────┐             │
│  │ Redis Queue    │  │ Redis Queue       │             │
│  │ (Volatile)     │  │ (Volatile)        │             │
│  └───────┬────────┘  └────────┬──────────┘             │
│          │                    │                         │
│          ▼                    ▼                         │
│  ┌────────────────┐  ┌───────────────────┐             │
│  │ URL Sandbox    │  │ RAG Engine        │             │
│  │ (Playwright)   │  │ (Gemini 1.5 Pro)  │             │
│  └────────────────┘  └───────────────────┘             │
└────────────────────────────────────────────────────────┘
```

---

## 2. User Review Required

### ⚠️ IMPORTANT: Accessibility Service Usage
Google Play requires a Prominent Disclosure & Consent form for `BIND_ACCESSIBILITY_SERVICE`. The implementation scopes the service to specific package names only (Instagram, WhatsApp, X, Telegram, TikTok, Facebook). You must submit a Play Console Accessibility Declaration before publishing.

### ⚠️ WARNING: Android 16 Foreground Service Restrictions
Starting with Android 16 (API 36), `dataSync` FGS has a 6-hour timeout. The implementation uses a combination of `dataSync` + `specialUse` types with proper property declarations. The `specialUse` type requires a justification during Play Store review.

### ⚠️ CAUTION: SSL Pinning Certificates
You must replace the placeholder SHA-256 pin hashes in `network_security_config.xml` with your actual server certificate pins. Incorrect pins will cause all network requests to fail.

---

## 3. Proposed Changes

### 3.1 Android Native Layer

#### [NEW] AndroidManifest.xml
**Path:** `android/app/src/main/AndroidManifest.xml`

Configure Android permissions and service declarations:

| Permission / Declaration | Purpose |
|---|---|
| `FOREGROUND_SERVICE` | Base FGS permission |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 16+ typed FGS for URL analysis sync |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 16+ typed FGS for security monitoring |
| `POST_NOTIFICATIONS` | Android 13+ notification runtime permission |
| `SYSTEM_ALERT_WINDOW` | Draw overlay badges over other apps |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Network communication |
| `ClipboardListenerService` | Foreground service with `dataSync\|specialUse` type |
| `ScreenTextAccessibilityService` | Accessibility service with meta-data pointing to config XML |
| `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | Android 16 compliance property |

---

#### [NEW] accessibility_service_config.xml
**Path:** `android/app/src/main/res/xml/accessibility_service_config.xml`

Scoped accessibility service config:
- `accessibilityEventTypes`: `typeWindowContentChanged|typeViewTextChanged`
- `packageNames`: `com.instagram.android`, `com.whatsapp`, `com.twitter.android`, `org.telegram.messenger`, `com.zhiliaoapp.musically`, `com.facebook.katana`
- `canRetrieveWindowContent`: `true`

---

#### [NEW] network_security_config.xml
**Path:** `android/app/src/main/res/xml/network_security_config.xml`

Certificate pinning config for TLS 1.3:
- Domain-specific pin-set for API server
- SHA-256 certificate pins (placeholder to be replaced)
- `cleartextTrafficPermitted="false"` globally

---

#### [NEW] ClipboardListenerModule.kt
**Path:** `android/app/src/main/java/com/veritasguard/clipboard/ClipboardListenerModule.kt`

React Native Native Module that:
1. Registers as a `ReactContextBaseJavaModule`
2. Starts a Foreground Service (`ClipboardListenerService`) with persistent notification
3. The service registers `OnPrimaryClipChangedListener` on `ClipboardManager`
4. On clip change → extracts text → fires `HeadlessJsTaskService.acquireWakeLockNow()`
5. Starts the `PhishingDetectorTask` Headless JS task with clipboard metadata payload

Key classes:
- `ClipboardListenerModule` — Native Module (bridge to JS)
- `ClipboardListenerService` — Android Service with FGS notification
- `ClipboardListenerPackage` — ReactPackage registration
- `PhishingTaskService` — extends HeadlessJsTaskService

---

#### [NEW] ScreenTextAccessibilityService.kt
**Path:** `android/app/src/main/java/com/veritasguard/accessibility/ScreenTextAccessibilityService.kt`

Custom AccessibilityService that:
1. Receives `TYPE_WINDOW_CONTENT_CHANGED` events from scoped packages
2. Traverses `AccessibilityNodeInfo` tree to extract visible text
3. Sends extracted text to React Native via `DeviceEventEmitter`
4. Implements debouncing (2s) and deduplication

---

#### [NEW] OverlayModule.kt
**Path:** `android/app/src/main/java/com/veritasguard/overlay/OverlayModule.kt`

Native Module for system alert window:
1. Uses `WindowManager` with `TYPE_APPLICATION_OVERLAY`
2. Shows a floating badge (colored circle) with threat level indicator
3. Supports `showBadge(level, message)` and `dismissBadge()` methods
4. Non-blocking, non-focusable overlay flags
5. Auto-dismiss after 8 seconds

---

### 3.2 React Native Layer

#### [NEW] App.tsx
**Path:** `App.tsx`

Main application entry:
1. SSL Pinning configuration via `react-native-ssl-pinning`
2. Background service initialization on mount
3. `NativeEventEmitter` listener for accessibility text events
4. PII scrubbing pipeline before API calls
5. Permission request flow (Notification, Overlay, Accessibility guidance)
6. Dark-themed dashboard UI showing security status and event log

---

#### [NEW] PhishingDetectorTask.ts
**Path:** `src/tasks/PhishingDetectorTask.ts`

Headless JS task registered via `AppRegistry.registerHeadlessTask`:
1. Receives clipboard metadata from native module
2. Extracts URLs using regex
3. Computes client-side heuristics (domain entropy, suspicious TLD, IP-based URL)
4. Sends URL + metadata to FastAPI `/api/v1/analyze-url` via SSL-pinned fetch
5. Falls back to client-side scoring when backend is unreachable
6. Triggers overlay badge if threat threshold exceeded

---

#### [NEW] piiScrubber.ts
**Path:** `src/utils/piiScrubber.ts`

Client-side PII removal before any data leaves device:
- Email addresses
- Phone numbers (international + local)
- Indonesian NIK (16-digit national ID)
- Credit/debit card numbers
- IP addresses
- URLs with embedded credentials
- SSN-like patterns

---

#### [NEW] hoaxChecker.ts
**Path:** `src/services/hoaxChecker.ts`

Client-side hoax checking orchestrator:
1. Listens for accessibility service text events
2. Applies PII scrubber to raw text
3. Implements per-package cooldown throttling (10 seconds)
4. Sends sanitized text to FastAPI `/api/v1/check-hoax` via SSL-pinned fetch
5. Triggers overlay badge with result summary

---

### 3.3 FastAPI Backend

#### [NEW] main.py
**Path:** `backend/app/main.py`

FastAPI application:
- `POST /api/v1/analyze-url` — URL phishing analysis endpoint
- `POST /api/v1/check-hoax` — Text factuality verification endpoint
- Redis job queue integration (volatile, `maxmemory-policy: allkeys-lru`)
- Sliding window rate limiter (30 requests/minute per IP)
- CORS middleware, Pydantic request/response models
- mTLS-ready Uvicorn configuration

---

#### [NEW] url_analyzer.py
**Path:** `backend/app/sandbox/url_analyzer.py`

URL sandbox analysis with 6 detection signals:
1. Domain entropy calculation using Shannon entropy
2. Suspicious TLD detection (18 known phishing TLDs)
3. IP-based URL detection
4. Excessive subdomain detection
5. Playwright headless browser visit (redirect chain, content analysis)
6. Login form / credential harvesting detection

Risk score aggregation on a 0–100 scale.

---

#### [NEW] hoax_checker.py
**Path:** `backend/app/rag/hoax_checker.py`

RAG-based fact verification:
1. Gemini 1.5 Pro integration via `google-generativeai`
2. Structured system prompt for factuality assessment
3. JSON output: `{verdict, confidence, sources, explanation}`
4. 5-tier verdict system: verified → likely_true → unverified → likely_false → false
5. Graceful fallback when API key is unavailable

---

#### [NEW] requirements.txt
**Path:** `backend/requirements.txt`

```
fastapi>=0.109.0
uvicorn[standard]>=0.27.0
redis>=5.0.0
playwright>=1.41.0
google-generativeai>=0.4.0
pydantic>=2.5.0
python-multipart>=0.0.6
```

---

## 4. Security Measures (Anti-Spyware Compliance)

| Measure | Implementation |
|---|---|
| **Zero-Knowledge Processing** | All data processed in-memory only; Redis nofsync, no RDB/AOF |
| **PII Scrubbing** | Client-side regex removal before any data leaves device |
| **Scoped Accessibility** | Config XML limits to 6 specific package names |
| **SSL Pinning** | `network_security_config.xml` + `react-native-ssl-pinning` |
| **Volatile Queue** | Redis `maxmemory-policy: allkeys-lru`, TTL on all keys |
| **Transparent FGS** | Persistent notification explains active monitoring |
| **No Disk Writes** | Sensitive data never written to SharedPreferences or files |

---

## 5. Verification Plan

### 5.1 Project Build Check
- Run `npx react-native run-android --mode=debug` in project root
- Verify no manifest merge errors in build output
- Check permissions: `aapt dump permissions app/build/outputs/apk/debug/app-debug.apk`

### 5.2 Native Module Linkage
- Run the app on an Android 14+ emulator
- Open React Native DevTools → check that `ClipboardListenerModule` is in `NativeModules`
- Call `NativeModules.ClipboardListenerModule.startListening()` from DevTools console

### 5.3 Backend Smoke Test
- Start backend: `cd backend && pip install -r requirements.txt && uvicorn app.main:app --reload`
- Test URL analysis: `curl -X POST http://localhost:8000/api/v1/analyze-url -H "Content-Type: application/json" -d '{"url":"https://example.com"}'`
- Test hoax check: `curl -X POST http://localhost:8000/api/v1/check-hoax -H "Content-Type: application/json" -d '{"text":"Breaking: Scientists confirm water is dry"}'`
- Verify both return JSON responses with expected schema

### 5.4 Security Audit Checklist
- [ ] Verify `cleartextTrafficPermitted="false"` in network config
- [ ] Confirm Redis is configured with `save ""` (no persistence)
- [ ] Run `testssl.sh` against deployed backend to confirm TLS 1.3
- [ ] Verify no sensitive data in SharedPreferences

---

## 6. Files Summary

| # | File | Purpose |
|---|---|---|
| 1 | `AndroidManifest.xml` | Permissions and service declarations |
| 2 | `accessibility_service_config.xml` | Accessibility scope limits |
| 3 | `network_security_config.xml` | Certificate pinning |
| 4 | `ClipboardListenerModule.kt` | Native module bridge to JS |
| 5 | `ClipboardListenerService.kt` | Foreground service + clip listener |
| 6 | `PhishingTaskService.kt` | HeadlessJsTaskService bridge |
| 7 | `ClipboardListenerPackage.kt` | ReactPackage registration |
| 8 | `ScreenTextAccessibilityService.kt` | Text scraping from scoped apps |
| 9 | `OverlayModule.kt` | Floating security badge overlay |
| 10 | `OverlayPackage.kt` | ReactPackage registration |
| 11 | `BootReceiver.kt` | Restart services after reboot |
| 12 | `App.tsx` | Dashboard UI, permissions, service init |
| 13 | `index.js` | Entry point + Headless task registration |
| 14 | `PhishingDetectorTask.ts` | Background URL analysis task |
| 15 | `piiScrubber.ts` | Client-side PII removal |
| 16 | `apiClient.ts` | SSL-pinned HTTP client |
| 17 | `hoaxChecker.ts` | Accessibility text → API pipeline |
| 18 | `main.py` | FastAPI gateway + Redis queue |
| 19 | `url_analyzer.py` | URL sandbox with Playwright |
| 20 | `hoax_checker.py` | Gemini 1.5 Pro RAG engine |
| 21 | `requirements.txt` | Python dependencies |
| 22 | `app.json` | React Native app config |
