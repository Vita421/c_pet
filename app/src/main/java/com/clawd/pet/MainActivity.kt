package com.clawd.pet
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var deckManageButton: Button
    private lateinit var widgetButton: Button
    companion object {
        private const val OVERLAY_PERMISSION_CODE = 1001
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        deckManageButton = findViewById(R.id.deckManageButton)
        widgetButton = findViewById(R.id.widgetButton)
        toggleButton.setOnClickListener { onToggleClick() }
        deckManageButton.setOnClickListener {
            startActivity(Intent(this, DeckActivity::class.java))
        }
        widgetButton.setOnClickListener {
            startActivity(Intent(this, WidgetManageActivity::class.java))
        }
        updateUI()
    }
    override fun onResume() {
        super.onResume()
        updateUI()
    }
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == OverlayService::class.java.name }
    }
    private fun onToggleClick() {
        val running = isServiceRunning()
        val isHome = ClawdWidgetProvider.isClawdHome(this)
        when {
            !running -> startPet()
            running && isHome -> knockClawd()
            running && !isHome -> stopPet()
        }
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
        updateUI()
        Toast.makeText(this, "Clawd 出发了！", Toast.LENGTH_SHORT).show()
    }
    private fun stopPet() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        ClawdWidgetProvider.setClawdHome(this, false)
        updateUI()
        Toast.makeText(this, "Clawd 回窝了", Toast.LENGTH_SHORT).show()
    }
    private fun knockClawd() {
        // Send explicit broadcast to service
        val intent = Intent("com.clawd.pet.KNOCK")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        ClawdWidgetProvider.setClawdHome(this, false)
        updateUI()
        Toast.makeText(this, "Clawd 出来啦！", Toast.LENGTH_SHORT).show()
    }
    private fun hasWidgetOnScreen(): Boolean {
        val mgr = android.appwidget.AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(android.content.ComponentName(this, ClawdWidgetProvider::class.java))
        return ids.isNotEmpty()
    }
    private fun updateUI() {
        val running = isServiceRunning()
        val isHome = ClawdWidgetProvider.isClawdHome(this)
        val widgetEnabled = hasWidgetOnScreen()
        when {
            !running -> {
                statusText.text = "\uD83E\uDD80 Clawd 在窝里等你"
                toggleButton.text = "启动 Clawd"
            }
            running && isHome -> {
                statusText.text = "\uD83C\uDFE0 Clawd 在家里休息"
                toggleButton.text = "敲敲 Clawd 的门"
            }
            else -> {
                statusText.text = "\uD83E\uDD80 Clawd 正在桌面上溜达"
                toggleButton.text = "关闭 Clawd"
            }
        }
        widgetButton.text = if (widgetEnabled) "桌面组件管理" else "开启桌面组件"
    }
    @Deprecated("Use ActivityResult API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_CODE && Settings.canDrawOverlays(this)) {
            startPet()
        }
    }
}
