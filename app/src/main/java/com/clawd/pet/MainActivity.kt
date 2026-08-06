package com.clawd.pet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var deckContainer: LinearLayout
    private lateinit var historyContainer: LinearLayout
    private lateinit var addDeckButton: Button
    private lateinit var clearHistoryButton: Button
    private lateinit var deckManager: DeckManager
    private var decks: MutableList<Deck> = mutableListOf()
    private var isRunning = false
    private var importTargetDeck: Deck? = null

    companion object {
        private const val OVERLAY_PERMISSION_CODE = 1001
        private const val IMPORT_FILE_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        deckContainer = findViewById(R.id.deckContainer)
        historyContainer = findViewById(R.id.historyContainer)
        addDeckButton = findViewById(R.id.addDeckButton)
        clearHistoryButton = findViewById(R.id.clearHistoryButton)

        deckManager = DeckManager(this)
        decks = deckManager.loadDecks()

        toggleButton.setOnClickListener {
            if (isRunning) stopPet() else startPet()
        }
        addDeckButton.setOnClickListener {
            showAddDeckDialog()
        }
        clearHistoryButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空历史")
                .setMessage("确定要清空所有抽签记录吗？")
                .setPositiveButton("清空") { _, _ ->
                    deckManager.clearHistory()
                    refreshHistory()
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        updateUI()
        refreshDecks()
        refreshHistory()
    }

    override fun onResume() {
        super.onResume()
        decks = deckManager.loadDecks()
        refreshDecks()
        refreshHistory()
        updateUI()
    }

    private fun startPet() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
            return
        }
        val intent = Intent(this, OverlayService::class.java)
        startForegroundService(intent)
        isRunning = true
        updateUI()
        Toast.makeText(this, "Clawd 出发了！", Toast.LENGTH_SHORT).show()
    }

    private fun stopPet() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        isRunning = false
        updateUI()
        Toast.makeText(this, "Clawd 回窝了", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        if (isRunning) {
            statusText.text = "\uD83E\uDD80 Clawd 正在桌面上溜达"
            toggleButton.text = "关闭 Clawd"
        } else {
            statusText.text = "\uD83E\uDD80 Clawd 在窝里等你"
            toggleButton.text = "启动 Clawd"
        }
    }

    // === DECK MANAGEMENT ===

    private fun refreshDecks() {
        deckContainer.removeAllViews()
        for (deck in decks) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(8), 0, dpToPx(8))
            }
            val indicator = TextView(this).apply {
                text = if (deck.isActive) "▶ " else "   "
                setTextColor(0xFFe0e0e0.toInt())
                textSize = 16f
            }
            val nameText = TextView(this).apply {
                text = "${deck.name}（${deck.cards.size}张）"
                setTextColor(if (deck.isActive) 0xFFffab40.toInt() else 0xFFe0e0e0.toInt())
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val activateBtn = Button(this).apply {
                text = if (deck.isActive) "激活中" else "激活"
                textSize = 12f
                isEnabled = !deck.isActive
                setOnClickListener { activateDeck(deck.id) }
            }
            val editBtn = Button(this).apply {
                text = "编辑"
                textSize = 12f
                setOnClickListener { showEditDeckDialog(deck) }
            }
            row.addView(indicator)
            row.addView(nameText)
            row.addView(activateBtn)
            row.addView(editBtn)
            deckContainer.addView(row)
        }
    }

    private fun refreshHistory() {
        historyContainer.removeAllViews()
        val history = deckManager.loadHistory()
        val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        for (record in history.take(20)) {
            val tv = TextView(this).apply {
                text = "${sdf.format(Date(record.timestamp))}  「${record.cardText}」 (${record.deckName})"
                setTextColor(0xFFa0a0a0.toInt())
                textSize = 13f
                setPadding(0, dpToPx(4), 0, dpToPx(4))
            }
            historyContainer.addView(tv)
        }
        if (history.isEmpty()) {
            val tv = TextView(this).apply {
                text = "还没有抽签记录。拖拽 Clawd 并左右快速晃动试试！"
                setTextColor(0xFF707070.toInt())
                textSize = 13f
            }
            historyContainer.addView(tv)
        }
    }

    private fun activateDeck(deckId: String) {
        decks.forEach { it.isActive = (it.id == deckId) }
        deckManager.saveDecks(decks)
        refreshDecks()
        notifyServiceReload()
    }

    private fun showAddDeckDialog() {
        val input = EditText(this).apply {
            hint = "牌组名称"
            setTextColor(0xFFe0e0e0.toInt())
            setHintTextColor(0xFF707070.toInt())
        }
        AlertDialog.Builder(this)
            .setTitle("新建牌组")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newDeck = Deck(
                        id = "deck_${System.currentTimeMillis()}",
                        name = name,
                        cards = mutableListOf(),
                        isActive = false
                    )
                    decks.add(newDeck)
                    deckManager.saveDecks(decks)
                    refreshDecks()
                    showEditDeckDialog(newDeck)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditDeckDialog(deck: Deck) {
        val container = ScrollView(this).apply {
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }
        val innerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(innerLayout)

        // Cards list with delete buttons
        if (deck.cards.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "（空牌组）"
                setTextColor(0xFF707070.toInt())
                textSize = 14f
                setPadding(0, 0, 0, dpToPx(8))
            }
            innerLayout.addView(emptyText)
        } else {
            for (i in deck.cards.indices) {
                val cardRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dpToPx(2), 0, dpToPx(2))
                }
                val cardText = TextView(this).apply {
                    text = "• ${deck.cards[i]}"
                    setTextColor(0xFFe0e0e0.toInt())
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val deleteBtn = TextView(this).apply {
                    text = "×"
                    setTextColor(0xFFff5252.toInt())
                    textSize = 18f
                    setPadding(dpToPx(12), 0, dpToPx(4), 0)
                    setOnClickListener {
                        deck.cards.removeAt(i)
                        deckManager.saveDecks(decks)
                        refreshDecks()
                        notifyServiceReload()
                        // Refresh the dialog
                        showEditDeckDialog(deck)
                    }
                }
                cardRow.addView(cardText)
                cardRow.addView(deleteBtn)
                innerLayout.addView(cardRow)
            }
        }

        // Add card input
        val addInput = EditText(this).apply {
            hint = "输入新签文（20字以内）"
            setTextColor(0xFFe0e0e0.toInt())
            setHintTextColor(0xFF707070.toInt())
        }
        innerLayout.addView(addInput)

        // Import hint
        val importHint = TextView(this).apply {
            text = "导入格式：txt文件，每行一条签文，建议每条≤20字"
            setTextColor(0xFF707070.toInt())
            textSize = 11f
            setPadding(0, dpToPx(8), 0, 0)
        }
        innerLayout.addView(importHint)

        val dialog = AlertDialog.Builder(this)
            .setTitle("编辑：${deck.name}")
            .setView(container)
            .setPositiveButton("添加签文") { _, _ ->
                val text = addInput.text.toString().trim()
                if (text.isNotEmpty() && text.length <= 20) {
                    deck.cards.add(text)
                    deckManager.saveDecks(decks)
                    refreshDecks()
                    notifyServiceReload()
                    Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show()
                } else if (text.length > 20) {
                    Toast.makeText(this, "签文不能超过20字", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("关闭", null)
            .setNeutralButton("更多…", null)
            .create()

        dialog.show()

        // Override neutral button to show submenu
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            showMoreOptionsDialog(deck, dialog)
        }
    }

    private fun showMoreOptionsDialog(deck: Deck, parentDialog: AlertDialog) {
        val options = mutableListOf("导入txt文件")
        if (deck.id != "default_answer_book") {
            options.add("删除牌组")
        }
        AlertDialog.Builder(this)
            .setTitle("更多操作")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "导入txt文件" -> {
                        importTargetDeck = deck
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "text/plain"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                        startActivityForResult(intent, IMPORT_FILE_CODE)
                        parentDialog.dismiss()
                    }
                    "删除牌组" -> {
                        decks.remove(deck)
                        if (decks.none { it.isActive } && decks.isNotEmpty()) {
                            decks[0].isActive = true
                        }
                        deckManager.saveDecks(decks)
                        refreshDecks()
                        notifyServiceReload()
                        parentDialog.dismiss()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importTxtFile(uri: Uri, deck: Deck) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val reader = BufferedReader(InputStreamReader(inputStream))
            var count = 0
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val card = if (trimmed.length > 20) trimmed.substring(0, 20) else trimmed
                    deck.cards.add(card)
                    count++
                }
            }
            reader.close()
            deckManager.saveDecks(decks)
            refreshDecks()
            notifyServiceReload()
            Toast.makeText(this, "导入了${count}条签文", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun notifyServiceReload() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_RELOAD_DECKS
        }
        try { startForegroundService(intent) } catch (e: Exception) {}
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    @Deprecated("Use ActivityResult API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            OVERLAY_PERMISSION_CODE -> {
                if (Settings.canDrawOverlays(this)) {
                    startPet()
                } else {
                    Toast.makeText(this, "需要悬浮窗权限才能显示 Clawd", Toast.LENGTH_LONG).show()
                }
            }
            IMPORT_FILE_CODE -> {
                if (resultCode == Activity.RESULT_OK && data?.data != null) {
                    importTargetDeck?.let { deck ->
                        importTxtFile(data.data!!, deck)
                    }
                    importTargetDeck = null
                }
            }
        }
    }
}
