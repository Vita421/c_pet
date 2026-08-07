package com.clawd.pet
import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.TypedValue
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import java.util.Calendar

class OverlayService : Service(), SensorEventListener {
    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null
    private var fortuneView: View? = null
    private var fortuneParams: WindowManager.LayoutParams? = null
    private var whisperView: View? = null
    private var whisperParams: WindowManager.LayoutParams? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var petSizePx = 0

    private val handler = Handler(Looper.getMainLooper())
    private val whisperHandler = Handler(Looper.getMainLooper())
    // Walk state
    private var isWalking = false
    private var walkDirection = 1
    private var walkStepsRemaining = 0
    // Peek state
    private var isPeeking = false
    private var peekSide = 0
    // Sensor
    private var sensorManager: SensorManager? = null
    private var linearAccel: Sensor? = null
    // Drag state
    private var isDragging = false
    private var isHanging = false
    // Fortune: finger direction reversal detection
    private var lastDragX = 0f
    private var lastDragDirection = 0
    private var directionChangeCount = 0
    private var firstDirectionChangeTime = 0L
    private var fortuneTriggered = false
    // Physics bounce state
    private var isBouncing = false
    private var bounceVx = 0f
    private var bounceVy = 0f
    private var sensorRegistered = false
    // Shake accumulation for bounce trigger
    private var shakeAccumCount = 0
    private var shakeAccumStart = 0L
    private var lastShakeAccumTime = 0L
    // Deck
    private lateinit var deckManager: DeckManager
    private var decks: MutableList<Deck> = mutableListOf()
    // Battery state
    private var isLowBattery = false
    private var isCharging = false
    private var batteryReceiver: BroadcastReceiver? = null

