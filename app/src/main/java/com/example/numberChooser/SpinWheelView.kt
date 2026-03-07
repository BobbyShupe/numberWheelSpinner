package com.example.numberChooser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

class SpinWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val TAG = "SpinWheelView"

    private var prefs: SharedPreferences? = null

    private var segmentCount = 12
    private val anglePerSegment: Float get() = 360f / segmentCount

    private var rotation = 0f
    private var angularVelocity = 0f

    private var velocityTracker: VelocityTracker? = null
    private var previousAngle = 0f
    private var gestureStartTime = 0L
    private var totalAngleDelta = 0f

    private val minVelocityToStartFling = 30f
    private val velocityScale = 1.8f
    private val stopVelocity = 2.5f

    private var isSpinning = false

    private var resultListener: ((Int) -> Unit)? = null
    private var currentNumberListener: ((Int) -> Unit)? = null

    private val history = mutableListOf<Int>()

    // Sound
    private var soundPool: SoundPool? = null
    private var tickSoundId: Int = 0

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(6)           // allows up to 6 overlapping ticks
            .setAudioAttributes(audioAttributes)
            .build()

        try {
            tickSoundId = soundPool?.load(context, R.raw.tick, 1) ?: 0
            if (tickSoundId == 0) {
                Log.w(TAG, "Failed to load tick sound - ID is 0")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading tick sound into SoundPool", e)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        soundPool?.release()
        soundPool = null
    }

    fun attachPreferences(sharedPreferences: SharedPreferences) {
        prefs = sharedPreferences

        segmentCount = prefs?.getInt("segmentCount", segmentCount) ?: segmentCount
        rotation = prefs?.getFloat("wheelRotation", 0f) ?: 0f

        val historyStr = prefs?.getString("history", "") ?: ""
        if (historyStr.isNotEmpty()) {
            history.clear()
            historyStr.split(",").forEach { token ->
                token.trim().toIntOrNull()?.let { history.add(it) }
            }
        }

        invalidate()
    }

    fun setSegmentCount(count: Int) {
        if (count < 2) return
        segmentCount = count
        prefs?.edit()?.putInt("segmentCount", count)?.apply()
        invalidate()
    }

    fun clearHistory() {
        history.clear()
        prefs?.edit()?.remove("history")?.apply()
        invalidate()
    }

    fun setOnResultListener(listener: (Int) -> Unit) {
        resultListener = listener
    }

    fun setOnCurrentNumberListener(listener: (Int) -> Unit) {
        currentNumberListener = listener
    }

    fun getHistory(): String = history.joinToString(", ")

    private fun addToHistory(number: Int) {
        history.add(number)
        prefs?.edit()?.apply {
            putString("history", history.joinToString(","))
            putFloat("wheelRotation", rotation)
            apply()
        }
    }

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFF00.toInt()
        alpha = 180
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private fun getCurrentSegmentIndex(): Int {
        val pointerPosition = 270f
        val normalized = (rotation + 360f) % 360f
        val angleFromPointer = (pointerPosition - normalized + 360f) % 360f
        return floor(angleFromPointer / anglePerSegment).toInt() % segmentCount
    }

    private fun getCurrentNumberUnderPointer(): Int = getCurrentSegmentIndex() + 1

    private fun checkAndPlayTick() {
        val currentSegment = getCurrentSegmentIndex()
        if (currentSegment != lastPlayedSegment) {
            lastPlayedSegment = currentSegment
            playTick()
        }
    }

    private var lastPlayedSegment = -1

    private fun playTick() {
        soundPool?.let { pool ->
            if (tickSoundId > 0) {
                // volume left/right, priority, loop, rate
                pool.play(tickSoundId, 0.7f, 0.7f, 1, 0, 1.0f)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val dx = event.x - cx
        val dy = event.y - cy

        var currentAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (currentAngle < 0) currentAngle += 360f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isSpinning = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)

                previousAngle = currentAngle
                totalAngleDelta = 0f
                gestureStartTime = event.eventTime
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val prevRad = Math.toRadians(previousAngle.toDouble())
                val currRad = Math.toRadians(currentAngle.toDouble())

                val prevX = cos(prevRad)
                val prevY = sin(prevRad)
                val currX = cos(currRad)
                val currY = sin(currRad)

                val cross = (prevX * currY - prevY * currX).toFloat()
                val dot = (prevX * currX + prevY * currY).toFloat()
                val delta = Math.toDegrees(atan2(cross.toDouble(), dot.toDouble())).toFloat()

                rotation += delta
                totalAngleDelta += delta
                previousAngle = currentAngle

                checkAndPlayTick()
                currentNumberListener?.invoke(getCurrentNumberUnderPointer())
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val durationMs = (event.eventTime - gestureStartTime).coerceAtLeast(1L)
                val absTotalDelta = abs(totalAngleDelta)

                var computedVel = (absTotalDelta / (durationMs / 1000f)) * velocityScale
                if (totalAngleDelta < 0) computedVel = -computedVel

                angularVelocity = computedVel
                if (abs(angularVelocity) < 60f && absTotalDelta > 15f) {
                    angularVelocity = 60f * sign(angularVelocity.coerceAtLeast(0.001f))
                }

                velocityTracker?.recycle()
                velocityTracker = null

                if (abs(angularVelocity) > minVelocityToStartFling) {
                    startFling()
                } else {
                    stopAndReport()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startFling() {
        if (isSpinning) return
        isSpinning = true

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 12000L
        animator.interpolator = LinearInterpolator()
        animator.repeatCount = ValueAnimator.INFINITE

        var lastTime = System.nanoTime()

        animator.addUpdateListener {
            if (!isSpinning) {
                animator.cancel()
                return@addUpdateListener
            }

            val now = System.nanoTime()
            val deltaTimeSec = (now - lastTime) / 1_000_000_000.0
            lastTime = now

            val deltaAngle = angularVelocity * deltaTimeSec.toFloat()
            rotation += deltaAngle
            rotation = (rotation + 360f) % 360f

            val speed = abs(angularVelocity)
            val friction = when {
                speed > 720f -> 0.993f
                speed > 360f -> 0.994f
                speed > 180f -> 0.995f
                speed > 90f  -> 0.996f
                else         -> 0.997f
            }

            angularVelocity *= friction.toDouble().pow(deltaTimeSec * 60.0).toFloat()

            checkAndPlayTick()
            currentNumberListener?.invoke(getCurrentNumberUnderPointer())

            if (abs(angularVelocity) < stopVelocity) {
                isSpinning = false
                animator.cancel()
                stopAndReport()
            }

            invalidate()
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isSpinning = false
            }
        })

        animator.start()
    }

    private fun stopAndReport() {
        rotation = (rotation + 360f) % 360f
        val winningNumber = getCurrentNumberUnderPointer()
        addToHistory(winningNumber)
        resultListener?.invoke(winningNumber)
        prefs?.edit()?.putFloat("wheelRotation", rotation)?.apply()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.88f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        val pointerAngle = 270f
        val normalizedRotation = (rotation + 360f) % 360f
        val highlightedSegment = floor(
            ((pointerAngle - normalizedRotation + 360f) % 360f) / anglePerSegment
        ).toInt() % segmentCount

        for (i in 0 until segmentCount) {
            val isHighlighted = i == highlightedSegment

            wheelPaint.color = when {
                isHighlighted -> 0xFFFFFF55.toInt()
                i % 2 == 0    -> 0xFFFF0000.toInt()
                else          -> 0xFFFFFFFF.toInt()
            }

            canvas.drawArc(oval, i * anglePerSegment + rotation, anglePerSegment, true, wheelPaint)

            if (isHighlighted) {
                canvas.drawArc(oval, i * anglePerSegment + rotation, anglePerSegment, true, highlightPaint)
            }
        }

        borderPaint.strokeWidth = 4f
        for (i in 0 until segmentCount) {
            val angleRad = Math.toRadians((i * anglePerSegment + rotation).toDouble())
            val x1 = cx + cos(angleRad) * radius
            val y1 = cy + sin(angleRad) * radius
            val x2 = cx + cos(angleRad) * radius * 0.92f
            val y2 = cy + sin(angleRad) * radius * 0.92f
            canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), borderPaint)
        }

        val dynamicTextSize = (radius * 0.22f) / sqrt(segmentCount.toFloat())
        textPaint.textSize = dynamicTextSize

        for (i in 0 until segmentCount) {
            val midAngle = i * anglePerSegment + rotation + anglePerSegment / 2
            val rad = Math.toRadians(midAngle.toDouble())
            val tx = cx + cos(rad) * radius * 0.72
            val ty = cy + sin(rad) * radius * 0.72 + textPaint.textSize * 0.35f
            canvas.drawText((i + 1).toString(), tx.toFloat(), ty.toFloat(), textPaint)
        }

        wheelPaint.color = 0xFFFF0000.toInt()
        canvas.drawCircle(cx, cy, radius * 0.033f, wheelPaint)

        // Pointer triangle
        val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply {
            moveTo(cx, cy - radius + 70f - 40f)
            lineTo(cx - 28f, cy - radius - 40f)
            lineTo(cx + 28f, cy - radius - 40f)
            close()
        }

        pointerPaint.color = Color.WHITE
        canvas.drawPath(path, pointerPaint)

        pointerPaint.color = Color.BLUE
        pointerPaint.style = Paint.Style.STROKE
        pointerPaint.strokeWidth = 7f
        canvas.drawPath(path, pointerPaint)
    }
}