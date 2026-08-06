package com.clawd.pet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Deck(
    val id: String,
    val name: String,
    val cards: MutableList<String>,
    var isActive: Boolean = false
)

data class DrawRecord(
    val timestamp: Long,
    val deckName: String,
    val cardText: String
)

class DeckManager(private val context: Context) {

    private val decksFile get() = File(context.filesDir, "decks.json")
    private val historyFile get() = File(context.filesDir, "history.json")

    fun loadDecks(): MutableList<Deck> {
        if (!decksFile.exists()) {
            val defaultDecks = mutableListOf(createDefaultDeck())
            saveDecks(defaultDecks)
            return defaultDecks
        }
        return try {
            val json = JSONArray(decksFile.readText())
            val decks = mutableListOf<Deck>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val cards = mutableListOf<String>()
                val cardsArr = obj.getJSONArray("cards")
                for (j in 0 until cardsArr.length()) {
                    cards.add(cardsArr.getString(j))
                }
                decks.add(Deck(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    cards = cards,
                    isActive = obj.optBoolean("isActive", false)
                ))
            }
            if (decks.none { it.isActive } && decks.isNotEmpty()) {
                decks[0].isActive = true
            }
            decks
        } catch (e: Exception) {
            mutableListOf(createDefaultDeck())
        }
    }

    fun saveDecks(decks: List<Deck>) {
        val json = JSONArray()
        for (deck in decks) {
            val obj = JSONObject()
            obj.put("id", deck.id)
            obj.put("name", deck.name)
            obj.put("isActive", deck.isActive)
            val cardsArr = JSONArray()
            deck.cards.forEach { cardsArr.put(it) }
            obj.put("cards", cardsArr)
            json.put(obj)
        }
        decksFile.writeText(json.toString(2))
    }

    fun getActiveDeck(decks: List<Deck>): Deck? {
        return decks.firstOrNull { it.isActive }
    }

    fun drawCard(decks: List<Deck>): String? {
        val active = getActiveDeck(decks) ?: return null
        if (active.cards.isEmpty()) return null
        val card = active.cards.random()
        addHistory(DrawRecord(
            timestamp = System.currentTimeMillis(),
            deckName = active.name,
            cardText = card
        ))
        return card
    }

    fun loadHistory(): List<DrawRecord> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val json = JSONArray(historyFile.readText())
            val records = mutableListOf<DrawRecord>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                records.add(DrawRecord(
                    timestamp = obj.getLong("timestamp"),
                    deckName = obj.getString("deckName"),
                    cardText = obj.getString("cardText")
                ))
            }
            records.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearHistory() {
        historyFile.delete()
    }

    private fun addHistory(record: DrawRecord) {
        val records = loadHistory().toMutableList()
        records.add(0, record)
        val trimmed = records.take(100)
        val json = JSONArray()
        for (r in trimmed) {
            val obj = JSONObject()
            obj.put("timestamp", r.timestamp)
            obj.put("deckName", r.deckName)
            obj.put("cardText", r.cardText)
            json.put(obj)
        }
        historyFile.writeText(json.toString(2))
    }

    private fun createDefaultDeck(): Deck {
        return Deck(
            id = "default_answer_book",
            name = "答案之书",
            cards = mutableListOf(
                "今天适合摸鱼",
                "去喝水",
                "站起来伸个懒腰",
                "答案是肯定的",
                "再想想吧",
                "相信你的直觉",
                "今天会有好事发生",
                "不急，慢慢来",
                "去晒太阳",
                "该吃点蛋白质了",
                "深呼吸三次",
                "今天的你很好",
                "放下手机看看窗外",
                "答案明天揭晓",
                "现在不是时候",
                "大胆去做",
                "先吃饱再说",
                "你已经很努力了",
                "今晚早睡",
                "出去走走"
            ),
            isActive = true
        )
    }
}
