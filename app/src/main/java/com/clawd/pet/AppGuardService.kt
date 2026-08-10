package com.clawd.pet

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat

class AppGuardService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastTriggeredPackage = ""
    private var lastTriggerTime = 0L

    companion object {
        private const val CHANNEL_ID = "clawd_guard_channel"
        private const val NOTIFICATION_ID = 1002
        private const val CHECK_INTERVAL_MS = 2000L
        private const val COOLDOWN_MS = 1800_000L // 30 min cooldown
        const val PREFS_NAME = "app_guard"
        const val KEY_ENABLED = "guard_enabled"
        const val KEY_PACKAGES = "guard_packages"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startChecking()
    }

    private fun startChecking() {
        handler.post(object : Runnable {
            override fun run() {
                checkForegroundApp()
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        })
    }

    private fun checkForegroundApp() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val packages = prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()
        if (packages.isEmpty()) return

        val currentApp = getForegroundPackage() ?: return
        if (currentApp !in packages) return

        val now = System.currentTimeMillis()
        // Cooldown: don't trigger again for same app within 30 min
        if (currentApp == lastTriggeredPackage && now - lastTriggerTime < COOLDOWN_MS) return

        lastTriggeredPackage = currentApp
        lastTriggerTime = now

        // Launch guard overlay
        val intent = Intent(this, AppGuardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_package", currentApp)
        }
        startActivity(intent)
    }

    private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10000, now)
        if (stats.isNullOrEmpty()) return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Clawd 专注守护")
            .setContentText("正在帮你留意无意识打开的App")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true).setSilent(true).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "专注守护", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
