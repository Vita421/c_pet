package com.clawd.pet

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    // Screen dimensions (pixels)
    private var screenWidth = 0
    private var screenHeight = 0
    private var petSizePx = 0

    // Auto-walk
    private val handler = Handler(Looper.getMainLooper())
    private var isWalking = false
    private var walkDirection = 1 // 1=right, -1=left
    private var walkStepsRemaining = 0

    // Peek state
    private var isPeeking = false
    private var peekSide = 0 // -1=left, 1=right

    companion object {
        private const val CHANNEL_ID = "clawd_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PET_SIZE_DP = 120
        private const val WALK_STEP_PX = 2
        private const val WALK_INTERVAL_MS = 50L
        // gif is 64px centered in 120dp window → 28dp padding each side
        private const val GIF_PADDING_DP = 28
        // gif edge must be within 5dp of screen edge to trigger peek
        private const val PEEK_TRIGGER_DP = 5
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Get screen dimensions
        val dm = resources.displayMetrics
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
        petSizePx = dpToPx(PET_SIZE_DP)

        setupOverlay()
        scheduleWalk()
    }

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

    // === SCREEN BOUNDARY HELPERS ===
    // Boundaries are calculated so gif edge touches screen edge, not window edge

    private fun getLeftBoundary(): Int = -dpToPx(GIF_PADDING_DP)
    private fun getRightBoundary(): Int = screenWidth - petSizePx + dpToPx(GIF_PADDING_DP)
    private fun getTopBoundary(): Int = -dpToPx(GIF_PADDING_DP)
    private fun getBottomBoundary(): Int = screenHeight - petSizePx + dpToPx(GIF_PADDING_DP)

    private fun clampY(y: Int): Int {
        return y.coerceIn(getTopBoundary(), getBottomBoundary())
    }

    // === AUTO WALK WITH BOUNDARY DETECTION ===

    private fun scheduleWalk() {
        if (isPeeking) return // Don't walk while peeking
        val delay = 3000L + (Math.random() * 4000).toLong()
        handler.postDelayed({
            if (!isWalking && !isPeeking) {
                startWalking()
            }
        }, delay)
    }

    private fun startWalking() {
        isWalking = true
        // Random direction
        walkDirection = if (Math.random() > 0.5) 1 else -1

        // Check if we'd walk into a wall immediately - if so, go the other way
        params?.let {
            if (walkDirection == -1 && it.x <= getLeftBoundary()) {
                walkDirection = 1
            } else if (walkDirection == 1 && it.x >= getRightBoundary()) {
                walkDirection = -1
            }
        }

        // Random steps (40-80 steps = 2-4 seconds at 50ms interval)
        walkStepsRemaining = 40 + (Math.random() * 40).toInt()

        // Tell JS to show walk animation
        val dir = if (walkDirection == 1) "walk_right" else "walk_left"
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.setState('$dir')", null
        )

        // Start stepping
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

            // Boundary check: if hitting edge, reverse direction
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

            it.x = (it.x + walkDirection * WALK_STEP_PX).coerceIn(getLeftBoundary(), getRightBoundary())
            try {
                windowManager?.updateViewLayout(overlayView, it)
            } catch (e: Exception) { /* view might be removed */ }
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

    // === PEEK (EDGE CLING) ===

    private fun enterPeek(side: Int) {
        isPeeking = true
        peekSide = side
        isWalking = false
        handler.removeCallbacksAndMessages(null)

        params?.let {
            if (side == -1) {
                // Left peek: window flush to left edge, sprite drawn at left of canvas
                it.x = 0
                overlayView?.evaluateJavascript(
                    "window.petEngine && window.petEngine.setState('peek_left')", null
                )
            } else {
                // Right peek: window flush to right edge, sprite drawn at right of canvas
                it.x = screenWidth - petSizePx
                overlayView?.evaluateJavascript(
                    "window.petEngine && window.petEngine.setState('peek_right')", null
                )
            }
            try {
                windowManager?.updateViewLayout(overlayView, it)
            } catch (e: Exception) {}
        }
    }

    private fun exitPeek() {
        isPeeking = false
        peekSide = 0
    }

    // === GESTURE HANDLING ===

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
                    // Fix ghost sliding: kill ALL pending handler callbacks
                    handler.removeCallbacksAndMessages(null)
                    isWalking = false
                    walkStepsRemaining = 0

                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    dragNotified = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        hasMoved = true

                        // If was peeking, exit peek on drag
                        if (isPeeking) {
                            exitPeek()
                        }

                        params?.let {
                            it.x = initialX + dx
                            // Clamp Y: never go above or below screen
                            it.y = clampY(initialY + dy)
                            windowManager?.updateViewLayout(overlayView, it)
                        }

                        // Notify JS only once when drag starts
                        if (!dragNotified) {
                            dragNotified = true
                            overlayView?.evaluateJavascript(
                                "window.petEngine && window.petEngine.onDragStart()", null
                            )
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchStartTime

                    if (!hasMoved) {
                        when {
                            elapsed > 600 -> onLongPress()
                            System.currentTimeMillis() - lastTapTime < 300 -> onDoubleTap()
                            else -> {
                                lastTapTime = System.currentTimeMillis()
                                onTap()
                            }
                        }
                    } else {
                        onDragEnd()
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
        // If was peeking and just tapped (no move), stay peeking
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
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onLongPress()", null
        )
    }

    private fun onDragEnd() {
        val currentX = params?.x ?: 0
        val gifPaddingPx = dpToPx(GIF_PADDING_DP)
        val peekTriggerPx = dpToPx(PEEK_TRIGGER_DP)

        // gif left edge = currentX + gifPaddingPx
        // gif right edge = currentX + petSizePx - gifPaddingPx
        val gifLeftEdge = currentX + gifPaddingPx
        val gifRightEdge = currentX + petSizePx - gifPaddingPx

        // Peek triggers when gif edge is within 5dp of screen edge
        if (gifLeftEdge < peekTriggerPx) {
            enterPeek(-1)
            return
        } else if (screenWidth - gifRightEdge < peekTriggerPx) {
            enterPeek(1)
            return
        }

        // Not at edge: clamp to safe area and go idle
        params?.let {
            it.x = it.x.coerceIn(getLeftBoundary(), getRightBoundary())
            it.y = clampY(it.y)
            try {
                windowManager?.updateViewLayout(overlayView, it)
            } catch (e: Exception) {}
        }

        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onDragEnd()", null
        )
        scheduleWalk()
    }

    // === NOTIFICATION with stop action ===

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

    // === UTILS ===

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}
