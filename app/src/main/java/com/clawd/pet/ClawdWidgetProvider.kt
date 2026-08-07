package com.clawd.pet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class ClawdWidgetProvider : AppWidgetProvider() {

    companion object {
        const val PREFS_NAME = "clawd_widget"
        const val KEY_ENABLED = "widget_enabled"
        const val KEY_FORTUNE_TEXT = "widget_fortune"
        const val KEY_CLAWD_HOME = "clawd_is_home"
        const val KEY_TODAY_FORTUNES = "today_fortunes"
        const val KEY_TODAY_DATE = "today_date"
        const val ACTION_UPDATE = "com.clawd.pet.WIDGET_UPDATE"
        const val ACTION_CLAWD_HOME = "com.clawd.pet.CLAWD_GO_HOME"
        const val ACTION_CLAWD_OUT = "com.clawd.pet.CLAWD_GO_OUT"

        fun isWidgetEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_ENABLED, false)
        }

        fun setWidgetEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
        }

        fun isClawdHome(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_CLAWD_HOME, false)
        }

        fun setClawdHome(context: Context, home: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_CLAWD_HOME, home).apply()
            notifyWidgetUpdate(context)
        }

        fun setFortuneText(context: Context, text: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_FORTUNE_TEXT, text).apply()
            // Also add to today's history
            addTodayFortune(context, text)
            notifyWidgetUpdate(context)
        }

        fun getFortuneText(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_FORTUNE_TEXT, "") ?: ""
        }

        fun addTodayFortune(context: Context, text: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            val savedDate = prefs.getString(KEY_TODAY_DATE, "") ?: ""
            val existingSet = if (savedDate == today) {
                prefs.getStringSet(KEY_TODAY_FORTUNES, mutableSetOf()) ?: mutableSetOf()
            } else {
                mutableSetOf()
            }
            val newSet = existingSet.toMutableSet()
            newSet.add(text)
            prefs.edit()
                .putString(KEY_TODAY_DATE, today)
                .putStringSet(KEY_TODAY_FORTUNES, newSet)
                .apply()
        }

        fun getTodayFortunes(context: Context): List<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            val savedDate = prefs.getString(KEY_TODAY_DATE, "") ?: ""
            if (savedDate != today) return emptyList()
            return (prefs.getStringSet(KEY_TODAY_FORTUNES, emptySet()) ?: emptySet()).toList()
        }

        fun notifyWidgetUpdate(context: Context) {
            val intent = Intent(context, ClawdWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ClawdWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            updateWidget(context, mgr, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_UPDATE -> {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, ClawdWidgetProvider::class.java))
                onUpdate(context, mgr, ids)
            }
        }
    }

    private fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        val isHome = isClawdHome(context)
        val fortune = getFortuneText(context)

        if (isHome) {
            // Clawd is home — yellow background placeholder
            views.setViewVisibility(R.id.widget_fortune_text, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_animation, android.view.View.GONE)
            views.setTextViewText(R.id.widget_fortune_text, "\uD83C\uDFE0 Clawd 在家")
            views.setTextColor(R.id.widget_fortune_text, android.graphics.Color.parseColor("#333333"))
            views.setInt(R.id.widget_root, "setBackgroundColor", android.graphics.Color.parseColor("#FFDD44"))
        } else if (fortune.isNotEmpty()) {
            // Show fortune text
            views.setViewVisibility(R.id.widget_fortune_text, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_animation, android.view.View.GONE)
            views.setTextViewText(R.id.widget_fortune_text, fortune)
            views.setTextColor(R.id.widget_fortune_text, android.graphics.Color.parseColor("#f5f5f5"))
            views.setInt(R.id.widget_root, "setBackgroundColor", android.graphics.Color.parseColor("#E6000000"))
        } else {
            views.setViewVisibility(R.id.widget_fortune_text, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_animation, android.view.View.GONE)
            views.setTextViewText(R.id.widget_fortune_text, "抽一签吧")
            views.setTextColor(R.id.widget_fortune_text, android.graphics.Color.parseColor("#888888"))
            views.setInt(R.id.widget_root, "setBackgroundColor", android.graphics.Color.parseColor("#E6000000"))
        }

        // Click opens menu activity
        val menuIntent = Intent(context, WidgetMenuActivity::class.java)
        menuIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val pendingMenu = PendingIntent.getActivity(
            context, id, menuIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingMenu)

        mgr.updateAppWidget(id, views)
    }
}
