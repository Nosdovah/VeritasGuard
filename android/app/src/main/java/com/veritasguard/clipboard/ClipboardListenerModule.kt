package com.veritasguard.clipboard

import android.content.ClipboardManager
import android.content.Context
import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * ClipboardListenerModule — React Native Native Module
 *
 * Bridges Android's ClipboardManager to React Native.
 * Starts a Foreground Service that monitors clipboard changes
 * and triggers a Headless JS task for phishing URL analysis.
 */
@ReactModule(name = ClipboardListenerModule.NAME)
class ClipboardListenerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "ClipboardListenerModule"
        const val TAG = "VG_Clipboard"
    }

    override fun getName(): String = NAME

    /**
     * Start the clipboard monitoring foreground service.
     * Called from React Native: NativeModules.ClipboardListenerModule.startListening()
     */
    @ReactMethod
    fun startListening(promise: Promise) {
        try {
            val context = reactApplicationContext
            val intent = Intent(context, ClipboardListenerService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            Log.i(TAG, "Clipboard listener service started")
            promise.resolve("Service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start clipboard listener", e)
            promise.reject("SERVICE_START_ERROR", "Failed to start clipboard listener: ${e.message}", e)
        }
    }

    /**
     * Stop the clipboard monitoring service.
     */
    @ReactMethod
    fun stopListening(promise: Promise) {
        try {
            val context = reactApplicationContext
            val intent = Intent(context, ClipboardListenerService::class.java)
            context.stopService(intent)
            Log.i(TAG, "Clipboard listener service stopped")
            promise.resolve("Service stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop clipboard listener", e)
            promise.reject("SERVICE_STOP_ERROR", "Failed to stop clipboard listener: ${e.message}", e)
        }
    }

    /**
     * Check if the clipboard monitoring service is currently active.
     */
    @ReactMethod
    fun isListening(promise: Promise) {
        promise.resolve(ClipboardListenerService.isRunning)
    }
}
