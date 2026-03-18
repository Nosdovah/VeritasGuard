
import { NativeEventEmitter, NativeModules, DeviceEventEmitter } from 'react-native';
import { ApiClient, HoaxCheckResult } from './apiClient';
import { PiiScrubber, ScrubResult } from '../utils/piiScrubber';
import OverlayModule from './Overlay';

// Type for the event payload from Kotlin
interface AccessibilityEventPayload {
    packageName: string;
    text: string;
    timestamp: number;
}

class HoaxChecker {
    private isRunning = false;
    private subscription: any = null;
    private lastCapturedText: string = "";
    private lastCapturedPayload: AccessibilityEventPayload | null = null;

    // Callbacks for UI updates
    private onResultCallback?: (result: HoaxCheckResult, pkg: string) => void;
    private onErrorCallback?: (error: Error, pkg: string) => void;
    private onPIICallback?: (result: ScrubResult, pkg: string) => void;

    start(callbacks: {
        onResult: (result: HoaxCheckResult, pkg: string) => void;
        onError: (error: Error, pkg: string) => void;
        onPIIDetected: (result: ScrubResult, pkg: string) => void;
    }) {
        if (this.isRunning) return;

        this.onResultCallback = callbacks.onResult;
        this.onErrorCallback = callbacks.onError;
        this.onPIICallback = callbacks.onPIIDetected;

        console.log('[HoaxChecker] Starting service...');

        // Listen to "onScreenTextCaptured" from ScreenTextAccessibilityService.kt
        this.subscription = DeviceEventEmitter.addListener(
            'onScreenTextCaptured',
            this.handleScreenText
        );

        this.isRunning = true;
    }

    stop() {
        if (!this.isRunning) return;
        if (this.subscription) {
            this.subscription.remove();
            this.subscription = null;
        }
        this.isRunning = false;
        console.log('[HoaxChecker] Service stopped.');
    }

    private handleScreenText = async (data: any) => {
        const payload = data as AccessibilityEventPayload;
        const { packageName, text } = payload;
        this.lastCapturedText = text;
        this.lastCapturedPayload = payload;

        console.log(`[HoaxChecker] Received text from ${packageName}: ${text.substring(0, 50)}...`);

        // Option: Only analyze on demand? Or analyze high-risk apps auto?
        // For now, let's analyze auto AND allow demand.
        this.analyzeText(payload);
    };

    private analyzeText = async (payload: AccessibilityEventPayload) => {
        const { packageName, text } = payload;
        try {
            // 1. Scrub PII locally
            const scrubResult = PiiScrubber.scrub(text);
            if (this.onPIICallback) {
                this.onPIICallback(scrubResult, packageName);
            }

            // 2. Mock RAG Check (for now, until backend is live)
            // In production: const result = await ApiClient.checkHoax(scrubResult.scrubbedText);
            const result = await this.mockCheck(scrubResult.scrubbedText);

            // 3. Trigger Overlay if Dangerous
            if (result.verdict === 'false' || result.verdict === 'likely_false') {
                const alertMsg = `HOAX DETECTED!\n${result.explanation.substring(0, 60)}...`;
                OverlayModule.showBadge('danger', alertMsg);
            }

            // 4. Notify UI
            if (this.onResultCallback) {
                this.onResultCallback(result, packageName);
            }

        } catch (error) {
            console.error('[HoaxChecker] Error processing text:', error);
            if (this.onErrorCallback) {
                this.onErrorCallback(error as Error, packageName);
            }
        }
    };

    private async mockCheck(text: string): Promise<HoaxCheckResult> {
        // Simulate API delay
        await new Promise<void>(resolve => setTimeout(() => resolve(), 1500));

        const lower = text.toLowerCase();

        // 1. Shortened URL Detection (TC-010)
        const shortUrlRegex = /\b(bit\.ly|goo\.gl|tinyurl\.com|t\.co|is\.gd|youtu\.be)\b/i;
        if (shortUrlRegex.test(lower)) {
            return {
                verdict: 'likely_false',
                confidence: 0.85,
                explanation: 'Contains shortened URL. These are often used to hide malicious links.',
                sources: []
            };
        }

        // 2. Homograph/Typosquatting Check (TC-011)
        // Simple check for "g0ogle" or mixed scripts (naively)
        if (lower.includes('g0ogle') || lower.includes('paypa1') || lower.includes('facebo0k')) {
            return {
                verdict: 'false',
                confidence: 0.99,
                explanation: 'Phishing Detected: Typosquatting (fake domain name).',
                sources: []
            };
        }

        // 3. Keyword Check
        if (lower.includes('free money') || lower.includes('winner') || lower.includes('conspiracy')) {
            return {
                verdict: 'false',
                confidence: 0.95,
                explanation: 'Contains common scam/hoax keywords.',
                sources: []
            };
        }

        return {
            verdict: 'true',
            confidence: 0.8,
            explanation: 'Content appears factual.',
            sources: []
        };
    }
}

export const hoaxChecker = new HoaxChecker();
