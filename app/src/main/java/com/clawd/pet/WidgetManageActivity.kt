package com.clawd.pet

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.*
import android.view.Gravity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity

class WidgetManageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUI()
    }

    override fun onResume() {
        super.onResume()
        buildUI()
    }

    private fun buildUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#1a1a1a"))
        }
        val hasWidget = hasWidgetOnScreen()

        // Title
        layout.addView(TextView(this).apply {
            text = "🏠 桌面组件管理"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 32)
        })

        // Status
        layout.addView(TextView(this).apply {
            text = if (hasWidget) "状态：组件已在桌面" else "状态：桌面无组件"
            textSize = 14f
            setTextColor(Color.parseColor("#aaaaaa"))
            setPadding(0, 0, 0, 24)
        })

        // Add widget button (only when no widget exists)
        if (!hasWidget) {
            layout.addView(makeButton("添加桌面组件") {
                requestPinWidget()
            })
        }

        if (hasWidget) {
            // Section: switch fortune
            layout.addView(TextView(this).apply {
                text = "── 切换签文 ──"
                textSize = 14f
                setTextColor(Color.parseColor("#888888"))
                setPadding(0, 24, 0, 12)
                gravity = Gravity.CENTER
            })
            val fortunes = ClawdWidgetProvider.getTodayFortunes(this)
            if (fortunes.isEmpty()) {
                layout.addView(TextView(this).apply {
                    text = "今天还没有抽过签"
                    textSize = 13f
                    setTextColor(Color.parseColor("#666666"))
                    setPadding(0, 0, 0, 16)
                })
            } else {
                for (f in fortunes) {
                    layout.addView(makeButton("📝 $f") {
                        ClawdWidgetProvider.setFortuneText(this, f)
                        Toast.makeText(this, "已更换", Toast.LENGTH_SHORT).show()
                        buildUI()
                    })
                }
            }

            // Section: switch background
            layout.addView(TextView(this).apply {
                text = "── 切换背景 ──"
                textSize = 14f
                setTextColor(Color.parseColor("#888888"))
                setPadding(0, 24, 0, 12)
                gravity = Gravity.CENTER
            })
            val isHome = ClawdWidgetProvider.isClawdHome(this)
            layout.addView(makeButton(if (isHome) "当前：Clawd的家（黄色）" else "当前：签文卡片") {
                ClawdWidgetProvider.setClawdHome(this, !isHome)
                ClawdWidgetProvider.notifyWidgetUpdate(this)
                buildUI()
            })
        }

        // Back
        layout.addView(makeButton("返回") { finish() })

        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)
    }

    private fun makeButton(text: String, action: () -> Unit): Button {
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
            setOnClickListener { action() }
        }
    }

    private fun hasWidgetOnScreen(): Boolean {
        val mgr = android.appwidget.AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(ComponentName(this, ClawdWidgetProvider::class.java))
        return ids.isNotEmpty()
    }

    private fun requestPinWidget() {
        val mgr = getSystemService(Context.APPWIDGET_SERVICE) as? android.appwidget.AppWidgetManager ?: return
        val provider = ComponentName(this, ClawdWidgetProvider::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(provider, null, null)
            }
        }
    }
}
