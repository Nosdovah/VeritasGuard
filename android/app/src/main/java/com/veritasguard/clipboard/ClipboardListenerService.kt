package com.veritasguard.clipboard

import android.app.*
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

/**
 * ClipboardListenerService — Android Foreground Service
 *
 * Runs as a persistent foreground service with a visible notification.
 * Registers OnPrimaryClipChangedListener to monitor clipboard changes.
 * When a clipboard change is detected:
 *   1. Extracts the clip text
 *   2. Creates a metadata bundle (text, timestamp, source package)
 *   3. Fires a Headless JS task (PhishingDetectorTask) via PhishingTaskService
 *
 * Android 16+ Compliance:
 *   - foregroundServiceType = dataSync | specialUse
 *   - PROPERTY_SPECIAL_USE_FGS_SUBTYPE declared in manifest
 */
class ClipboardListenerService : Service() {

    companion object {
        const val TAG = "VG_ClipService"
        const val CHANNEL_ID = "veritasguard_clipboard_channel"
        const val NOTIFICATION_ID = 1001
        const val HEADLESS_TASK_NAME = "PhishingDetectorTask"

        @Volatile
        var isRunning = false
    }

    private lateinit var clipboardManager: ClipboardManager

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipChanged()
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ClipboardListenerService created")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipChangedListener)

        isRunning = true
        Log.i(TAG, "Clipboard listener registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Restart if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipChangedListener)
        isRunning = false
        Log.i(TAG, "ClipboardListenerService destroyed, listener removed")
    }

    // =========================================================================
    // Clipboard Event Handler
    // =========================================================================

    private fun handleClipChanged() {
        try {
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) return

            val clipText = clip.getItemAt(0)?.coerceToText(this)?.toString() ?: return
            if (clipText.isBlank()) return

            Log.d(TAG, "Clipboard change detected: ${clipText.take(50)}...")

            // Build metadata payload for the Headless JS task
            val taskData = Arguments.createMap().apply {
                putString("clipboardText", clipText)
                putDouble("timestamp", System.currentTimeMillis().toDouble())
                putString("description", clip.description?.label?.toString() ?: "unknown")
            }

            // Launch Headless JS task via PhishingTaskService
            val serviceIntent = Intent(this, PhishingTaskService::class.java)
            serviceIntent.putExtras(Arguments.toBundle(taskData) ?: return)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Log.i(TAG, "PhishingDetectorTask dispatched for clipboard content")
        } catch (e: SecurityException) {
            // Android 13+ may restrict clipboard access for non-focused apps
            Log.w(TAG, "Clipboard access restricted (expected on Android 13+): ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling clipboard change", e)
        }
    }

    // =========================================================================
    // Notification (required for Foreground Service)
    // =========================================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VeritasGuard Security Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors clipboard for phishing URLs to keep you safe"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, Class.forName("com.veritasguard.MainActivity")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VeritasGuard Active")
            .setContentText("Security monitoring is running — clipboard protection enabled")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }
}
