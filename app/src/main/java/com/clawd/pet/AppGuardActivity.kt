package com.clawd.pet

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class AppGuardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targetPackage = intent.getStringExtra("target_package") ?: ""
        val appName = getAppName(targetPackage)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 120, 64, 120)
            setBackgroundColor(Color.parseColor("#F0000000"))
        }

        // App name
        root.addView(TextView(this).apply {
            text = "你刚打开了 $appName"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        })

        // Question
        root.addView(TextView(this).apply {
            text = "你打开它的目的是？"
            textSize = 15f
            setTextColor(Color.parseColor("#cccccc"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        })

        // Option 1: I have a purpose
        root.addView(makeButton("我有明确目的") {
            logAndFinish(targetPackage, "有明确目的")
        })

        // Option 2: just habit
        root.addView(makeButton("习惯性点开了…") {
            showFollowUp(root, targetPackage)
        })

        setContentView(root)
    }

    private fun showFollowUp(root: LinearLayout, targetPackage: String) {
        root.removeAllViews()

        root.addView(TextView(this).apply {
            text = "要不要先做点别的？"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        })

        root.addView(makeButton("算了还是刷") {
            logAndFinish(targetPackage, "习惯性→继续刷")
        })

        root.addView(makeButton("回去找向晦") {
            logAndFinish(targetPackage, "习惯性→找向晦")
            val intent = packageManager.getLaunchIntentForPackage("com.ai.assistance.operit")
            if (intent != null) startActivity(intent)
        })

        root.addView(makeButton("做别的去") {
            logAndFinish(targetPackage, "习惯性→做别的")
            // Just go home
            val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        })
    }

    private fun logAndFinish(targetPackage: String, choice: String) {
        // Simple local log
        val prefs = getSharedPreferences("app_guard_log", MODE_PRIVATE)
        val log = prefs.getString("log", "") ?: ""
        val time = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date())
        val appName = getAppName(targetPackage)
        val entry = "$time | $appName | $choice\n"
        prefs.edit().putString("log", entry + log).apply()
        finish()
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast(".")
        }
    }

    private fun makeButton(text: String, action: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = 24f
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 16
            layoutParams = params
            setOnClickListener { action() }
        }
    }
}
