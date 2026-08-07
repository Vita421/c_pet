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
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#CC000000"))
        }
        // Title
        layout.addView(TextView(this).apply {
            text = "Clawd 组件"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })
        // Button: change fortune
        layout.addView(makeButton("换签文") { showFortuneList() })
        // Button: change background
        layout.addView(makeButton("换背景") { switchBackground() })
        // Button: knock
        layout.addView(makeButton("敲门召唤") { knockClawd() })
        // Button: close
        layout.addView(makeButton("关闭") { finish() })

        setContentView(layout)
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
            Toast.makeText(this, "已更换", Toast.LENGTH_SHORT).show()
            finish()
        }
        builder.show()
    }

    private fun switchBackground() {
        // Toggle to yellow/home background
        val isHome = ClawdWidgetProvider.isClawdHome(this)
        if (!isHome) {
            ClawdWidgetProvider.setClawdHome(this, true)
            // Hide overlay via broadcast
            val intent = android.content.Intent("com.clawd.pet.GO_HOME")
            intent.setPackage(packageName)
            sendBroadcast(intent)
            Toast.makeText(this, "Clawd 回家了", Toast.LENGTH_SHORT).show()
        } else {
            ClawdWidgetProvider.setClawdHome(this, false)
            Toast.makeText(this, "已切换为签文", Toast.LENGTH_SHORT).show()
        }
        ClawdWidgetProvider.notifyWidgetUpdate(this)
        finish()
    }

    private fun knockClawd() {
        val intent = android.content.Intent("com.clawd.pet.KNOCK")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        ClawdWidgetProvider.setClawdHome(this, false)
        Toast.makeText(this, "Clawd 出来啦！", Toast.LENGTH_SHORT).show()
        finish()
    }
}
