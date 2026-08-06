package com.clawd.pet

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service(), SensorEventListener {
    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    private var fortuneView: View? = null
    private var fortuneParams: WindowManager.LayoutParams? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var petSizePx = 0

    private val handler = Handler(Looper.getMainLooper())

    // Walk state
    private var isWalking = false
    private var walkDirection = 1
    private var walkStepsRemaining = 0

    // Peek state
    private var isPeeking = false
    private var peekSide = 0

    // Sensor
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    // Drag state
    private var isDragging = false

    // Fortune: finger direction reversal detection
    private var lastDragX = 0f
    private var lastDragDirection = 0 // -1 left, 1 right, 0 unknown
    private var directionChangeCount = 0
    private var firstDirectionChangeTime = 0L
    private var fortuneTriggered = false

    // Physics bounce state
    private var isBouncing = false
    private var bounceVelocityX = 0f
    private var bounceVelocityY = 0f
    private var isBouncePaused = false // wall hit idle pause
    private var sensorRegistered = false

    // Deck
    private lateinit var deckManager: DeckManager
    private var decks: MutableList<Deck> = mutableListOf()

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

        // Fortune trigger: finger direction changes
        private const val FORTUNE_DIRECTION_CHANGES = 4
        private const val FORTUNE_WINDOW_MS = 1500L
        private const val FORTUNE_DELAY_MS = 1000L

        // Physics bounce
        private const val BOUNCE_ACCEL_SCALE = 3.0f
        private const val BOUNCE_FRICTION = 0.985f
        private const val BOUNCE_RESTITUTION = 0.7f
        private const val BOUNCE_STOP_SPEED = 1.5f
        private const val BOUNCE_UPDATE_MS = 16L
        private const val BOUNCE_WALL_IDLE_MS = 1000L
        private const val BOUNCE_TRIGGER_THRESHOLD = 18f

        const val ACTION_RELOAD_DECKS = "com.clawd.pet.RELOAD_DECKS"
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
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setupOverlay()
        registerSensor()
        scheduleWalk()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RELOAD_DECKS) {
            decks = deckManager.loadDecks()
        }
        return START_STICKY
    }

    // === SENSOR MANAGEMENT ===

    private fun registerSensor() {
        if (!sensorRegistered) {
            accelerometer?.let {
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

    // === OVERLAY SETUP ===

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            petSizePx,
            petSizePx,
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
            if (!isWalking && !isPeeking && !isDragging && !isBouncing) {
                startWalking()
            }
        }, delay)
    }

    private fun startWalking() {
        isWalking = true
        walkDirection = if (Math.random() > 0.5) 1 else -1
        params?.let {
            if (walkDirection == -1 && it.x <= getLeftBoundary()) {
                walkDirection = 1
            } else if (walkDirection == 1 && it.x >= getRightBoundary()) {
                walkDirection = -1
            }
        }
        walkStepsRemaining = 40 + (Math.random() * 40).toInt()
        val dir = if (walkDirection == 1) "walk_right" else "walk_left"
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.setState('$dir')", null
        )
        walkStep()
    }

    private fun walkStep() {
        if (walkStepsRemaining <= 0 || !isWalking) {
            stopWalking()
            return
        }
        walkStepsRemaining--
        params?.let {
            val newX = it.x + walkDirection * WALK_STEP_PX
            if (newX <= getLeftBoundary()) {
                walkDirection = 1
                overlayView?.evaluateJavascript(
                    "window.petEngine && window.petEngine.setState('walk_right')", null
                )
            } else if (newX >= getRightBoundary()) {
                walkDirection = -1
                overlayView?.evaluateJavascript(
                    "window.petEngine && window.petEngine.setState('walk_left')", null
                )
            }
            it.x = clampX(it.x + walkDirection * WALK_STEP_PX)
            try { windowManager?.updateViewLayout(overlayView, it) } catch (e: Exception) {}
        }
        handler.postDelayed({ walkStep() }, WALK_INTERVAL_MS)
    }

    private fun stopWalking() {
        isWalking = false
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.setState('idle')", null
        )
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
                overlayView?.evaluateJavascript(
                    "window.petEngine && window.petEngine.setState('peek_left')", null
                )
            } else {
                it.x = screenWidth - petSizePx
                overlayView?.evaluateJavascript(
                    "window.petEngine && window.petEngine.setState('peek_right')", null
                )
            }
            try { windowManager?.updateViewLayout(overlayView, it) } catch (e: Exception) {}
        }
    }

    private fun exitPeek() {
        isPeeking = false
        peekSide = 0
    }

    // === PHYSICS BOUNCE ===

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val ax = event.values[0]
        val ay = event.values[1]

        // Only trigger bounce when NOT dragging, NOT peeking
        if (isDragging || isPeeking) return

        val accelMag = Math.sqrt((ax * ax + ay * ay).toDouble()).toFloat()

        if (!isBouncing && accelMag > BOUNCE_TRIGGER_THRESHOLD) {
            // Enter bounce mode
            enterBounceMode(-ax * BOUNCE_ACCEL_SCALE, ay * BOUNCE_ACCEL_SCALE)
        } else if (isBouncing && !isBouncePaused) {
            // Continuously add acceleration force while bouncing
            bounceVelocityX += -ax * 0.5f
            bounceVelocityY += ay * 0.5f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun enterBounceMode(initialVx: Float, initialVy: Float) {
        if (isBouncing) return

        isBouncing = true
        isWalking = false
        isBouncePaused = false
        handler.removeCallbacksAndMessages(null)

        bounceVelocityX = initialVx
        bounceVelocityY = initialVy

        // Set initial animation direction
        updateBounceAnimation()
        scheduleBounceStep()
    }

    private fun scheduleBounceStep() {
        handler.postDelayed({ bounceStep() }, BOUNCE_UPDATE_MS)
    }

    private fun bounceStep() {
        if (!isBouncing || isBouncePaused) return

        // Apply friction
        bounceVelocityX *= BOUNCE_FRICTION
        bounceVelocityY *= BOUNCE_FRICTION

        val speed = Math.sqrt((bounceVelocityX * bounceVelocityX + bounceVelocityY * bounceVelocityY).toDouble()).toFloat()

        // Stop if speed too low
        if (speed < BOUNCE_STOP_SPEED) {
            exitBounceMode()
            return
        }

        params?.let {
            val newX = it.x + bounceVelocityX.toInt()
            val newY = it.y + bounceVelocityY.toInt()

            var hitWall = false

            // Check X boundaries
            if (newX <= getLeftBoundary()) {
                bounceVelocityX = Math.abs(bounceVelocityX) * BOUNCE_RESTITUTION
                it.x = getLeftBoundary()
                hitWall = true
            } else if (newX >= getRightBoundary()) {
                bounceVelocityX = -Math.abs(bounceVelocityX) * BOUNCE_RESTITUTION
                it.x = getRightBoundary()
                hitWall = true
            } else {
                it.x = newX
            }

            // Check Y boundaries
            if (newY <= getTopBoundary()) {
                bounceVelocityY = Math.abs(bounceVelocityY) * BOUNCE_RESTITUTION
                it.y = getTopBoundary()
                hitWall = true
            } else if (newY >= getBottomBoundary()) {
                bounceVelocityY = -Math.abs(bounceVelocityY) * BOUNCE_RESTITUTION
                it.y = getBottomBoundary()
                hitWall = true
            } else {
                it.y = newY
            }

            try { windowManager?.updateViewLayout(overlayView, it) } catch (e: Exception) {}

            if (hitWall) {
                // Pause at wall: show idle for 1 second, then continue
                isBouncePaused = true
                overlayView?.evaluateJavascript(
                    "window.petEngine && window.petEngine.setState('idle')", null
                )
                handler.postDelayed({
                    isBouncePaused = false
                    if (isBouncing) {
                        updateBounceAnimation()
                        scheduleBounceStep()
                    }
                }, BOUNCE_WALL_IDLE_MS)
                return
            } else {
                // Update direction animation
                updateBounceAnimation()
            }
        }

        scheduleBounceStep()
    }

    private fun updateBounceAnimation() {
        val dir = if (bounceVelocityX >= 0) "walk_right" else "walk_left"
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.setState('$dir')", null
        )
    }

    private fun exitBounceMode() {
        isBouncing = false
        isBouncePaused = false
        bounceVelocityX = 0f
        bounceVelocityY = 0f
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.setState('idle')", null
        )
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

        if (lastDragX == 0f) {
            lastDragX = currentX
            return
        }

        val dx = currentX - lastDragX
        lastDragX = currentX

        // Need meaningful movement
        if (Math.abs(dx) < 3f) return

        val currentDirection = if (dx > 0) 1 else -1

        if (lastDragDirection != 0 && currentDirection != lastDragDirection) {
            // Direction reversed
            val now = System.currentTimeMillis()

            if (directionChangeCount == 0) {
                firstDirectionChangeTime = now
            }

            // Check if within time window
            if (now - firstDirectionChangeTime > FORTUNE_WINDOW_MS) {
                // Reset, window expired
                directionChangeCount = 1
                firstDirectionChangeTime = now
            } else {
                directionChangeCount++
            }

            if (directionChangeCount >= FORTUNE_DIRECTION_CHANGES) {
                // Triggered!
                fortuneTriggered = true
                onFortuneTriggered()
            }
        }

        lastDragDirection = currentDirection
    }

    private fun onFortuneTriggered() {
        // Wait 1 second (animation placeholder), then show fortune
        handler.postDelayed({
            isDragging = false
            val card = deckManager.drawCard(decks)
            if (card != null) {
                showFortune(card)
            }
            overlayView?.evaluateJavascript(
                "window.petEngine && window.petEngine.setState('idle')", null
            )
        }, FORTUNE_DELAY_MS)
    }

    // === FORTUNE DISPLAY ===

    private fun showFortune(text: String) {
        if (fortuneView != null) return

        val padding = dpToPx(24)
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#2d2d2d"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#f5f5f5"))
                cornerRadius = dpToPx(16).toFloat()
            }
            background = bg
            elevation = 8f
        }
        tv.setOnClickListener {
            dismissFortune()
            scheduleWalk()
        }

        fortuneParams = WindowManager.LayoutParams(
            (screenWidth * 0.7).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        fortuneView = tv
        try { windowManager?.addView(tv, fortuneParams) } catch (e: Exception) {}
    }

    private fun dismissFortune() {
        fortuneView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {}
        }
        fortuneView = null
        fortuneParams = null
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
                    isBouncePaused = false
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
                        if (isPeeking) {
                            exitPeek()
                        }
                        params?.let {
                            it.x = initialX + dx
                            it.y = clampY(initialY + dy)
                            try { windowManager?.updateViewLayout(overlayView, it) } catch (e: Exception) {}
                        }
                        if (!dragNotified) {
                            dragNotified = true
                            isDragging = true
                            overlayView?.evaluateJavascript(
                                "window.petEngine && window.petEngine.onDragStart()", null
                            )
                        }
                        // Detect finger direction reversal for fortune
                        detectDirectionReversal(event.rawX)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchStartTime
                    if (!hasMoved) {
                        if (fortuneView != null) {
                            dismissFortune()
                            scheduleWalk()
                        } else {
                            when {
                                elapsed > 600 -> onLongPress()
                                System.currentTimeMillis() - lastTapTime < 300 -> onDoubleTap()
                                else -> {
                                    lastTapTime = System.currentTimeMillis()
                                    onTap()
                                }
                            }
                        }
                    } else {
                        isDragging = false
                        if (!fortuneTriggered) {
                            onDragEnd()
                        } else {
                            // Fortune was triggered during drag, just reset position
                            scheduleWalk()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onTap() {
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onTap()", null
        )
        if (isPeeking) return
        scheduleWalk()
    }

    private fun onDoubleTap() {
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onDoubleTap()", null
        )
        if (isPeeking) return
        scheduleWalk()
    }

    private fun onLongPress() {
        // Only play drag animation (clothesline feature)
        // Do NOT start any detection
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onLongPress()", null
        )
    }

    private fun onDragEnd() {
        val currentX = params?.x ?: 0
        val peekThresholdPx = dpToPx(PEEK_BODY_OUT_DP)
        val gifLeftPad = dpToPx(GIF_PAD_LEFT_DP)
        val gifRightPad = dpToPx(GIF_PAD_RIGHT_DP)

        val gifLeftEdge = currentX + gifLeftPad
        val gifRightEdge = currentX + petSizePx - gifRightPad

        if (gifLeftEdge < -peekThresholdPx) {
            enterPeek(-1)
            return
        } else if (gifRightEdge > screenWidth + peekThresholdPx) {
            enterPeek(1)
            return
        }

        // Clamp to boundaries
        params?.let {
            it.x = clampX(it.x)
            it.y = clampY(it.y)
            try { windowManager?.updateViewLayout(overlayView, it) } catch (e: Exception) {}
        }

        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onDragEnd()", null
        )
        scheduleWalk()
    }

    // === NOTIFICATION ===

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, StopReceiver::class.java)
        val stopPending = PendingIntent.getBroadcast(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("\uD83E\uDD80 Clawd")
            .setContentText("在你身边溜达中")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", stopPending)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clawd 桌宠",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // === UTIL ===

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterSensor()
        dismissFortune()
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}
