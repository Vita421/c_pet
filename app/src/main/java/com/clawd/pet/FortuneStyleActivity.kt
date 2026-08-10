package com.clawd.pet

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class FortuneStyleActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "fortune_style"
        const val KEY_BG_COLOR = "bg_color"
        const val KEY_TEXT_COLOR = "text_color"
        const val KEY_BG_ALPHA = "bg_alpha"
        const val KEY_ROUNDED = "rounded_corners"

        fun getBgColor(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_BG_COLOR, Color.parseColor("#f5f5f5"))
        }

        fun getTextColor(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_TEXT_COLOR, Color.parseColor("#2d2d2d"))
        }

        fun getBgAlpha(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_BG_ALPHA, 255)
        }

        fun isRounded(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_ROUNDED, true)
        }
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var previewText: TextView
    private lateinit var previewContainer: View

    private var bgColor = Color.parseColor("#f5f5f5")
    private var textColor = Color.parseColor("#2d2d2d")
    private var bgAlpha = 255
    private var rounded = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        bgColor = prefs.getInt(KEY_BG_COLOR, bgColor)
        textColor = prefs.getInt(KEY_TEXT_COLOR, textColor)
        bgAlpha = prefs.getInt(KEY_BG_ALPHA, 255)
        rounded = prefs.getBoolean(KEY_ROUNDED, true)

        val root = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#1a1a1a"))
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "签文样式"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        // Preview
        previewText = TextView(this).apply {
            text = "你不需要先变好，才配被善待。"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
        }
        val previewWrapper = FrameLayout(this).apply {
            setPadding(0, 0, 0, 32)
            addView(previewText)
        }
        previewContainer = previewText
        layout.addView(previewWrapper)
        updatePreview()

        // === Background Color ===
        layout.addView(makeLabel("背景颜色"))
        layout.addView(makeColorInput("背景", bgColor) { color ->
            bgColor = color
            updatePreview()
        })

        // === Text Color ===
        layout.addView(makeLabel("字体颜色"))
        layout.addView(makeColorInput("字体", textColor) { color ->
            textColor = color
            updatePreview()
        })

        // === Background Alpha ===
        layout.addView(makeLabel("背景透明度: ${bgAlpha * 100 / 255}%"))
        val alphaLabel = layout.getChildAt(layout.childCount - 1) as TextView
        val alphaSeekBar = SeekBar(this).apply {
            max = 255
            progress = bgAlpha
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    bgAlpha = progress
                    alphaLabel.text = "背景透明度: ${bgAlpha * 100 / 255}%"
                    updatePreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        layout.addView(alphaSeekBar, marginParams(0, 0, 0, 24))

        // === Corner Style ===
        layout.addView(makeLabel("圆角样式"))
        val cornerToggle = Switch(this).apply {
            text = if (rounded) "圆角" else "尖角"
            isChecked = rounded
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked ->
                rounded = checked
                this.text = if (rounded) "圆角" else "尖角"
                updatePreview()
            }
        }
        layout.addView(cornerToggle, marginParams(0, 0, 0, 32))

        // === Preset Colors ===
        layout.addView(makeLabel("快捷预设"))
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val presets = listOf(
            "默认" to Pair("#f5f5f5", "#2d2d2d"),
            "暗夜" to Pair("#1a1a2e", "#e0e0e0"),
            "暖黄" to Pair("#fff8e1", "#5d4037"),
            "薄荷" to Pair("#e0f2f1", "#004d40"),
            "玫瑰" to Pair("#fce4ec", "#880e4f")
        )
        presets.forEach { (name, colors) ->
            presetRow.addView(Button(this).apply {
                text = name
                textSize = 12f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(colors.first))
                    cornerRadius = 16f
                    setStroke(2, Color.parseColor("#666666"))
                }
                setOnClickListener {
                    bgColor = Color.parseColor(colors.first)
                    textColor = Color.parseColor(colors.second)
                    bgAlpha = 230
                    updatePreview()
                }
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                p.marginEnd = 8
                layoutParams = p
            })
        }
        layout.addView(presetRow, marginParams(0, 0, 0, 32))

        // === Save Button ===
        layout.addView(Button(this).apply {
            text = "保存"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4CAF50"))
                cornerRadius = 24f
            }
            setOnClickListener { saveAndFinish() }
        })

        root.addView(layout)
        setContentView(root)
    }

    private fun updatePreview() {
        val alphaColor = Color.argb(bgAlpha, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
        val radius = if (rounded) 32f else 4f
        previewText.background = GradientDrawable().apply {
            setColor(alphaColor)
            cornerRadius = radius
        }
        previewText.setTextColor(textColor)
    }

    private fun saveAndFinish() {
        prefs.edit()
            .putInt(KEY_BG_COLOR, bgColor)
            .putInt(KEY_TEXT_COLOR, textColor)
            .putInt(KEY_BG_ALPHA, bgAlpha)
            .putBoolean(KEY_ROUNDED, rounded)
            .apply()
        // Notify widget to update
        ClawdWidgetProvider.notifyWidgetUpdate(this)
        Toast.makeText(this, "样式已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun makeLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#cccccc"))
            setPadding(0, 16, 0, 8)
        }
    }

    private fun makeColorInput(label: String, initialColor: Int, onChange: (Int) -> Unit): LinearLayout {
        var r = Color.red(initialColor)
        var g = Color.green(initialColor)
        var b = Color.blue(initialColor)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = 24
            layoutParams = p
        }

        val previewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val colorPreview = View(this).apply {
            background = GradientDrawable().apply { setColor(initialColor); cornerRadius = 8f; setStroke(2, Color.parseColor("#666666")) }
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply { marginEnd = 16 }
        }
        previewRow.addView(colorPreview)
        val hexLabel = TextView(this).apply {
            text = String.format("#%06X", 0xFFFFFF and initialColor)
            textSize = 13f; setTextColor(Color.parseColor("#aaaaaa"))
        }
        previewRow.addView(hexLabel)
        container.addView(previewRow)

        fun refresh() {
            val color = Color.rgb(r, g, b)
            colorPreview.background = GradientDrawable().apply { setColor(color); cornerRadius = 8f; setStroke(2, Color.parseColor("#666666")) }
            hexLabel.text = String.format("#%06X", 0xFFFFFF and color)
            onChange(color)
        }

        fun makeSlider(name: String, initial: Int, color: Int, onVal: (Int) -> Unit): LinearLayout {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 8, 0, 0) }
            row.addView(TextView(this).apply { text = name; textSize = 12f; setTextColor(color); layoutParams = LinearLayout.LayoutParams(dpToPx(20), LinearLayout.LayoutParams.WRAP_CONTENT) })
            val sb = SeekBar(this).apply { max = 255; progress = initial; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, f: Boolean) { onVal(v); refresh() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            row.addView(sb)
            return row
        }

        container.addView(makeSlider("R", r, Color.parseColor("#ff6666")) { r = it })
        container.addView(makeSlider("G", g, Color.parseColor("#66ff66")) { g = it })
        container.addView(makeSlider("B", b, Color.parseColor("#6666ff")) { b = it })

        return container
    }

    private fun marginParams(l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(l, t, r, b) }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
