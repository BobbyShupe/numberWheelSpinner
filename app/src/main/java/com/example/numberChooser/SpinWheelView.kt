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
    private var angularVelocity = 0f            // degrees per second

    private var velocityTracker: VelocityTracker? = null
    private var previousAngle = 0f
    private var gestureStartTime = 0L
    private var totalAngleDelta = 0f

    // Tuning
    private val friction = 0.985f            // was 0.965  (slower decay)
    private val minVelocityToStartFling = 30f
    private val velocityScale = 1.8f
    private val stopVelocity = 2.5f          // new: very slow final stop
    private var isSpinning = false

    private var resultListener: ((Int) -> Unit)? = null

    fun setOnResultListener(listener: (Int) -> Unit) {
        resultListener = listener
    }

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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

// cross product gives correct rotational direction
                val cross = (prevX * currY - prevY * currX).toFloat()

// dot product gives angle magnitude
                val dot = (prevX * currX + prevY * currY).toFloat()
                val delta = Math.toDegrees(atan2(cross.toDouble(), dot.toDouble())).toFloat()

                rotation += delta
                totalAngleDelta += delta

                previousAngle = currentAngle
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

                Log.d(TAG, "RELEASE | totalΔ=%.1f° | dur=%dms | vel=%.1f °/s | fling? ${abs(angularVelocity) > minVelocityToStartFling}".format(
                    totalAngleDelta, durationMs, angularVelocity
                ))

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

        Log.d(TAG, "FLING START | initial vel = ${"%.1f".format(angularVelocity)} °/s")

        val animator = ValueAnimator.ofFloat(0f, 1f)   // just a ticker
        animator.duration = 12000L                      // max duration
        animator.interpolator = LinearInterpolator()
        animator.repeatCount = ValueAnimator.INFINITE   // we cancel manually

        var lastTime = System.nanoTime()

        animator.addUpdateListener { _ ->
            if (!isSpinning) {
                animator.cancel()
                return@addUpdateListener
            }

            val now = System.nanoTime()
            val deltaTimeSec = (now - lastTime) / 1_000_000_000.0
            lastTime = now

            // Time-corrected rotation update
            val deltaAngle = angularVelocity * deltaTimeSec.toFloat()
            rotation += deltaAngle
            rotation = (rotation + 360f) % 360f

// Progressive friction (casino style)
// strong slowdown when fast, gentle when slow
            val speed = abs(angularVelocity)

            val dynamicFriction = when {
                speed > 720f -> 0.993f
                speed > 360f -> 0.994f
                speed > 180f -> 0.995f
                speed > 90f  -> 0.996f
                else         -> 0.997f   // very slow coast at the end
            }

            angularVelocity *= dynamicFriction.toDouble().pow(deltaTimeSec * 60.0).toFloat()
            Log.d(TAG, "FLING FRAME | vel=%.1f °/s | deltaAngle=%.2f° | rot=%.1f°".format(
                angularVelocity, deltaAngle, rotation
            ))

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
                Log.d(TAG, "FLING END | final rot = ${"%.1f".format(rotation)}°")
            }
        })

        animator.start()
    }

    private fun stopAndReport() {
        rotation = (rotation + 360f) % 360f
        invalidate()
        reportResult()
    }

    private fun reportResult() {
        val pointerPosition = 270f
        val normalized = (rotation + 360f) % 360f
        val angleFromPointer = (pointerPosition - normalized + 360f) % 360f
        val segmentIndex = floor(angleFromPointer / ANGLE_PER_SEGMENT).toInt() % SEGMENTS
        val winningNumber = (segmentIndex + 1)

        Log.d(TAG, "RESULT → number: $winningNumber   segment: $segmentIndex   rotation: ${"%.1f".format(rotation)}°")
        resultListener?.invoke(winningNumber)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.88f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        for (i in 0 until SEGMENTS) {
            wheelPaint.color = if (i % 2 == 0) 0xFF1A1A1A.toInt() else 0xFF252525.toInt()
            canvas.drawArc(oval, i * ANGLE_PER_SEGMENT + rotation, ANGLE_PER_SEGMENT, true, wheelPaint)
        }

        borderPaint.strokeWidth = 4f
        for (i in 0 until SEGMENTS) {
            val angleRad = Math.toRadians((i * ANGLE_PER_SEGMENT + rotation).toDouble())
            val x1 = cx + cos(angleRad) * radius
            val y1 = cy + sin(angleRad) * radius
            val x2 = cx + cos(angleRad) * radius * 0.08f
            val y2 = cy + sin(angleRad) * radius * 0.08f
            canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), borderPaint)
        }

        for (i in 0 until SEGMENTS) {
            val midAngle = i * ANGLE_PER_SEGMENT + rotation + ANGLE_PER_SEGMENT / 2
            val rad = Math.toRadians(midAngle.toDouble())
            val tx = cx + cos(rad) * radius * 0.70
            val ty = cy + sin(rad) * radius * 0.70 + textPaint.textSize * 0.35f
            canvas.drawText((i + 1).toString(), tx.toFloat(), ty.toFloat(), textPaint)
        }

        wheelPaint.color = 0xFF222222.toInt()
        canvas.drawCircle(cx, cy, radius * 0.16f, wheelPaint)

        val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply {
            moveTo(cx, cy - radius + 80f)
            lineTo(cx - 48f, cy - radius + 10f)
            lineTo(cx + 48f, cy - radius + 10f)
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