    companion object {
        private const val CHANNEL_ID = "clawd_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PET_SIZE_DP = 80
        private const val GIF_PAD_LEFT_DP = 8
        private const val GIF_PAD_RIGHT_DP = 8
        private const val GIF_PAD_TOP_DP = 16
        private const val GIF_PAD_BOTTOM_DP = 0
        private const val WALK_STEP_PX = 2
        private const val WALK_INTERVAL_MS = 50L
        private const val WALK_OVERFLOW_DP = 10
        private const val PEEK_BODY_OUT_DP = 16
        // Fortune trigger
        private const val FORTUNE_DIRECTION_CHANGES = 4
        private const val FORTUNE_WINDOW_MS = 1500L
        private const val FORTUNE_DELAY_MS = 1000L
        // Physics bounce (modeled after AccelerometerBallBounce)
        private const val BOUNCE_DAMPING = 0.98f
        private const val BOUNCE_WALL_FACTOR = 0.6f
        private const val BOUNCE_ACCEL_SCALE = 1.5f
        private const val BOUNCE_STOP_SPEED = 1.5f
        private const val BOUNCE_UPDATE_MS = 16L
        // Shake detection for bounce trigger
        private const val SHAKE_THRESHOLD = 12f
        private const val SHAKE_ACCUM_NEEDED = 2
        private const val SHAKE_ACCUM_WINDOW_MS = 3000L
        private const val SHAKE_DEBOUNCE_MS = 200L
        // Battery thresholds
        private const val LOW_BATTERY_THRESHOLD = 30
        const val ACTION_RELOAD_DECKS = "com.clawd.pet.RELOAD_DECKS"
        // Supabase whisper
        private const val SUPABASE_URL = "https://oonkoosthtghkctzqutu.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9vbmtvb3N0aHRnaGtjdHpxdXR1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU5MTI1MTYsImV4cCI6MjEwMTQ4ODUxNn0.cH5I-_m0fJ1xba7VD_0q4sSWbJgJtcZ4d5FSKr4uSNk"
        private const val WHISPER_INTERVAL_MS = 1800_000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        val dm = resources.displayMetrics
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
        petSizePx = dpToPx(PET_SIZE_DP)
        deckManager = DeckManager(this)
        decks = deckManager.loadDecks()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        setupOverlay()
        registerSensor()
        registerBatteryReceiver()
        scheduleWalk()
        startWhisperRotation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RELOAD_DECKS) {
            decks = deckManager.loadDecks()
        }
        return START_STICKY
    }

    private fun registerSensor() {
        if (!sensorRegistered) {
            linearAccel?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                sensorRegistered = true
            }
        }
    }

    private fun unregisterSensor() {
        if (sensorRegistered) {
            sensorManager?.unregisterListener(this)
            sensorRegistered = false
        }
    }

    // === BATTERY AWARENESS ===
    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val pct = (level * 100) / scale
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    onBatteryUpdate(pct, charging)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun onBatteryUpdate(pct: Int, charging: Boolean) {
        val wasLow = isLowBattery
        val wasCharging = isCharging
        isCharging = charging
        isLowBattery = !charging && pct <= LOW_BATTERY_THRESHOLD

        // Entering low battery mode
        if (isLowBattery && !wasLow) {
            onEnterLowBattery()
        }
        // Exiting low battery (charged above threshold or started charging)
        if (!isLowBattery && wasLow) {
            onExitLowBattery()
        }
        // Started charging
        if (isCharging && !wasCharging) {
            onStartCharging()
        }
        // Stopped charging
        if (!isCharging && wasCharging) {
            onStopCharging()
        }
    }

    private fun onEnterLowBattery() {
        if (isDragging || isPeeking || isBouncing) return
        isWalking = false
        handler.removeCallbacksAndMessages(null)
        // Play low_alert animation for ~8 seconds (4 gif loops), then enter idle_low
        overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('low_alert')", null)
        handler.postDelayed({
            if (isLowBattery && !isDragging && !isPeeking && !isBouncing) {
                overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('idle_low')", null)
                scheduleWalk()
            }
        }, 8000L)
    }

    private fun onExitLowBattery() {
        if (!isDragging && !isPeeking && !isBouncing && !isWalking) {
            overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('idle')", null)
            scheduleWalk()
        }
    }

    private fun onStartCharging() {
        if (isDragging || isPeeking || isBouncing) return
        isWalking = false
        handler.removeCallbacksAndMessages(null)
        overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('charging')", null)
        // After 5s of happy charging animation, resume walk
        handler.postDelayed({
            if (isCharging && !isDragging && !isPeeking && !isBouncing) {
                scheduleWalk()
            }
        }, 5000L)
    }

    private fun onStopCharging() {
        // Will be handled by battery level check
    }

    // Get the correct idle state based on battery (random pool)
    private fun getIdleState(): String {
        return when {
            isCharging -> if (Math.random() < 0.5) "charging" else "idle"
            isLowBattery -> if (Math.random() < 0.4) "low_alert" else "idle_low"
            else -> "idle"
        }
    }

    // Get the correct walk state based on battery and direction
    private fun getWalkState(direction: Int): String {
        return when {
            isLowBattery -> if (direction == 1) "walk_low_right" else "walk_low_left"
            else -> if (direction == 1) "walk_right" else "walk_left"
        }
    }

    // === OVERLAY SETUP ===
    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(
            petSizePx, petSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - petSizePx) / 2
            y = screenHeight / 3
        }
        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }
        windowManager?.addView(overlayView, params)
    }

    // === BOUNDARY HELPERS ===
    private fun getLeftBoundary(): Int = -(dpToPx(GIF_PAD_LEFT_DP) + dpToPx(WALK_OVERFLOW_DP))
    private fun getRightBoundary(): Int = screenWidth - petSizePx + dpToPx(GIF_PAD_RIGHT_DP) + dpToPx(WALK_OVERFLOW_DP)
    private fun getTopBoundary(): Int = -dpToPx(GIF_PAD_TOP_DP)
    private fun getBottomBoundary(): Int = screenHeight - petSizePx
    private fun clampX(x: Int): Int = x.coerceIn(getLeftBoundary(), getRightBoundary())
    private fun clampY(y: Int): Int = y.coerceIn(getTopBoundary(), getBottomBoundary())

    // === WALK LOGIC ===
    private fun scheduleWalk() {
        if (isPeeking || isBouncing) return
        val delay = 3000L + (Math.random() * 4000).toLong()
        handler.postDelayed({
            if (!isWalking && !isPeeking && !isDragging && !isBouncing && !isHanging) {
                startWalking()
            }
        }, delay)
    }

    private fun startWalking() {
        isWalking = true
        walkDirection = if (Math.random() > 0.5) 1 else -1
        params?.let {
            if (walkDirection == -1 && it.x <= getLeftBoundary()) walkDirection = 1
            else if (walkDirection == 1 && it.x >= getRightBoundary()) walkDirection = -1
        }
        walkStepsRemaining = 40 + (Math.random() * 40).toInt()
        val anim = getWalkState(walkDirection)
        overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('$anim')", null)
        walkStep()
    }

    private fun walkStep() {
        if (walkStepsRemaining <= 0 || !isWalking) { stopWalking(); return }
        walkStepsRemaining--
        params?.let {
            val newX = it.x + walkDirection * WALK_STEP_PX
            if (newX <= getLeftBoundary()) {
                walkDirection = 1
                val anim = getWalkState(1)
                overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('$anim')", null)
            } else if (newX >= getRightBoundary()) {
                walkDirection = -1
                val anim = getWalkState(-1)
                overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('$anim')", null)
            }
            it.x = clampX(it.x + walkDirection * WALK_STEP_PX)
            try { windowManager?.updateViewLayout(overlayView, it) } catch (e: Exception) {}
        }
        handler.postDelayed({ walkStep() }, WALK_INTERVAL_MS)
    }

    private fun stopWalking() {
        isWalking = false
        val idle = getIdleState()
        overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('$idle')", null)
        scheduleWalk()
    }

    // === PEEK LOGIC ===
    private fun enterPeek(side: Int) {
        isPeeking = true
        peekSide = side
        isWalking = false
        isBouncing = false
        handler.removeCallbacksAndMessages(null)
        params?.let {
            if (side == -1) {
                it.x = 0
                overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('peek_left')", null)
            } else {
                it.x = screenWidth - petSizePx
                overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('peek_right')", null)
            }
            try { windowManager?.updateViewLayout(overlayView, it) } catch (e: Exception) {}
        }
    }

    private fun exitPeek() { isPeeking = false; peekSide = 0 }

    // === PHYSICS BOUNCE (continuous force model) ===
    private var lastBounceDir = 0
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        val ax = event.values[0]
        val ay = event.values[1]
        if (isDragging || isPeeking || isHanging) return
        val mag = Math.sqrt((ax * ax + ay * ay).toDouble()).toFloat()
        // --- Bounce trigger detection ---
        if (!isBouncing && mag > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastShakeAccumTime < SHAKE_DEBOUNCE_MS) return
            lastShakeAccumTime = now
            if (shakeAccumCount == 0 || now - shakeAccumStart > SHAKE_ACCUM_WINDOW_MS) {
                shakeAccumCount = 1
                shakeAccumStart = now
            } else {
                shakeAccumCount++
            }
            if (shakeAccumCount >= SHAKE_ACCUM_NEEDED) {
                shakeAccumCount = 0
                enterBounceMode()
            }
            return
        }
        // --- Continuous force while bouncing ---
        if (isBouncing) {
            val effectiveAx = if (Math.abs(ax) > 1.5f) ax else 0f
            val effectiveAy = if (Math.abs(ay) > 1.5f) ay else 0f
            bounceVx += -effectiveAx * BOUNCE_ACCEL_SCALE
            bounceVy += effectiveAy * BOUNCE_ACCEL_SCALE
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun enterBounceMode() {
        if (isBouncing) return
        isBouncing = true
        isWalking = false
        handler.removeCallbacksAndMessages(null)
        bounceVx = 0f
        bounceVy = 0f
        lastBounceDir = 0
        scheduleBounceStep()
    }

    private fun scheduleBounceStep() {
        handler.postDelayed({ bounceStep() }, BOUNCE_UPDATE_MS)
    }

    private fun bounceStep() {
        if (!isBouncing) return
        bounceVx *= BOUNCE_DAMPING
        bounceVy *= BOUNCE_DAMPING
        val speed = Math.sqrt((bounceVx * bounceVx + bounceVy * bounceVy).toDouble()).toFloat()
        if (speed < BOUNCE_STOP_SPEED) { exitBounceMode(); return }
        params?.let {
            val newX = it.x + bounceVx.toInt()
            val newY = it.y + bounceVy.toInt()
            if (newX <= getLeftBoundary()) {
                bounceVx = Math.abs(bounceVx) * BOUNCE_WALL_FACTOR
                it.x = getLeftBoundary()
            } else if (newX >= getRightBoundary()) {
                bounceVx = -Math.abs(bounceVx) * BOUNCE_WALL_FACTOR
                it.x = getRightBoundary()
            } else { it.x = newX }
            if (newY <= getTopBoundary()) {
                bounceVy = Math.abs(bounceVy) * BOUNCE_WALL_FACTOR
                it.y = getTopBoundary()
            } else if (newY >= getBottomBoundary()) {
                bounceVy = -Math.abs(bounceVy) * BOUNCE_WALL_FACTOR
                it.y = getBottomBoundary()
            } else { it.y = newY }
            try { windowManager?.updateViewLayout(overlayView, it) } catch (e: Exception) {}
        }
        val newDir = if (bounceVx >= 0) 1 else -1
        if (newDir != lastBounceDir) {
            lastBounceDir = newDir
            val anim = getWalkState(newDir)
            overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('$anim')", null)
        }
        scheduleBounceStep()
    }

    private fun exitBounceMode() {
        isBouncing = false
        bounceVx = 0f
        bounceVy = 0f
        lastBounceDir = 0
        val idle = getIdleState()
        overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('$idle')", null)
        scheduleWalk()
    }

    // === FORTUNE: FINGER DIRECTION REVERSAL ===
    private fun resetFortuneDetection() {
        lastDragX = 0f
        lastDragDirection = 0
        directionChangeCount = 0
        firstDirectionChangeTime = 0L
        fortuneTriggered = false
    }

    private fun detectDirectionReversal(currentX: Float) {
        if (fortuneTriggered) return
        if (lastDragX == 0f) { lastDragX = currentX; return }
        val dx = currentX - lastDragX
        lastDragX = currentX
        if (Math.abs(dx) < 3f) return
        val currentDirection = if (dx > 0) 1 else -1
        if (lastDragDirection != 0 && currentDirection != lastDragDirection) {
            val now = System.currentTimeMillis()
            if (directionChangeCount == 0) firstDirectionChangeTime = now
            if (now - firstDirectionChangeTime > FORTUNE_WINDOW_MS) {
                directionChangeCount = 1; firstDirectionChangeTime = now
            } else {
                directionChangeCount++
            }
            if (directionChangeCount >= FORTUNE_DIRECTION_CHANGES) {
                fortuneTriggered = true
                onFortuneTriggered()
            }
        }
        lastDragDirection = currentDirection
    }

    private fun onFortuneTriggered() {
        handler.postDelayed({
            isDragging = false
            val card = deckManager.drawCard(decks)
            if (card != null) showFortune(card)
            val idle = getIdleState()
            overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('$idle')", null)
        }, FORTUNE_DELAY_MS)
    }

    // === FORTUNE DISPLAY ===
    private fun showFortune(text: String) {
        if (fortuneView != null) return
        val padding = dpToPx(24)
        // Adaptive sizing based on text length
        val textSizeSp = when {
            text.length > 50 -> 15f
            else -> 18f
        }
        val widthFraction = when {
            text.length <= 15 -> 0.0 // WRAP_CONTENT
            text.length <= 50 -> 0.7
            else -> 0.85
        }
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#2d2d2d"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#f5f5f5"))
                cornerRadius = dpToPx(16).toFloat()
            }
            elevation = 8f
        }
        tv.setOnClickListener { dismissFortune(); scheduleWalk() }
        val width = if (widthFraction == 0.0) {
            WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            (screenWidth * widthFraction).toInt()
        }
        fortuneParams = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        fortuneView = tv
        try { windowManager?.addView(tv, fortuneParams) } catch (e: Exception) {}
    }

    private fun dismissFortune() {
        fortuneView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        fortuneView = null; fortuneParams = null
    }

    // === WHISPER BUBBLE (floating speech bubble above Clawd) ===
    private fun showWhisperBubble(text: String) {
        if (whisperView != null) dismissWhisperBubble()
        val paddingH = dpToPx(12)
        val paddingV = dpToPx(8)
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#333333"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(paddingH, paddingV, paddingH, paddingV)
            maxWidth = (screenWidth * 0.6).toInt()
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#f0ffffff"))
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#e0e0e0"))
            }
            elevation = 6f
        }
        tv.setOnClickListener { dismissWhisperBubble() }
        // Position directly above Clawd, bottom of bubble touching top of pet window
        val petX = params?.x ?: (screenWidth / 2)
        val petY = params?.y ?: (screenHeight / 3)
        whisperParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Bubble bottom edge touches pet top edge (pet has 16dp top padding = visual space)
            x = (petX + petSizePx / 2 - dpToPx(60)).coerceIn(dpToPx(4), screenWidth - dpToPx(124))
            y = (petY - dpToPx(28)).coerceAtLeast(dpToPx(4))
        }
        whisperView = tv
        try { windowManager?.addView(tv, whisperParams) } catch (e: Exception) {}
        // Auto dismiss after 8 seconds
        whisperHandler.postDelayed({ dismissWhisperBubble() }, 8000L)
    }

    private fun dismissWhisperBubble() {
        whisperView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        whisperView = null; whisperParams = null
    }

    // === TOUCH HANDLING ===
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var touchStartTime = 0L
    private var hasMoved = false
    private var dragNotified = false

    private fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacksAndMessages(null)
                    isWalking = false
                    isBouncing = false
                    walkStepsRemaining = 0
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    dragNotified = false
                    resetFortuneDetection()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        hasMoved = true
                        if (isHanging) isHanging = false
                        if (isPeeking) exitPeek()
                        params?.let {
                            it.x = initialX + dx
                            it.y = clampY(initialY + dy)
                            try { windowManager?.updateViewLayout(overlayView, it) } catch (e: Exception) {}
                        }
                        if (!dragNotified) {
                            dragNotified = true
                            isDragging = true
                            overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.onDragStart()", null)
                        }
                        detectDirectionReversal(event.rawX)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchStartTime
                    if (!hasMoved) {
                        if (whisperView != null) { dismissWhisperBubble() }
                        if (fortuneView != null) { dismissFortune(); scheduleWalk() }
                        else when {
                            elapsed > 600 -> onLongPress()
                            System.currentTimeMillis() - lastTapTime < 300 -> onDoubleTap()
                            else -> { lastTapTime = System.currentTimeMillis(); onTap() }
                        }
                    } else {
                        isDragging = false
                        if (!fortuneTriggered) onDragEnd() else scheduleWalk()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onTap() {
        if (isHanging) {
            isHanging = false
            val idle = getIdleState()
            overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('$idle')", null)
            scheduleWalk(); return
        }
        overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.onTap()", null)
        if (isPeeking) return
        scheduleWalk()
    }

    private fun onDoubleTap() {
        if (isHanging) {
            isHanging = false
            val idle = getIdleState()
            overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.setState('$idle')", null)
            scheduleWalk(); return
        }
        overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.onDoubleTap()", null)
        if (isPeeking) return
        scheduleWalk()
    }

    private fun onLongPress() {
        isHanging = true
        overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.onLongPress()", null)
    }

    private fun onDragEnd() {
        val currentX = params?.x ?: 0
        val peekPx = dpToPx(PEEK_BODY_OUT_DP)
        val gifLeftPad = dpToPx(GIF_PAD_LEFT_DP)
        val gifRightPad = dpToPx(GIF_PAD_RIGHT_DP)
        val gifLeftEdge = currentX + gifLeftPad
        val gifRightEdge = currentX + petSizePx - gifRightPad
        if (gifLeftEdge < -peekPx) { enterPeek(-1); return }
        else if (gifRightEdge > screenWidth + peekPx) { enterPeek(1); return }
        params?.let {
            it.x = clampX(it.x); it.y = clampY(it.y)
            try { windowManager?.updateViewLayout(overlayView, it) } catch (e: Exception) {}
        }
        overlayView?.evaluateJavascript("window.petEngine&&window.petEngine.onDragEnd()", null)
        scheduleWalk()
    }

    // === NOTIFICATION ===
    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, StopReceiver::class.java)
        val stopPending = PendingIntent.getBroadcast(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("\uD83E\uDD80 Clawd")
            .setContentText("在你身边溜达中")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", stopPending)
            .setOngoing(true).setSilent(true).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Clawd 桌宠", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    // === WHISPER SYSTEM ===
    private fun startWhisperRotation() {
        whisperHandler.postDelayed(object : Runnable {
            override fun run() {
                fetchAndShowWhisper()
                whisperHandler.postDelayed(this, WHISPER_INTERVAL_MS)
            }
        }, 5000L)
    }

    private fun fetchAndShowWhisper() {
        Thread {
            try {
                val condition = getCurrentCondition()
                val urlStr = "$SUPABASE_URL/rest/v1/whispers?used=eq.false&or=(condition.eq.always,condition.eq.$condition)&order=created_at.asc&limit=1"
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("apikey", SUPABASE_KEY)
                conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        val text = obj.getString("text")
                        val id = obj.getLong("id")
                        handler.post { showWhisperBubble(text) }
                        markWhisperUsed(id)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {}
        }.start()
    }

    private fun markWhisperUsed(id: Long) {
        try {
            val urlStr = "$SUPABASE_URL/rest/v1/whispers?id=eq.$id"
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("apikey", SUPABASE_KEY)
            conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.doOutput = true
            conn.outputStream.use { it.write("{\"used\":true}".toByteArray()) }
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {}
    }

    private fun getCurrentCondition(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 0..5 -> "time:late_night"
            hour in 6..9 -> "time:morning"
            hour in 11..13 -> "time:noon"
            hour in 14..17 -> "time:afternoon"
            hour in 22..23 -> "time:late_night"
            else -> "always"
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        whisperHandler.removeCallbacksAndMessages(null)
        unregisterSensor()
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (e: Exception) {} }
        dismissFortune()
        dismissWhisperBubble()
        overlayView?.let { windowManager?.removeView(it); it.destroy() }
        overlayView = null
        super.onDestroy()
    }
}
