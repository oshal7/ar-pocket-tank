package com.pockettanks.ar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Tactile half-circle "protractor" dial. Drag anywhere on the arc to set the
 * turret elevation angle (0..180 degrees), per the PRD's bottom-left control.
 */
class RadialDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var angleDeg: Float = 45f
        private set

    var onAngleChanged: ((Float) -> Unit)? = null

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00F3FF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF0055")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A4A55")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private fun pivotX() = width / 2f
    private fun pivotY() = height.toFloat() - 8f
    private fun radius() = min(width / 2f, height.toFloat()) - 12f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = pivotX()
        val cy = pivotY()
        val r = radius()

        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 180f, 180f, false, arcPaint)

        var tick = 0
        while (tick <= 180) {
            val rad = Math.toRadians(tick.toDouble())
            val inner = r - 10f
            val x0 = cx + (inner * cos(rad)).toFloat()
            val y0 = cy - (inner * sin(rad)).toFloat()
            val x1 = cx + (r * cos(rad)).toFloat()
            val y1 = cy - (r * sin(rad)).toFloat()
            canvas.drawLine(x0, y0, x1, y1, tickPaint)
            tick += 30
        }

        val rad = Math.toRadians(angleDeg.toDouble())
        val nx = cx + (r * cos(rad)).toFloat()
        val ny = cy - (r * sin(rad)).toFloat()
        canvas.drawLine(cx, cy, nx, ny, needlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - pivotX()).toDouble()
                val dy = (pivotY() - event.y).toDouble()
                var deg = Math.toDegrees(atan2(dy, dx)).toFloat()
                if (dy < 0) {
                    deg = if (dx >= 0) 0f else 180f
                }
                angleDeg = deg.coerceIn(0f, 180f)
                onAngleChanged?.invoke(angleDeg)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun setAngle(value: Float) {
        angleDeg = value.coerceIn(0f, 180f)
        invalidate()
    }
}
