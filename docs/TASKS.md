# VeritasGuard — Task Checklist

**Project:** VeritasGuard (Security-Focused Mobile App)
**Date:** February 10, 2026

---

## Phase 1: Planning
- [x] Review existing codebase and past conversations
- [x] Create implementation plan with all deliverables
- [x] Get user approval on architecture plan

## Phase 2: Project Scaffolding
- [x] Initialize React Native project structure
- [x] Configure project structure and dependencies

## Phase 3: Android Native Layer
- [x] Create `AndroidManifest.xml` with permissions and service declarations
    - Foreground Service permissions (base + dataSync + specialUse)
    - POST_NOTIFICATIONS, SYSTEM_ALERT_WINDOW, WAKE_LOCK
    - ClipboardListenerService, ScreenTextAccessibilityService declarations
    - BootReceiver for service restart
- [x] Create `accessibility_service_config.xml`
    - Scoped to 6 packages: Instagram, WhatsApp, X, Telegram, TikTok, Facebook
    - Event types: typeWindowContentChanged, typeViewTextChanged
    - canRetrieveWindowContent enabled
- [x] Create `network_security_config.xml`
    - Certificate pinning for api.veritasguard.io
    - cleartextTrafficPermitted=false globally
    - Debug overrides for local development
- [x] Implement `ClipboardListenerModule.kt` (Native Module + Headless JS bridge)
    - ReactContextBaseJavaModule with startListening/stopListening/isListening
    - Starts ClipboardListenerService foreground service
- [x] Implement `ClipboardListenerService.kt` (Foreground Service)
    - OnPrimaryClipChangedListener registration
    - Clip text extraction and metadata bundling
    - HeadlessJsTask dispatch via PhishingTaskService
    - START_STICKY for persistence
    - Notification channel and persistent notification
- [x] Implement `PhishingTaskService.kt` (HeadlessJsTaskService)
    - Bridges clipboard metadata to JS PhishingDetectorTask
    - 5-second timeout, allows foreground execution
- [x] Implement `ClipboardListenerPackage.kt` (ReactPackage)
- [x] Implement `ScreenTextAccessibilityService.kt` (Accessibility Service)
    - AccessibilityNodeInfo tree traversal
    - 2-second debouncing, text deduplication
    - DeviceEventEmitter to React Native
    - 5000 character max text length
- [x] Implement `OverlayModule.kt` (System Alert Window)
    - TYPE_APPLICATION_OVERLAY with non-blocking flags
    - Color-coded badges: safe(green), warning(amber), danger(red), info(blue)
    - Auto-dismiss after 8 seconds
    - canDrawOverlays permission check
- [x] Implement `OverlayPackage.kt` (ReactPackage)
- [x] Implement `BootReceiver.kt`
    - Restarts ClipboardListenerService on BOOT_COMPLETED

## Phase 4: React Native Layer
- [x] Create `App.tsx` with background service initialization and SSL Pinning
    - Permission request flow (notifications, overlay, accessibility guidance)
    - ClipboardListenerModule.startListening() on user action
    - HoaxChecker event listener setup
    - Dark-themed dashboard with security event log
    - Status card with monitoring toggle
- [x] Create `index.js` with Headless task registration
    - AppRegistry.registerComponent for main app
    - AppRegistry.registerHeadlessTask for PhishingDetectorTask
- [x] Create PII Scrubber utility (`piiScrubber.ts`)
    - 7 regex patterns: email, phone, NIK, credit card, IP, credential URL, SSN
    - scrubPII(), containsPII(), analyzePII() functions
    - Returns sanitized text + PII count + PII types
- [x] Create SSL-pinned API client (`apiClient.ts`)
    - react-native-ssl-pinning integration
    - analyzeUrl() and checkHoax() typed functions
    - 15-second timeout, consistent error handling
- [x] Create Phishing Detector Headless Task (`PhishingDetectorTask.ts`)
    - URL extraction via regex
    - Client-side heuristics: domain entropy, suspicious TLD, IP-based URL, excessive subdomains
    - Backend API call with SSL pinning
    - Offline fallback using client-side scoring
    - Overlay badge trigger based on risk threshold
- [x] Create Hoax Checker service (`hoaxChecker.ts`)
    - NativeEventEmitter listener for onScreenTextCaptured
    - PII scrubber integration (mandatory before any API call)
    - Per-package cooldown throttling (10 seconds)
    - 5-tier verdict handling with overlay badges
    - Singleton pattern with start/stop lifecycle

## Phase 5: FastAPI Backend
- [x] Create `main.py` with mTLS endpoints and Redis queue
    - POST /api/v1/analyze-url endpoint
    - POST /api/v1/check-hoax endpoint
    - Redis volatile mode (no disk persistence)
    - Sliding window rate limiter (30 req/min per IP)
    - CORS middleware, Pydantic models
    - Health check endpoint
- [x] Create `url_analyzer.py` with headless browser sandbox
    - Shannon entropy domain calculation
    - 18 suspicious TLD blacklist
    - Playwright sandboxed browser visit
    - Redirect chain analysis
    - Login form / credential harvesting detection
    - 0-100 risk score aggregation
- [x] Create `hoax_checker.py` with Gemini 1.5 Pro RAG engine
    - Structured system prompt for factuality
    - JSON response parsing with validation
    - 5-tier verdict: verified/likely_true/unverified/likely_false/false
    - Trusted source priority (Reuters, AP, AFP)
    - Mock mode fallback when no API key
- [x] Create `requirements.txt`
    - fastapi, uvicorn, redis, playwright, google-generativeai, pydantic

## Phase 6: Verification
- [x] Verify all files are created (25 files confirmed)
- [x] Create walkthrough documentation
- [ ] Build verification on Android device/emulator
- [ ] Backend smoke test with curl commands
- [ ] Security audit checklist

## Phase 7: Remaining Setup (User Tasks)
- [ ] Run `npx -y @react-native-community/cli init VeritasGuard` for full RN scaffold
- [ ] Register ClipboardListenerPackage and OverlayPackage in MainApplication.kt
- [ ] Replace placeholder SHA-256 pins in network_security_config.xml
- [ ] Set GEMINI_API_KEY environment variable
- [ ] Install Playwright: `playwright install chromium`
- [ ] Submit Play Console Accessibility Declaration
- [ ] Prepare PROPERTY_SPECIAL_USE_FGS_SUBTYPE justification for Play Store review
