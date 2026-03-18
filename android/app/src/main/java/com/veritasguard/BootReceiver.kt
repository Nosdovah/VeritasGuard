package com.veritasguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.veritasguard.clipboard.ClipboardListenerService

/**
 * BootReceiver — Restarts security services after device reboot.
 *
 * Ensures persistent protection by re-launching the
 * ClipboardListenerService foreground service on BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "VG_Boot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed — restarting VeritasGuard services")

            val serviceIntent = Intent(context, ClipboardListenerService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
