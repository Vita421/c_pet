package com.clawd.pet

import android.os.Bundle
import android.widget.*
import android.view.Gravity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity

class WidgetMenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isHome = ClawdWidgetProvider.isClawdHome(this)
        val isServiceStopped = !isServiceRunning()

        if (isServiceStopped) {
            // State C: service not running, just open app
            startActivity(android.content.Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#CC000000"))
        }
        layout.addView(TextView(this).apply {
            text = "Clawd 组件"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        // Always show: change fortune
        layout.addView(makeButton("换签文") { showFortuneList() })

        if (isHome) {
            // State B: Clawd is home
            layout.addView(makeButton("敲门召唤") { knockClawd() })
        } else {
            // State A: Clawd is outside
            layout.addView(makeButton("换背景") { toggleBackground() })
            layout.addView(makeButton("敲门") {
                Toast.makeText(this, "没人在家哦，Clawd在外面溜达呢", Toast.LENGTH_SHORT).show()
            })
        }

        layout.addView(makeButton("关闭") { finish() })
        setContentView(layout)
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == OverlayService::class.java.name }
    }

    private fun makeButton(text: String, action: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#444444"))
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

    private fun showFortuneList() {
        val fortunes = ClawdWidgetProvider.getTodayFortunes(this)
        if (fortunes.isEmpty()) {
            Toast.makeText(this, "今天还没有抽过签", Toast.LENGTH_SHORT).show()
            return
        }
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("选择签文")
        builder.setItems(fortunes.toTypedArray()) { _, which ->
            ClawdWidgetProvider.setFortuneText(this, fortunes[which])
            ClawdWidgetProvider.notifyWidgetUpdate(this)
            finish()
        }
        builder.show()
    }

    private fun toggleBackground() {
        // Toggle between fortune display and empty home background
        // This does NOT affect Clawd's overlay — just the widget look
        val fortune = ClawdWidgetProvider.getFortuneText(this)
        if (fortune.isNotEmpty()) {
            // Currently showing fortune → switch to empty home
            ClawdWidgetProvider.setFortuneText(this, "")
            ClawdWidgetProvider.notifyWidgetUpdate(this)
            Toast.makeText(this, "已切换为空家背景", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "没有签文可显示，先去抽一签吧", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun knockClawd() {
        val intent = android.content.Intent("com.clawd.pet.KNOCK")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        ClawdWidgetProvider.setClawdHome(this, false)
        ClawdWidgetProvider.notifyWidgetUpdate(this)
        Toast.makeText(this, "Clawd 出来啦！", Toast.LENGTH_SHORT).show()
        finish()
    }
}
