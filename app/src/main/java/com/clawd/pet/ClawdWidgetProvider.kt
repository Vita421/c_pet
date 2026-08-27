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
        const val KEY_ROTATION_SHOW_FORTUNE = "rotation_show_fortune"
        const val KEY_TODAY_FORTUNES = "today_fortunes"
        const val KEY_TODAY_DATE = "today_date"
        const val KEY_FORTUNE_CLEARED = "fortune_cleared"
        const val KEY_CURRENT_ANIM = "current_anim"         // idle, sleep, coffee
        const val KEY_ANIM_CHOSEN_TIME = "anim_chosen_time" // millis
        const val KEY_NAP_TAKEN = "nap_taken"               // boolean
        const val KEY_NAP_DATE = "nap_date"                 // yyyy-MM-dd
        const val ACTION_UPDATE = "com.clawd.pet.WIDGET_UPDATE"
        const val ACTION_CLAWD_HOME = "com.clawd.pet.CLAWD_GO_HOME"
        const val ACTION_CLAWD_OUT = "com.clawd.pet.CLAWD_GO_OUT"

        // Animation durations in millis
        private const val DURATION_IDLE = 30 * 60 * 1000L   // 30 min
        private const val DURATION_COFFEE = 10 * 60 * 1000L // 10 min
        private const val DURATION_SLEEP = 30 * 60 * 1000L  // 30 min

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
            if (home) {
                // Force re-pick animation when coming home
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_ANIM_CHOSEN_TIME, 0).apply()
            }
            notifyWidgetUpdate(context)
        }
        fun setFortuneText(context: Context, text: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putString(KEY_FORTUNE_TEXT, text)
            if (text.isEmpty()) {
                editor.putBoolean(KEY_FORTUNE_CLEARED, true)
            } else {
                editor.putBoolean(KEY_FORTUNE_CLEARED, false)
                addTodayFortune(context, text)
                if (prefs.getBoolean(KEY_CLAWD_HOME, false)) {
                    editor.putBoolean(KEY_ROTATION_SHOW_FORTUNE, true)
                }
            }
            editor.apply()
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

        /**
         * Determine which animation to show based on time-of-day probability.
         * Returns "idle", "sleep", or "coffee".
         */
        fun pickAnimation(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val lastPick = prefs.getLong(KEY_ANIM_CHOSEN_TIME, 0)
            val currentAnim = prefs.getString(KEY_CURRENT_ANIM, "idle") ?: "idle"

            // Check if we need to re-pick
            val duration = when (currentAnim) {
                "coffee" -> DURATION_COFFEE
                "sleep" -> DURATION_SLEEP
                else -> DURATION_IDLE
            }
            if (lastPick > 0 && (now - lastPick) < duration) {
                return currentAnim // Not time to re-pick yet
            }

            // Get current hour/minute
            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = cal.get(java.util.Calendar.MINUTE)
            val timeMinutes = hour * 60 + minute

            // Check nap status for today
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            val napDate = prefs.getString(KEY_NAP_DATE, "") ?: ""
            val napTaken = if (napDate == today) prefs.getBoolean(KEY_NAP_TAKEN, false) else false

            // Time slots and probabilities
            val rand = Math.random()
            val picked: String

            when {
                // 7:00 - 13:00
                timeMinutes in 420 until 780 -> {
                    picked = when {
                        rand < 0.70 -> "idle"
                        rand < 0.90 -> "coffee"
                        else -> "sleep"
                    }
                }
                // 13:00 - 14:30 (nap time)
                timeMinutes in 780 until 870 -> {
                    if (napTaken) {
                        // Already napped today, just idle
                        picked = "idle"
                    } else {
                        picked = if (rand < 0.50) "sleep" else "idle"
                        if (picked == "sleep") {
                            // Mark nap taken
                            prefs.edit()
                                .putBoolean(KEY_NAP_TAKEN, true)
                                .putString(KEY_NAP_DATE, today)
                                .apply()
                        }
                    }
                }
                // 14:30 - 22:30
                timeMinutes in 870 until 1350 -> {
                    picked = when {
                        rand < 0.70 -> "idle"
                        rand < 0.90 -> "coffee"
                        else -> "sleep"
                    }
                }
                // 22:30 - 7:00 (night)
                else -> {
                    picked = if (rand < 0.60) "sleep" else "idle"
                }
            }

            // Save pick
            prefs.edit()
                .putString(KEY_CURRENT_ANIM, picked)
                .putLong(KEY_ANIM_CHOSEN_TIME, now)
                .apply()

            return picked
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
        val rounded = FortuneStyleActivity.isRounded(context)
        val bgDrawable = if (rounded) R.drawable.widget_bg else R.drawable.widget_bg_sharp
        views.setInt(R.id.widget_root, "setBackgroundResource", bgDrawable)

        // Hide all animation views first
        views.setViewVisibility(R.id.widget_animation, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_animation_sharp, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_sleep, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_sleep_sharp, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_coffee, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_coffee_sharp, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_empty_sharp, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_fortune_text, android.view.View.GONE)

        if (isHome) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val showFortune = prefs.getBoolean(KEY_ROTATION_SHOW_FORTUNE, false)
            if (showFortune && fortune.isNotEmpty()) {
                // Show fortune text with style
                val styleBgColor = FortuneStyleActivity.getBgColor(context)
                val styleTextColor = FortuneStyleActivity.getTextColor(context)
                val styleBgAlpha = FortuneStyleActivity.getBgAlpha(context)
                val rotBgColor = android.graphics.Color.argb(
                    styleBgAlpha,
                    android.graphics.Color.red(styleBgColor),
                    android.graphics.Color.green(styleBgColor),
                    android.graphics.Color.blue(styleBgColor)
                )
                views.setViewVisibility(R.id.widget_fortune_text, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_fortune_text, fortune)
                views.setTextColor(R.id.widget_fortune_text, styleTextColor)
                views.setColorStateList(R.id.widget_root, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(rotBgColor))
            } else {
                // Show home animation based on picked type
                val animType = pickAnimation(context)
                val viewId = when (animType) {
                    "sleep" -> if (rounded) R.id.widget_sleep else R.id.widget_sleep_sharp
                    "coffee" -> if (rounded) R.id.widget_coffee else R.id.widget_coffee_sharp
                    else -> if (rounded) R.id.widget_animation else R.id.widget_animation_sharp
                }
                views.setViewVisibility(viewId, android.view.View.VISIBLE)
            }
        } else if (fortune.isNotEmpty()) {
            val styleBgColor = FortuneStyleActivity.getBgColor(context)
            val styleTextColor = FortuneStyleActivity.getTextColor(context)
            val styleBgAlpha = FortuneStyleActivity.getBgAlpha(context)
            val widgetBgColor = android.graphics.Color.argb(
                styleBgAlpha,
                android.graphics.Color.red(styleBgColor),
                android.graphics.Color.green(styleBgColor),
                android.graphics.Color.blue(styleBgColor)
            )
            views.setViewVisibility(R.id.widget_fortune_text, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_fortune_text, fortune)
            views.setTextColor(R.id.widget_fortune_text, styleTextColor)
            views.setColorStateList(R.id.widget_root, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(widgetBgColor))
        } else {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cleared = prefs.getBoolean(KEY_FORTUNE_CLEARED, false)
            if (cleared) {
                // Show empty home background instead of plain color
                val emptyId = if (rounded) R.id.widget_empty else R.id.widget_empty_sharp
                views.setViewVisibility(emptyId, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_fortune_text, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_fortune_text, "抽一签吧")
                views.setTextColor(R.id.widget_fortune_text, android.graphics.Color.parseColor("#666666"))
                views.setColorStateList(R.id.widget_root, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFF5D6")))
            }
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
