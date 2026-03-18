/**
 * VeritasGuard — Phishing Detector Headless JS Task
 *
 * This task runs in a background JS context (no UI) when triggered by
 * the native ClipboardListenerService via PhishingTaskService.
 *
 * Flow:
 *   1. Receives clipboard text + metadata from native layer
 *   2. Extracts URLs using regex
 *   3. Performs client-side heuristics (domain entropy, suspicious TLD)
 *   4. Sends URL to FastAPI backend via SSL-pinned client
 *   5. Triggers overlay badge if threat detected
 *
 * Registration:
 *   AppRegistry.registerHeadlessTask('PhishingDetectorTask', () => PhishingDetectorTask)
 */

import { NativeModules } from 'react-native';
import { analyzeUrl, UrlAnalysisResult } from '../services/apiClient';

const { OverlayModule } = NativeModules;

// ============================================================================
// Types
// ============================================================================

interface ClipboardTaskData {
    clipboardText: string;
    timestamp: number;
    description: string;
}

// ============================================================================
// URL Extraction & Heuristics
// ============================================================================

/** Extract hostname from a URL string without relying on URL.hostname typing */
function parseHostname(url: string): string {
    const match = url.match(/^https?:\/\/([^/:?#]+)/i);
    return match ? match[1] : '';
}

/** Extract URLs from text */
function extractUrls(text: string): string[] {
    const urlRegex = /https?:\/\/[^\s<>"{}|\\^`\[\]]+/gi;
    const matches = text.match(urlRegex);
    return matches ? [...new Set(matches)] : []; // Deduplicate
}

/** Calculate Shannon entropy of a domain (higher = more suspicious) */
function calculateDomainEntropy(domain: string): number {
    const freq: Record<string, number> = {};
    for (const char of domain) {
        freq[char] = (freq[char] || 0) + 1;
    }

    let entropy = 0;
    const len = domain.length;
    for (const count of Object.values(freq)) {
        const p = count / len;
        entropy -= p * Math.log2(p);
    }
    return entropy;
}

/** Suspicious TLD check */
const SUSPICIOUS_TLDS = [
    '.tk', '.ml', '.ga', '.cf', '.gq', '.xyz', '.top', '.work',
    '.click', '.link', '.info', '.buzz', '.surf', '.rest', '.icu',
];

function hasSuspiciousTLD(url: string): boolean {
    const hostname = parseHostname(url).toLowerCase();
    if (!hostname) { return false; }
    return SUSPICIOUS_TLDS.some(tld => hostname.endsWith(tld));
}

/** Check for IP-based URLs (common in phishing) */
function isIPBasedUrl(url: string): boolean {
    const hostname = parseHostname(url);
    return /^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(hostname);
}

/** Check for excessive subdomains (common in phishing) */
function hasExcessiveSubdomains(url: string): boolean {
    const hostname = parseHostname(url);
    if (!hostname) { return false; }
    const parts = hostname.split('.');
    return parts.length > 4;
}

// ============================================================================
// Main Task
// ============================================================================

/**
 * Phishing Detector Headless Task
 *
 * Entry point called by React Native's HeadlessJsTaskService.
 * This function runs WITHOUT any UI context.
 */
async function PhishingDetectorTask(taskData: ClipboardTaskData): Promise<void> {
    console.log('[VeritasGuard] PhishingDetectorTask started');

    try {
        const { clipboardText, timestamp } = taskData;

        // Step 1: Extract URLs from clipboard text
        const urls = extractUrls(clipboardText);
        if (urls.length === 0) {
            console.log('[VeritasGuard] No URLs found in clipboard text');
            return;
        }

        console.log(`[VeritasGuard] Found ${urls.length} URL(s) to analyze`);

        // Step 2: Analyze each URL
        for (const url of urls) {
            // Client-side pre-screening
            const domain = parseHostname(url) || url;

            const entropy = calculateDomainEntropy(domain);
            const suspiciousTLD = hasSuspiciousTLD(url);
            const ipBased = isIPBasedUrl(url);
            const excessiveSubdomains = hasExcessiveSubdomains(url);

            // Quick client-side risk score
            let clientRisk = 0;
            if (entropy > 4.0) { clientRisk += 25; }
            if (suspiciousTLD) { clientRisk += 20; }
            if (ipBased) { clientRisk += 30; }
            if (excessiveSubdomains) { clientRisk += 15; }

            console.log(
                `[VeritasGuard] Client-side analysis: domain=${domain}, entropy=${entropy.toFixed(2)}, ` +
                `clientRisk=${clientRisk}`
            );

            // Step 3: Send to backend for full analysis
            try {
                const result: UrlAnalysisResult = await analyzeUrl(url, {
                    client_entropy: entropy.toString(),
                    client_risk: clientRisk.toString(),
                    clipboard_timestamp: timestamp.toString(),
                });

                // Step 4: Show overlay badge based on result
                if (result.riskScore >= 70) {
                    await OverlayModule.showBadge(
                        'danger',
                        `⚠️ Phishing detected! Risk: ${result.riskScore}/100`,
                    );
                } else if (result.riskScore >= 40) {
                    await OverlayModule.showBadge(
                        'warning',
                        `🔍 Suspicious URL detected. Risk: ${result.riskScore}/100`,
                    );
                } else {
                    console.log(`[VeritasGuard] URL is safe: ${domain} (risk: ${result.riskScore})`);
                }
            } catch (apiError) {
                // If backend is unreachable, use client-side score
                console.warn('[VeritasGuard] Backend unreachable, using client-side analysis');

                if (clientRisk >= 50) {
                    await OverlayModule.showBadge(
                        'warning',
                        `⚠️ Suspicious URL detected (offline analysis)`,
                    );
                }
            }
        }
    } catch (error) {
        console.error('[VeritasGuard] PhishingDetectorTask error:', error);
    }

    console.log('[VeritasGuard] PhishingDetectorTask completed');
}

export default PhishingDetectorTask;
