package com.clawd.pet
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
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
        const val EXTRA_FROM_WIDGET = "from_widget"
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
    private lateinit var saveButton: Button
    private var bgColor = Color.parseColor("#f5f5f5")
    private var textColor = Color.parseColor("#2d2d2d")
    private var bgAlpha = 255
    private var rounded = true
    private var fromWidget = false

    // References for syncing presets to sliders
    private var bgHsv = FloatArray(3)
    private var textHsv = FloatArray(3)
    private var bgSliders = mutableListOf<SeekBar>()
    private var textSliders = mutableListOf<SeekBar>()
    private var bgHexInput: EditText? = null
    private var textHexInput: EditText? = null
    private var alphaSeekBar: SeekBar? = null
    private var alphaLabel: TextView? = null
    private var updatingFromPreset = false

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    private fun pageBgColor(): Int = if (isDarkMode()) 0xFF1a1a1a.toInt() else 0xFFf5f5f5.toInt()
    private fun pageTextColor(): Int = if (isDarkMode()) 0xFFe0e0e0.toInt() else 0xFF2d2d2d.toInt()
    private fun labelColor(): Int = if (isDarkMode()) 0xFFcccccc.toInt() else 0xFF505050.toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fromWidget = intent.getBooleanExtra(EXTRA_FROM_WIDGET, false)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        bgColor = prefs.getInt(KEY_BG_COLOR, bgColor)
        textColor = prefs.getInt(KEY_TEXT_COLOR, textColor)
        bgAlpha = prefs.getInt(KEY_BG_ALPHA, 255)
        rounded = prefs.getBoolean(KEY_ROUNDED, true)
        Color.colorToHSV(bgColor, bgHsv)
        Color.colorToHSV(textColor, textHsv)
        val root = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(pageBgColor())
        }
        // Title
        layout.addView(TextView(this).apply {
            text = "签文样式"
            textSize = 20f
            setTextColor(pageTextColor())
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
        layout.addView(makeColorInput("背景", bgColor, bgHsv, bgSliders, { bgHexInput = it }) { color ->
            bgColor = color
            updatePreview()
        })
        // === Text Color ===
        layout.addView(makeLabel("字体颜色"))
        layout.addView(makeColorInput("字体", textColor, textHsv, textSliders, { textHexInput = it }) { color ->
            textColor = color
            updatePreview()
        })
        // === Background Alpha ===
        val aLabel = makeLabel("背景透明度: ${bgAlpha * 100 / 255}%")
        alphaLabel = aLabel
        layout.addView(aLabel)
        val aSeekBar = SeekBar(this).apply {
            max = 255
            progress = bgAlpha
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    bgAlpha = progress
                    aLabel.text = "背景透明度: ${bgAlpha * 100 / 255}%"
                    updatePreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        alphaSeekBar = aSeekBar
        layout.addView(aSeekBar, marginParams(0, 0, 0, 24))
        // === Corner Style ===
        layout.addView(makeLabel("圆角样式"))
        val cornerToggle = Switch(this).apply {
            text = if (rounded) "圆角" else "尖角"
            isChecked = rounded
            setTextColor(pageTextColor())
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
            "默认" to Triple("#f5f5f5", "#2d2d2d", 255),
            "暗夜" to Triple("#1a1a2e", "#e0e0e0", 230),
            "暖黄" to Triple("#fff8e1", "#5d4037", 230),
            "薄荷" to Triple("#e0f2f1", "#004d40", 230),
            "玫瑰" to Triple("#fce4ec", "#880e4f", 230)
        )
        presets.forEach { (name, config) ->
            presetRow.addView(Button(this).apply {
                text = name
                textSize = 12f
                setTextColor(pageTextColor())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(config.first))
                    cornerRadius = 16f
                    setStroke(2, Color.parseColor("#666666"))
                }
                setOnClickListener {
                    applyPreset(Color.parseColor(config.first), Color.parseColor(config.second), config.third)
                }
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                p.marginEnd = 8
                layoutParams = p
            })
        }
        layout.addView(presetRow, marginParams(0, 0, 0, 32))
        // === Save Button ===
        saveButton = Button(this).apply {
            text = "保存"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4CAF50"))
                cornerRadius = 24f
            }
            setOnClickListener { saveStyle() }
        }
        layout.addView(saveButton)

        root.setBackgroundColor(pageBgColor())
        root.addView(layout)
        setContentView(root)
    }

    private fun applyPreset(newBgColor: Int, newTextColor: Int, newAlpha: Int) {
        updatingFromPreset = true
        bgColor = newBgColor
        textColor = newTextColor
        bgAlpha = newAlpha

        // Update HSV arrays
        Color.colorToHSV(bgColor, bgHsv)
        Color.colorToHSV(textColor, textHsv)

        // Sync background sliders
        if (bgSliders.size == 3) {
            bgSliders[0].progress = bgHsv[0].toInt()
            bgSliders[1].progress = (bgHsv[1] * 100).toInt()
            bgSliders[2].progress = (bgHsv[2] * 100).toInt()
        }
        bgHexInput?.setText(String.format("#%06X", 0xFFFFFF and bgColor))

        // Sync text sliders
        if (textSliders.size == 3) {
            textSliders[0].progress = textHsv[0].toInt()
            textSliders[1].progress = (textHsv[1] * 100).toInt()
            textSliders[2].progress = (textHsv[2] * 100).toInt()
        }
        textHexInput?.setText(String.format("#%06X", 0xFFFFFF and textColor))

        // Sync alpha
        alphaSeekBar?.progress = bgAlpha
        alphaLabel?.text = "背景透明度: ${bgAlpha * 100 / 255}%"

        updatePreview()
        updatingFromPreset = false
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
    private fun saveStyle() {
        prefs.edit()
            .putInt(KEY_BG_COLOR, bgColor)
            .putInt(KEY_TEXT_COLOR, textColor)
            .putInt(KEY_BG_ALPHA, bgAlpha)
            .putBoolean(KEY_ROUNDED, rounded)
            .apply()
        // Notify widget to update
        ClawdWidgetProvider.notifyWidgetUpdate(this)
        if (fromWidget) {
            Toast.makeText(this, "样式已保存", Toast.LENGTH_SHORT).show()
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        } else {
            saveButton.text = "已保存 ✓"
            saveButton.background = GradientDrawable().apply {
                setColor(Color.parseColor("#388E3C"))
                cornerRadius = 24f
            }
        }
    }
    private fun makeLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(labelColor())
            setPadding(0, 16, 0, 8)
        }
    }
    private fun makeColorInput(
        label: String,
        initialColor: Int,
        hsv: FloatArray,
        slidersRef: MutableList<SeekBar>,
        hexRef: (EditText) -> Unit,
        onChange: (Int) -> Unit
    ): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = 24
            layoutParams = p
        }
        val previewRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val colorPreview = View(this).apply {
            background = GradientDrawable().apply { setColor(initialColor); cornerRadius = 8f; setStroke(2, Color.parseColor("#666666")) }
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply { marginEnd = 16 }
        }
        previewRow.addView(colorPreview)
        val hexInput = EditText(this).apply {
            setText(String.format("#%06X", 0xFFFFFF and initialColor))
            textSize = 13f
            setTextColor(Color.parseColor("#eeeeee"))
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(16, 8, 16, 8)
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        hexRef(hexInput)
        previewRow.addView(hexInput)
        container.addView(previewRow)
        var updatingFromSlider = false
        var updatingFromHex = false
        fun refresh() {
            updatingFromSlider = true
            val color = Color.HSVToColor(hsv)
            colorPreview.background = GradientDrawable().apply { setColor(color); cornerRadius = 8f; setStroke(2, Color.parseColor("#666666")) }
            if (!updatingFromHex && !updatingFromPreset) {
                hexInput.setText(String.format("#%06X", 0xFFFFFF and color))
            }
            onChange(color)
            updatingFromSlider = false
        }
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingFromSlider || updatingFromPreset) return
                val text = s?.toString()?.trim() ?: return
                val hex = if (text.startsWith("#")) text else "#$text"
                if (hex.length != 7) return
                try {
                    val color = Color.parseColor(hex)
                    updatingFromHex = true
                    Color.colorToHSV(color, hsv)
                    if (slidersRef.size == 3) {
                        slidersRef[0].progress = hsv[0].toInt()
                        slidersRef[1].progress = (hsv[1] * 100).toInt()
                        slidersRef[2].progress = (hsv[2] * 100).toInt()
                    }
                    colorPreview.background = GradientDrawable().apply { setColor(color); cornerRadius = 8f; setStroke(2, Color.parseColor("#666666")) }
                    onChange(color)
                    updatingFromHex = false
                } catch (_: Exception) {}
            }
        })
        fun makeSlider(name: String, max: Int, initial: Int, color: Int, onVal: (Int) -> Unit): LinearLayout {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 8, 0, 0) }
            row.addView(TextView(this).apply { text = name; textSize = 12f; setTextColor(color); layoutParams = LinearLayout.LayoutParams(dpToPx(24), LinearLayout.LayoutParams.WRAP_CONTENT) })
            val sb = SeekBar(this).apply { this.max = max; progress = initial; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, f: Boolean) {
                    if (!updatingFromPreset) { onVal(v); refresh() }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            row.addView(sb)
            slidersRef.add(sb)
            return row
        }
        container.addView(makeSlider("H", 360, hsv[0].toInt(), Color.parseColor("#ff9800")) { hsv[0] = it.toFloat() })
        container.addView(makeSlider("S", 100, (hsv[1] * 100).toInt(), Color.parseColor("#8bc34a")) { hsv[1] = it / 100f })
        container.addView(makeSlider("V", 100, (hsv[2] * 100).toInt(), Color.parseColor("#03a9f4")) { hsv[2] = it / 100f })
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
