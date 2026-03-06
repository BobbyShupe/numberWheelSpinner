package com.example.numberChooser

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import kotlin.math.*

class SpinWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val TAG = "SpinWheel"

    private val SEGMENTS = 12
    private val ANGLE_PER_SEGMENT = 360f / SEGMENTS

    private var rotation = 0f           // current wheel rotation in degrees
    private var angularVelocity = 0f    // degrees per frame

    private var velocityTracker: VelocityTracker? = null
    private var previousAngle = 0f

    // Physics - smoother, longer spin (less abrupt stop)
    private val friction = 0.988f          // higher = slower decay, more spin time
    private val minVelocityToStop = 0.12f  // lower threshold = stops more gently
    private val velocityScale = 0.058f     // slightly softer initial fling

    private var isSpinning = false

    private var resultListener: ((Int) -> Unit)? = null

    fun setOnResultListener(listener: (Int) -> Unit) {
        resultListener = listener
    }

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
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
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)

                var delta = currentAngle - previousAngle
                delta = (delta + 180f) % 360f - 180f

                rotation += delta
                angularVelocity = delta

                previousAngle = currentAngle
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)

                val vx = velocityTracker?.xVelocity ?: 0f
                val vy = velocityTracker?.yVelocity ?: 0f

                val rx = event.x - cx
                val ry = event.y - cy

                val tangentialVelocity = (vx * -ry + vy * rx) / (rx * rx + ry * ry)

                angularVelocity = tangentialVelocity * 1800f

                velocityTracker?.recycle()
                velocityTracker = null

                if (abs(angularVelocity) > minVelocityToStop) {
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
        isSpinning = true
        post(object : Runnable {
            override fun run() {
                if (!isSpinning) return

                rotation = (rotation + angularVelocity) % 360f
                angularVelocity *= friction

                if (abs(angularVelocity) < minVelocityToStop) {
                    isSpinning = false
                    stopAndReport()
                    return
                }

                invalidate()
                postOnAnimation(this)
            }
        })
    }

    private fun stopAndReport() {
        rotation = ((rotation % 360f) + 360f) % 360f
        invalidate()
        reportResult()
    }

    private fun reportResult() {
        // Pointer is at TOP (0 degrees in mathematical convention)
        val pointerPosition = 270f - ANGLE_PER_SEGMENT / 2f

        val normalized = ((rotation % 360f) + 360f) % 360f
        // Angle from pointer to segment start (clockwise)
        val angleFromPointer = (pointerPosition - normalized + 360f) % 360f

        val segmentIndex = ((angleFromPointer / ANGLE_PER_SEGMENT).roundToInt()) % SEGMENTS
        val winningNumber = (segmentIndex + 1)  // 1..12

        val midAngleOfSegment = (segmentIndex * ANGLE_PER_SEGMENT + ANGLE_PER_SEGMENT / 2 + rotation) % 360f
        val distanceToCenter = (angleFromPointer % ANGLE_PER_SEGMENT - ANGLE_PER_SEGMENT / 2).absoluteValue

        Log.d(TAG, "Result → number: $winningNumber | segment: $segmentIndex | rot: ${"%.1f".format(rotation)}° | pointer→mid: ${"%.1f".format(distanceToCenter)}° away")

        resultListener?.invoke(winningNumber)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.88f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // Segments
        for (i in 0 until SEGMENTS) {
            wheelPaint.color = if (i % 2 == 0) 0xFF1A1A1A.toInt() else 0xFF252525.toInt()
            canvas.drawArc(oval, i * ANGLE_PER_SEGMENT + rotation, ANGLE_PER_SEGMENT, true, wheelPaint)
        }

        // Spokes
        borderPaint.strokeWidth = 4f
        for (i in 0 until SEGMENTS) {
            val angleRad = Math.toRadians((i * ANGLE_PER_SEGMENT + rotation).toDouble())
            val x1 = cx + cos(angleRad) * radius
            val y1 = cy + sin(angleRad) * radius
            val x2 = cx + cos(angleRad) * radius * 0.08f
            val y2 = cy + sin(angleRad) * radius * 0.08f
            canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), borderPaint)
        }

        // Numbers (drawn at segment midpoints)
        for (i in 0 until SEGMENTS) {
            val midAngle = i * ANGLE_PER_SEGMENT + rotation + ANGLE_PER_SEGMENT / 2
            val rad = Math.toRadians(midAngle.toDouble())
            val tx = cx + cos(rad) * radius * 0.70
            val ty = cy + sin(rad) * radius * 0.70 + textPaint.textSize * 0.35f
            canvas.drawText((i + 1).toString(), tx.toFloat(), ty.toFloat(), textPaint)
        }

        // Center hub
        wheelPaint.color = 0xFF222222.toInt()
        canvas.drawCircle(cx, cy, radius * 0.16f, wheelPaint)

        // Pointer TRIANGLE AT TOP, pointing DOWN (tip towards center)
        val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val path = Path().apply {
            moveTo(cx, cy - radius + 80f)          // tip (pointing down toward center)
            lineTo(cx - 48f, cy - radius + 10f)    // left base (higher up)
            lineTo(cx + 48f, cy - radius + 10f)    // right base
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