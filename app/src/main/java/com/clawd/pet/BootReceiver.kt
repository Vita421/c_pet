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

        // Auto-start AppGuard if it was previously enabled
        val guardPrefs = context.getSharedPreferences(AppGuardService.PREFS_NAME, Context.MODE_PRIVATE)
        val guardEnabled = guardPrefs.getBoolean(AppGuardService.KEY_ENABLED, false)
        val guardPackages = guardPrefs.getStringSet(AppGuardService.KEY_PACKAGES, emptySet()) ?: emptySet()
        if (guardEnabled && guardPackages.isNotEmpty()) {
            val guardIntent = Intent(context, AppGuardService::class.java)
            context.startForegroundService(guardIntent)
        }
    }
}
