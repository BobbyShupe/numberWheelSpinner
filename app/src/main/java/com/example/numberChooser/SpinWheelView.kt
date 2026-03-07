package com.example.numberChooser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
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

    private val TAG = "SpinWheel"

    private val SEGMENTS = 12
    private val ANGLE_PER_SEGMENT = 360f / SEGMENTS

    private var rotation = 0f
    private var angularVelocity = 0f

    private var velocityTracker: VelocityTracker? = null
    private var previousAngle = 0f
    private var gestureStartTime = 0L
    private var totalAngleDelta = 0f

    // Tuning
    private val friction = 0.985f
    private val minVelocityToStartFling = 30f
    private val velocityScale = 1.8f
    private val stopVelocity = 2.5f
    private var isSpinning = false

    private var resultListener: ((Int) -> Unit)? = null
    private var currentNumberListener: ((Int) -> Unit)? = null   // for live preview
    private val history = mutableListOf<Int>()

    fun setOnResultListener(listener: (Int) -> Unit) {
        resultListener = listener
    }

    fun setOnCurrentNumberListener(listener: (Int) -> Unit) {
        currentNumberListener = listener
    }

    fun getHistory(): String = history.joinToString(", ")

    fun addToHistory(number: Int) {
        history.add(number)
    }

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF4444FF.toInt() // blue-ish highlight
        alpha = 180
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF444444.toInt()
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    // Returns the number currently under the pointer (live)
    private fun getCurrentNumberUnderPointer(): Int {
        val pointerPosition = 270f   // top = 270° in android canvas
        val normalized = (rotation + 360f) % 360f
        val angleFromPointer = (pointerPosition - normalized + 360f) % 360f
        val segmentIndex = floor(angleFromPointer / ANGLE_PER_SEGMENT).toInt() % SEGMENTS
        return (segmentIndex + 1)
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

                // Live preview during drag
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

        animator.addUpdateListener { _ ->
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
            val dynamicFriction = when {
                speed > 720f -> 0.993f
                speed > 360f -> 0.994f
                speed > 180f -> 0.995f
                speed > 90f  -> 0.996f
                else         -> 0.997f
            }

            angularVelocity *= dynamicFriction.toDouble().pow(deltaTimeSec * 60.0).toFloat()

            // Live update during spin
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
                stopAndReport()
            }
        })

        animator.start()
    }

    private fun stopAndReport() {
        rotation = (rotation + 360f) % 360f
        val winningNumber = getCurrentNumberUnderPointer()
        addToHistory(winningNumber)
        invalidate()
        resultListener?.invoke(winningNumber)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.88f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        val pointerAngle = 270f
        val normalizedRotation = (rotation + 360f) % 360f
        val highlightedSegment = floor(((pointerAngle - normalizedRotation + 360f) % 360f) / ANGLE_PER_SEGMENT).toInt() % SEGMENTS

        // Draw segments + highlight
        for (i in 0 until SEGMENTS) {
            val isHighlighted = (i == highlightedSegment)
            wheelPaint.color = when {
                isHighlighted -> 0xFF5555FF.toInt() // brighter blue
                i % 2 == 0    -> 0xFF1A1A1A.toInt()
                else          -> 0xFF252525.toInt()
            }
            canvas.drawArc(oval, i * ANGLE_PER_SEGMENT + rotation, ANGLE_PER_SEGMENT, true, wheelPaint)

            // subtle overlay highlight
            if (isHighlighted) {
                canvas.drawArc(oval, i * ANGLE_PER_SEGMENT + rotation, ANGLE_PER_SEGMENT, true, highlightPaint)
            }
        }

        // Dividers
        borderPaint.strokeWidth = 4f
        for (i in 0 until SEGMENTS) {
            val angleRad = Math.toRadians((i * ANGLE_PER_SEGMENT + rotation).toDouble())
            val x1 = cx + cos(angleRad) * radius
            val y1 = cy + sin(angleRad) * radius
            val x2 = cx + cos(angleRad) * radius * 0.92f   // shorter lines
            val y2 = cy + sin(angleRad) * radius * 0.92f
            canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), borderPaint)
        }

        // Numbers
        for (i in 0 until SEGMENTS) {
            val midAngle = i * ANGLE_PER_SEGMENT + rotation + ANGLE_PER_SEGMENT / 2
            val rad = Math.toRadians(midAngle.toDouble())
            val tx = cx + cos(rad) * radius * 0.70
            val ty = cy + sin(rad) * radius * 0.70 + textPaint.textSize * 0.35f
            canvas.drawText((i + 1).toString(), tx.toFloat(), ty.toFloat(), textPaint)
        }

        // Center circle
        wheelPaint.color = 0xFF222222.toInt()
        canvas.drawCircle(cx, cy, radius * 0.16f, wheelPaint)

        // Pointer TRIANGLE - now ABOVE the wheel
        val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply {
            moveTo(cx, cy - radius + 70f - 40f)           // ← original starting point (tip slightly inside)
            lineTo(cx - 28f, cy - radius - 40f)     // original base points
            lineTo(cx + 28f, cy - radius - 40f)
            close()
        }

        pointerPaint.color = Color.WHITE
        canvas.drawPath(path, pointerPaint)

        pointerPaint.color = Color.BLACK
        pointerPaint.style = Paint.Style.STROKE
        pointerPaint.strokeWidth = 7f
        canvas.drawPath(path, pointerPaint)
    }
}