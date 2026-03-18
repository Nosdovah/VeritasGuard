package com.veritasguard.overlay

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import com.facebook.react.modules.core.DeviceEventManagerModule
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.content.ComponentName
import android.text.TextUtils
import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import com.veritasguard.accessibility.ScreenTextAccessibilityService

/**
 * OverlayModule — React Native Native Module for System Alert Window
 *
 * Displays non-blocking, floating security badges over other apps.
 * The badge shows a threat level indicator (color-coded circle + message).
 *
 * Security Levels:
 *   - "safe"     → Green badge
 *   - "warning"  → Orange badge
 *   - "danger"   → Red badge
 *   - "info"     → Blue badge
 *
 * Usage from JS:
 *   NativeModules.OverlayModule.showBadge("danger", "Phishing URL detected!")
 *   NativeModules.OverlayModule.dismissBadge()
 */
@ReactModule(name = OverlayModule.NAME)
class OverlayModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "OverlayModule"
        const val TAG = "VG_Overlay"

        // Auto-dismiss timeout in milliseconds
        private const val AUTO_DISMISS_MS = 8000L
    }

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    // Receiver for Overlay Actions
    private val overlayReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.veritasguard.ACTION_SCAN_PHISHING" -> emitEvent("onOverlayAction", "scan_phishing")
                "com.veritasguard.ACTION_VERIFY_FACT" -> emitEvent("onOverlayAction", "verify_fact")
            }
        }
    }

    override fun initialize() {
        super.initialize()
        val filter = android.content.IntentFilter().apply {
            addAction("com.veritasguard.ACTION_SCAN_PHISHING")
            addAction("com.veritasguard.ACTION_VERIFY_FACT")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reactApplicationContext.registerReceiver(overlayReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            reactApplicationContext.registerReceiver(overlayReceiver, filter)
        }
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        try {
            reactApplicationContext.unregisterReceiver(overlayReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }

    private fun emitEvent(eventName: String, data: String) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, data)
    }

    override fun getName(): String = NAME

    /**
     * Check if the app has overlay permission.
     */
    @ReactMethod
    fun canDrawOverlays(promise: Promise) {
        promise.resolve(Settings.canDrawOverlays(reactApplicationContext))
    }

    /**
     * Start the Assistive Touch Overlay Service or Update Result.
     */
    @ReactMethod
    fun showBadge(level: String, message: String, promise: Promise) {
        try {
            if (!Settings.canDrawOverlays(reactApplicationContext)) {
                promise.reject("NO_PERMISSION", "Overlay permission not granted")
                return
            }
            
            val intent = Intent(reactApplicationContext, OverlayService::class.java)
            intent.action = OverlayService.ACTION_SHOW_RESULT
            intent.putExtra(OverlayService.EXTRA_RESULT_TYPE, level)
            intent.putExtra(OverlayService.EXTRA_RESULT_TITLE, if (level == "safe") "Selamat!" else "Waspada!")
            intent.putExtra(OverlayService.EXTRA_RESULT_MESSAGE, message)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reactApplicationContext.startForegroundService(intent)
            } else {
                reactApplicationContext.startService(intent)
            }
            promise.resolve("Overlay Updated")
        } catch (e: Exception) {
            promise.reject("ERROR", e.message, e)
        }
    }

    /**
     * Stop the Assistive Touch Overlay Service.
     */
    @ReactMethod
    fun dismissBadge(promise: Promise) {
        try {
            val intent = Intent(reactApplicationContext, OverlayService::class.java)
            reactApplicationContext.stopService(intent)
            promise.resolve("Overlay Service Stopped")
        } catch (e: Exception) {
            promise.reject("ERROR", e.message, e)
        }
    }

    /**
     * Check if the ScreenTextAccessibilityService is enabled.
     */
    @ReactMethod
    fun isAccessibilityServiceEnabled(promise: Promise) {
        try {
            val enabledServicesSetting = Settings.Secure.getString(
                reactApplicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (enabledServicesSetting == null) {
                promise.resolve(false)
                return
            }
            
            // Simpler check: Does the string contain our Service class name?
            // "com.veritasguard/com.veritasguard.accessibility.ScreenTextAccessibilityService"
            val isEnabled = enabledServicesSetting.contains("ScreenTextAccessibilityService")
            promise.resolve(isEnabled)
        } catch (e: Exception) {
            promise.reject("ERROR", e.message, e)
        }
    }

    // =========================================================================
    // View Construction
    // =========================================================================

    private fun createBadgeView(context: Context, level: String, message: String): LinearLayout {
        val badgeColor = when (level.lowercase()) {
            "safe"    -> Color.parseColor("#22C55E")  // Green
            "warning" -> Color.parseColor("#F59E0B")  // Amber
            "danger"  -> Color.parseColor("#EF4444")  // Red
            "info"    -> Color.parseColor("#3B82F6")  // Blue
            else      -> Color.parseColor("#6B7280")  // Gray
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(context, 12), dpToPx(context, 8), dpToPx(context, 12), dpToPx(context, 8))
            elevation = 8f

            // Rounded background
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A2E"))  // Dark background
                cornerRadius = dpToPx(context, 24).toFloat()
                setStroke(dpToPx(context, 2), badgeColor)
            }

            // Status indicator circle
            val indicator = TextView(context).apply {
                text = "●"
                setTextColor(badgeColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, 0, dpToPx(context, 8), 0)
            }
            addView(indicator)

            // Shield icon
            val shield = TextView(context).apply {
                text = "🛡️"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 0, dpToPx(context, 6), 0)
            }
            addView(shield)

            // Message text
            val messageView = TextView(context).apply {
                text = message
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 2
                maxWidth = dpToPx(context, 200)
            }
            addView(messageView)
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private fun removeOverlayInternal() {
        try {
            dismissRunnable?.let { mainHandler.removeCallbacks(it) }
            overlayView?.let { windowManager?.removeView(it) }
            overlayView = null
        } catch (e: Exception) {
            Log.w(TAG, "Error removing overlay: ${e.message}")
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
