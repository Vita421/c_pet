package com.clawd.pet
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
    private var isRunning = false

    companion object {
        private const val OVERLAY_PERMISSION_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        deckManageButton = findViewById(R.id.deckManageButton)

        toggleButton.setOnClickListener {
            if (isRunning) stopPet() else startPet()
        }
        deckManageButton.setOnClickListener {
            startActivity(Intent(this, DeckActivity::class.java))
        }
        updateUI()
    }

    override fun onResume() {
        super.onResume()
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

    @Deprecated("Use ActivityResult API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_CODE && Settings.canDrawOverlays(this)) {
            startPet()
        }
    }
}
