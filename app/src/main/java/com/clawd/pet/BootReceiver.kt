package com.clawd.pet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        // Only auto-start if overlay permission is already granted
        if (!Settings.canDrawOverlays(context)) return
        val serviceIntent = Intent(context, OverlayService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
