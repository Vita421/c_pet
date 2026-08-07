package com.clawd.pet
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class DeckActivity : AppCompatActivity() {
    private lateinit var deckContainer: LinearLayout
    private lateinit var historyContainer: LinearLayout
    private lateinit var addDeckButton: Button
    private lateinit var clearHistoryButton: Button
    private lateinit var refreshHistoryButton: ImageButton
    private lateinit var deckManager: DeckManager
    private var decks: MutableList<Deck> = mutableListOf()
    private var importTargetDeck: Deck? = null
    private var currentEditDialog: AlertDialog? = null

    companion object {
        private const val IMPORT_FILE_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deck)
        title = "牌组管理"
        deckContainer = findViewById(R.id.deckContainer)
        historyContainer = findViewById(R.id.historyContainer)
        addDeckButton = findViewById(R.id.addDeckButton)
        clearHistoryButton = findViewById(R.id.clearHistoryButton)
        refreshHistoryButton = findViewById(R.id.refreshHistoryButton)
        deckManager = DeckManager(this)
        decks = deckManager.loadDecks()

        addDeckButton.setOnClickListener { showAddDeckDialog() }
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
        refreshHistoryButton.setOnClickListener {
            refreshHistory()
            Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show()
        }
        refreshDecks()
        refreshHistory()
    }

    override fun onResume() {
        super.onResume()
        decks = deckManager.loadDecks()
        refreshDecks()
        refreshHistory()
    }

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun textColor(): Int = if (isDarkMode()) 0xFFe0e0e0.toInt() else 0xFF2d2d2d.toInt()
    private fun secondaryColor(): Int = if (isDarkMode()) 0xFFa0a0a0.toInt() else 0xFF505050.toInt()
    private fun hintColor(): Int = if (isDarkMode()) 0xFF707070.toInt() else 0xFF909090.toInt()

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
                setTextColor(textColor())
                textSize = 16f
            }
            val nameText = TextView(this).apply {
                text = "${deck.name}（${deck.cards.size}张）"
                setTextColor(if (deck.isActive) 0xFFffab40.toInt() else textColor())
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
                setTextColor(secondaryColor())
                textSize = 13f
                setPadding(0, dpToPx(4), 0, dpToPx(4))
            }
            historyContainer.addView(tv)
        }
        if (history.isEmpty()) {
            val tv = TextView(this).apply {
                text = "还没有抽签记录。拖拽 Clawd 并左右快速晃动试试！"
                setTextColor(hintColor())
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
            setTextColor(textColor())
            setHintTextColor(hintColor())
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
        currentEditDialog?.dismiss()
        currentEditDialog = null
        val container = ScrollView(this).apply {
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }
        val innerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(innerLayout)
        if (deck.cards.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "（空牌组）"
                setTextColor(hintColor())
                textSize = 14f
                setPadding(0, 0, 0, dpToPx(8))
            }
            innerLayout.addView(emptyText)
        } else {
            for (i in deck.cards.indices) {
                val cardText = TextView(this).apply {
                    text = "• ${deck.cards[i]}"
                    setTextColor(textColor())
                    textSize = 14f
                    setPadding(0, dpToPx(4), 0, dpToPx(4))
                    setOnLongClickListener {
                        val cardContent = deck.cards[i]
                        AlertDialog.Builder(this@DeckActivity)
                            .setTitle("删除签文")
                            .setMessage("确定删除「$cardContent」？")
                            .setPositiveButton("删除") { _, _ ->
                                deck.cards.removeAt(i)
                                deckManager.saveDecks(decks)
                                refreshDecks()
                                notifyServiceReload()
                                currentEditDialog?.dismiss()
                                currentEditDialog = null
                                showEditDeckDialog(deck)
                            }
                            .setNegativeButton("取消", null)
                            .show()
                        true
                    }
                }
                innerLayout.addView(cardText)
            }
            val deleteHint = TextView(this).apply {
                text = "长按签文可删除"
                setTextColor(hintColor())
                textSize = 11f
                setPadding(0, dpToPx(4), 0, dpToPx(8))
            }
            innerLayout.addView(deleteHint)
        }
        val addInput = EditText(this).apply {
            hint = "输入新签文"
            setTextColor(textColor())
            setHintTextColor(hintColor())
        }
        innerLayout.addView(addInput)
        val importHint = TextView(this).apply {
            text = "导入格式：txt文件，每行一条签文"
            setTextColor(hintColor())
            textSize = 11f
            setPadding(0, dpToPx(8), 0, 0)
        }
        innerLayout.addView(importHint)
        val dialog = AlertDialog.Builder(this)
            .setTitle("编辑：${deck.name}")
            .setView(container)
            .setPositiveButton("添加签文") { _, _ ->
                val text = addInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    deck.cards.add(text)
                    deckManager.saveDecks(decks)
                    refreshDecks()
                    notifyServiceReload()
                    Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show()
                }
                currentEditDialog = null
            }
            .setNegativeButton("关闭") { _, _ -> currentEditDialog = null }
            .setNeutralButton("更多…", null)
            .setOnDismissListener { currentEditDialog = null }
            .create()
        dialog.show()
        currentEditDialog = dialog
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
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(this, "文件暂时不可用，请稍后重试", Toast.LENGTH_LONG).show()
                return
            }
            val reader = BufferedReader(InputStreamReader(inputStream))
            var count = 0
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    deck.cards.add(trimmed)
                    count++
                }
            }
            reader.close()
            deckManager.saveDecks(decks)
            refreshDecks()
            notifyServiceReload()
            Toast.makeText(this, "导入了${count}条签文", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "文件暂时不可用，请稍后重试", Toast.LENGTH_LONG).show()
        }
    }

    private fun notifyServiceReload() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_RELOAD_DECKS
        }
        try { startForegroundService(intent) } catch (e: Exception) {}
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    @Deprecated("Use ActivityResult API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_FILE_CODE && resultCode == Activity.RESULT_OK && data?.data != null) {
            importTargetDeck?.let { deck -> importTxtFile(data.data!!, deck) }
            importTargetDeck = null
        }
    }
}
