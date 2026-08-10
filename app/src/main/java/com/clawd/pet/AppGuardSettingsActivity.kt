package com.clawd.pet

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AppGuardSettingsActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout
    private lateinit var toggleButton: Button
    private var guardedPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(AppGuardService.PREFS_NAME, MODE_PRIVATE)
        guardedPackages = (prefs.getStringSet(AppGuardService.KEY_PACKAGES, emptySet()) ?: emptySet()).toMutableSet()

        val root = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#1a1a1a"))
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "专注守护"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        layout.addView(TextView(this).apply {
            text = "选择需要提醒的App。打开这些App时会弹窗问你是否有意识地打开。"
            textSize = 13f
            setTextColor(Color.parseColor("#999999"))
            setPadding(0, 0, 0, 24)
        })

        // Usage access permission check
        if (!hasUsageAccess()) {
            layout.addView(makeButton("⚠️ 需要授权「使用情况访问」") {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
        }

        // Toggle service
        val isEnabled = prefs.getBoolean(AppGuardService.KEY_ENABLED, false)
        toggleButton = makeButton(if (isEnabled) "守护中 ✓（点击关闭）" else "点击开启守护").apply {
            setOnClickListener { toggleService() }
        }
        layout.addView(toggleButton)

        // App list
        layout.addView(TextView(this).apply {
            text = "── 选择监控的App ──"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 24, 0, 12)
            gravity = Gravity.CENTER
        })

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(listContainer)
        populateAppList()

        // Log section
        layout.addView(TextView(this).apply {
            text = "── 最近记录 ──"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 24, 0, 12)
            gravity = Gravity.CENTER
        })

        val logPrefs = getSharedPreferences("app_guard_log", MODE_PRIVATE)
        val log = logPrefs.getString("log", "") ?: ""
        val lines = log.split("\n").filter { it.isNotBlank() }.take(10)
        if (lines.isEmpty()) {
            layout.addView(TextView(this).apply {
                text = "暂无记录"
                textSize = 13f
                setTextColor(Color.parseColor("#666666"))
            })
        } else {
            for (line in lines) {
                layout.addView(TextView(this).apply {
                    text = line
                    textSize = 12f
                    setTextColor(Color.parseColor("#aaaaaa"))
                    setPadding(0, 4, 0, 4)
                })
            }
        }

        // Back
        layout.addView(makeButton("返回") { finish() })

        root.addView(layout)
        setContentView(root)
    }

    private fun populateAppList() {
        listContainer.removeAllViews()
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it).toString() }

        for (app in apps) {
            val name = pm.getApplicationLabel(app).toString()
            val pkg = app.packageName
            val isChecked = pkg in guardedPackages

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val cb = CheckBox(this).apply {
                this.isChecked = isChecked
                setOnCheckedChangeListener { _, checked ->
                    if (checked) guardedPackages.add(pkg) else guardedPackages.remove(pkg)
                    savePackages()
                }
            }
            row.addView(cb)

            row.addView(TextView(this).apply {
                text = "$name\n$pkg"
                textSize = 13f
                setTextColor(Color.parseColor("#dddddd"))
                setPadding(12, 0, 0, 0)
            })

            listContainer.addView(row)
        }
    }

    private fun savePackages() {
        getSharedPreferences(AppGuardService.PREFS_NAME, MODE_PRIVATE)
            .edit().putStringSet(AppGuardService.KEY_PACKAGES, guardedPackages).apply()
    }

    private fun toggleService() {
        val prefs = getSharedPreferences(AppGuardService.PREFS_NAME, MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(AppGuardService.KEY_ENABLED, false)
        if (isEnabled) {
            // Stop
            stopService(Intent(this, AppGuardService::class.java))
            prefs.edit().putBoolean(AppGuardService.KEY_ENABLED, false).apply()
            toggleButton.text = "点击开启守护"
        } else {
            // Check permission first
            if (!hasUsageAccess()) {
                Toast.makeText(this, "请先授权「使用情况访问」", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                return
            }
            // Start
            val serviceIntent = Intent(this, AppGuardService::class.java)
            startForegroundService(serviceIntent)
            prefs.edit().putBoolean(AppGuardService.KEY_ENABLED, true).apply()
            toggleButton.text = "守护中 ✓（点击关闭）"
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun makeButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = 20f
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 12
            layoutParams = params
        }
    }

    private fun makeButton(text: String, action: () -> Unit): Button {
        return makeButton(text).apply { setOnClickListener { action() } }
    }
}
