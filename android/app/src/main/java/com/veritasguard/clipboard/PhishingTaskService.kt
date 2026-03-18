package com.veritasguard.clipboard

import android.content.Intent
import android.os.Bundle
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

/**
 * PhishingTaskService — Headless JS Task Bridge
 *
 * Extends HeadlessJsTaskService to run the React Native
 * "PhishingDetectorTask" in a background JS context.
 *
 * This service is started by ClipboardListenerService when
 * a clipboard change is detected. It passes the clipboard
 * metadata (text, timestamp) to the JS task for URL extraction
 * and phishing analysis.
 */
class PhishingTaskService : HeadlessJsTaskService() {

    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? {
        val extras: Bundle = intent?.extras ?: return null

        return HeadlessJsTaskConfig(
            ClipboardListenerService.HEADLESS_TASK_NAME,
            Arguments.fromBundle(extras),
            5000,  // timeout: 5 seconds
            true   // allow task in foreground
        )
    }
}
