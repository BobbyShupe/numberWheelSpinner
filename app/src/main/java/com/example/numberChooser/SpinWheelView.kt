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

    private val segments = 12
    private val anglePerSegment = 360f / segments

    private var rotation = 0f
    private var angularVelocity = 0f

    private var velocityTracker: VelocityTracker? = null
    private var lastAngle = 0f

    private val decay = 0.992f      // gradual slowdown
    private val stopThreshold = 0.02f

    private var spinning = false

    private var resultListener: ((Int) -> Unit)? = null

    fun setOnResultListener(listener: (Int) -> Unit) {
        resultListener = listener
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF555555.toInt()
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        val cx = width / 2f
        val cy = height / 2f

        val dx = event.x - cx
        val dy = event.y - cy

        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                spinning = false

                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker!!.addMovement(event)

                lastAngle = angle

                parent.requestDisallowInterceptTouchEvent(true)

                return true
            }

            MotionEvent.ACTION_MOVE -> {

                velocityTracker?.addMovement(event)

                var delta = angle - lastAngle

                if (delta > 180) delta -= 360f
                if (delta < -180) delta += 360f

                rotation += delta

                angularVelocity = delta

                lastAngle = angle

                invalidate()

                return true
            }

            MotionEvent.ACTION_UP -> {

                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)

                val vx = velocityTracker?.xVelocity ?: 0f
                val vy = velocityTracker?.yVelocity ?: 0f

                val linearSpeed = sqrt(vx * vx + vy * vy)

                angularVelocity = linearSpeed / 35f

                startSpin()

                velocityTracker?.recycle()
                velocityTracker = null

                return true
            }
        }

        return true
    }

    private fun startSpin() {

        spinning = true

        post(object : Runnable {

            override fun run() {

                if (!spinning) return

                rotation += angularVelocity

                angularVelocity *= decay

                if (abs(angularVelocity) < stopThreshold) {

                    spinning = false
                    reportResult()
                    invalidate()
                    return
                }

                invalidate()
                postOnAnimation(this)
            }
        })
    }

    private fun reportResult() {

        val normalized = ((rotation % 360f) + 360f) % 360f

        val pointerAngle = 180f

        val relative = (pointerAngle - normalized + 360f) % 360f

        val index = floor(relative / anglePerSegment).toInt()

        val result = index + 1

        Log.d(TAG, "Result = $result")

        resultListener?.invoke(result)
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f

        val radius = min(width, height) / 2f * 0.88f

        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        for (i in 0 until segments) {

            paint.color =
                if (i % 2 == 0) 0xFF1E1E1E.toInt()
                else 0xFF2A2A2A.toInt()

            canvas.drawArc(
                oval,
                i * anglePerSegment + rotation,
                anglePerSegment,
                true,
                paint
            )
        }

        for (i in 0 until segments) {

            val a = (i * anglePerSegment + rotation) * PI / 180

            val x1 = cx + cos(a) * radius
            val y1 = cy + sin(a) * radius

            val x2 = cx + cos(a) * radius * 0.12
            val y2 = cy + sin(a) * radius * 0.12

            canvas.drawLine(
                x1.toFloat(),
                y1.toFloat(),
                x2.toFloat(),
                y2.toFloat(),
                borderPaint
            )
        }

        for (i in 0 until segments) {

            val mid = i * anglePerSegment + rotation + anglePerSegment / 2
            val r = mid * PI / 180

            val tx = cx + cos(r) * radius * 0.68
            val ty = cy + sin(r) * radius * 0.68 + textPaint.textSize / 3

            canvas.drawText(
                (i + 1).toString(),
                tx.toFloat(),
                ty.toFloat(),
                textPaint
            )
        }

        paint.color = 0xFF2C2C2C.toInt()
        canvas.drawCircle(cx, cy, radius * 0.14f, paint)

        val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        pointerPaint.color = Color.WHITE

        val path = Path()

        path.moveTo(cx, cy + radius - 50f)
        path.lineTo(cx - 44f, cy + radius + 30f)
        path.lineTo(cx + 44f, cy + radius + 30f)
        path.close()

        canvas.drawPath(path, pointerPaint)

        pointerPaint.color = Color.BLACK
        pointerPaint.style = Paint.Style.STROKE
        pointerPaint.strokeWidth = 7f

        canvas.drawPath(path, pointerPaint)
    }
}