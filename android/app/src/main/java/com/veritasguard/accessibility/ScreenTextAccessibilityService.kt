package com.veritasguard.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.facebook.react.ReactApplication
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * ScreenTextAccessibilityService — Android Accessibility Service
 *
 * Scrapes screen text from scoped packages (Instagram, WhatsApp, Twitter,
 * Telegram, TikTok, Facebook) when window content changes.
 *
 * Sends extracted text to React Native via DeviceEventEmitter so the
 * PII Scrubber can process it before forwarding to the backend.
 *
 * Anti-Spyware Compliance:
 *   - Package-scoped via accessibility_service_config.xml
 *   - Only captures text content, no screenshots or keylogging
 *   - Sends to JS layer for PII scrubbing before any network transmission
 */
class ScreenTextAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "VG_A11yService"
        const val EVENT_NAME = "onScreenTextCaptured"

        // Debounce: minimum ms between processing events
        private const val DEBOUNCE_MS = 2000L

        // Maximum text length to prevent memory issues
        private const val MAX_TEXT_LENGTH = 5000
    }

    private var lastProcessedTime: Long = 0
    private var lastProcessedText: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "ScreenTextAccessibilityService connected")

        // Programmatic configuration (supplements XML config)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 500
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Debounce: skip if too soon after last event
        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < DEBOUNCE_MS) return

        val packageName = event.packageName?.toString() ?: return

        try {
            val rootNode = rootInActiveWindow ?: return
            val extractedText = StringBuilder()
            traverseNodeTree(rootNode, extractedText)
            rootNode.recycle()

            val text = extractedText.toString().trim()
            if (text.isEmpty() || text.length < 20) return // Skip trivial content

            // Truncate to prevent excessive data
            val truncatedText = if (text.length > MAX_TEXT_LENGTH) {
                text.substring(0, MAX_TEXT_LENGTH)
            } else text

            // Deduplicate: skip if same text as last capture
            if (truncatedText == lastProcessedText) return

            lastProcessedText = truncatedText
            lastProcessedTime = now

            // Send to React Native
            sendEventToReactNative(packageName, truncatedText)
            Log.d(TAG, "Captured ${truncatedText.length} chars from $packageName")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "ScreenTextAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "ScreenTextAccessibilityService destroyed")
    }

    // =========================================================================
    // Node Tree Traversal
    // =========================================================================

    /**
     * Recursively traverse the accessibility node tree and extract text content.
     * Collects text from TextViews and content descriptions.
     * 
     * SECURITY: Skips nodes marked as passwords to prevent credential logging.
     */
    private fun traverseNodeTree(node: AccessibilityNodeInfo, builder: StringBuilder) {
        // ANTI-SPYWARE: Ignore password fields
        if (node.isPassword) return

        // Extract text from this node
        node.text?.let { text ->
            val textStr = text.toString().trim()
            if (textStr.isNotEmpty() && textStr.length > 1) {
                builder.append(textStr).append(" ")
            }
        }

        // Also capture content descriptions (often used for image captions)
        node.contentDescription?.let { desc ->
            val descStr = desc.toString().trim()
            if (descStr.isNotEmpty() && descStr.length > 2) {
                builder.append(descStr).append(" ")
            }
        }

        // Recurse into child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNodeTree(child, builder)
                child.recycle()
            }
        }
    }

    // =========================================================================
    // React Native Bridge
    // =========================================================================

    /**
     * Emit a DeviceEvent to React Native with the captured text.
     * JS layer receives this via NativeEventEmitter subscription.
     */
    private fun sendEventToReactNative(packageName: String, text: String) {
        try {
            val reactApp = application as? ReactApplication ?: return
            val reactContext = reactApp.reactNativeHost
                .reactInstanceManager
                .currentReactContext ?: return

            val params: WritableMap = Arguments.createMap().apply {
                putString("packageName", packageName)
                putString("text", text)
                putDouble("timestamp", System.currentTimeMillis().toDouble())
            }

            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(EVENT_NAME, params)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send event to React Native", e)
        }
    }
}
