package com.pockettanks.ar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.pockettanks.ar.game.GameState
import com.pockettanks.ar.game.Tank
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen top-down 2D battlefield: terrain silhouette, both tanks with
 * turret orientation, the live shell and its trail, and a wind indicator.
 */
class BattlefieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var gameState: GameState? = null

    private val bgPaint = Paint().apply { color = Color.parseColor("#0D0D14") }
    private val terrainStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#39FF14")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val terrainFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A24")
        style = Paint.Style.FILL
    }
    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00F3FF") }
    private val aiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF0055") }
    private val playerTurretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00F3FF")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val aiTurretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF0055")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8000F3FF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val windPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8FBFF")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    /** World-space vertical span mapped to the screen; shells can arc above the terrain's own height range. */
    private val maxHeightWorld = 0.6f
    private val tankBodyHalfWidth = 0.018f
    private val tankBodyHeight = 0.022f
    private val turretLength = 0.05f

    fun refresh() = invalidate()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gs = gameState ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val terrain = gs.terrain
        val groundY = h * 0.82f
        val vScale = h * 0.6f
        val pxPerWorldX = w / (2f * terrain.halfWidth)
        val pxPerWorldY = vScale / maxHeightWorld

        fun worldXToView(x: Float) = (x + terrain.halfWidth) * pxPerWorldX
        fun worldYToView(y: Float) = groundY - y * pxPerWorldY

        val path = Path()
        path.moveTo(0f, groundY)
        for (i in 0..terrain.columns) {
            val x = worldXToView((i.toFloat() / terrain.columns) * 2f * terrain.halfWidth - terrain.halfWidth)
            val y = worldYToView(terrain.heights[i])
            path.lineTo(x, y)
        }
        path.lineTo(w, groundY)
        path.close()
        canvas.drawPath(path, terrainFillPaint)
        canvas.drawPath(path, terrainStrokePaint)

        drawTank(canvas, gs.player, worldXToView(gs.player.x), worldYToView(gs.player.y), pxPerWorldX, pxPerWorldY, playerPaint, playerTurretPaint)
        drawTank(canvas, gs.ai, worldXToView(gs.ai.x), worldYToView(gs.ai.y), pxPerWorldX, pxPerWorldY, aiPaint, aiTurretPaint)

        gs.shell?.let { shell ->
            if (shell.trail.size > 1) {
                val trailPath = Path()
                val (sx0, sy0) = shell.trail[0]
                trailPath.moveTo(worldXToView(sx0), worldYToView(sy0))
                for (i in 1 until shell.trail.size) {
                    val p = shell.trail[i]
                    trailPath.lineTo(worldXToView(p[0]), worldYToView(p[1]))
                }
                canvas.drawPath(trailPath, trailPaint)
            }
            canvas.drawCircle(worldXToView(shell.x), worldYToView(shell.y), 7f, shellPaint)
        }

        val windArrow = if (gs.windAccel >= 0) "WIND ▶" else "WIND ◀"
        canvas.drawText(windArrow, w / 2f, 48f, windPaint)
    }

    private fun drawTank(
        canvas: Canvas,
        tank: Tank,
        vx: Float,
        vy: Float,
        pxPerWorldX: Float,
        pxPerWorldY: Float,
        bodyPaint: Paint,
        turretPaint: Paint
    ) {
        val halfW = tankBodyHalfWidth * pxPerWorldX
        val bodyH = tankBodyHeight * pxPerWorldY
        canvas.drawRoundRect(vx - halfW, vy - bodyH, vx + halfW, vy, 6f, 6f, bodyPaint)

        val dir = if (tank.facingRight) 1f else -1f
        val turretAngleDeg = if (tank.facingRight) tank.angleDeg else 180f - tank.angleDeg
        val rad = Math.toRadians(turretAngleDeg.toDouble())
        val lenPx = turretLength * pxPerWorldX
        val pivotX = vx + dir * halfW * 0.4f
        val pivotY = vy - bodyH * 0.5f
        val tipX = pivotX + (cos(rad) * lenPx).toFloat()
        val tipY = pivotY - (sin(rad) * lenPx).toFloat()
        canvas.drawLine(pivotX, pivotY, tipX, tipY, turretPaint)
    }
}
