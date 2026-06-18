package com.pockettanks.ar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.pockettanks.ar.game.GameState

/**
 * Top-down 2D Picture-in-Picture radar: flattens the 3D spatial field into a
 * vector map showing terrain silhouette, both tanks, wind, and the live
 * shell position, per the PRD's Dual-View Interface spec.
 */
class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var gameState: GameState? = null

    private val bgPaint = Paint().apply { color = Color.parseColor("#CC0D0D14") }
    private val terrainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#39FF14")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val terrainFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A24")
        style = Paint.Style.FILL
    }
    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00F3FF") }
    private val aiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF0055") }
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val windPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8FBFF")
        textSize = 18f
        textAlign = Paint.Align.CENTER
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00F3FF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    fun refresh() {
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gs = gameState ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val terrain = gs.terrain
        val maxH = terrain.heightScale * 1.4f + terrain.baseHeight
        val groundY = h * 0.92f

        fun worldXToView(x: Float) = ((x + terrain.halfWidth) / (2f * terrain.halfWidth)) * w
        fun worldYToView(y: Float) = groundY - (y / maxH) * (h * 0.55f)

        val path = android.graphics.Path()
        path.moveTo(0f, groundY)
        for (i in 0..terrain.columns) {
            val x = worldXToView((i.toFloat() / terrain.columns) * 2f * terrain.halfWidth - terrain.halfWidth)
            val y = worldYToView(terrain.heights[i])
            path.lineTo(x, y)
        }
        path.lineTo(w, groundY)
        path.close()
        canvas.drawPath(path, terrainFillPaint)
        canvas.drawPath(path, terrainPaint)

        canvas.drawCircle(worldXToView(gs.player.x), worldYToView(gs.player.y) - 4f, 5f, playerPaint)
        canvas.drawCircle(worldXToView(gs.ai.x), worldYToView(gs.ai.y) - 4f, 5f, aiPaint)

        gs.shell?.let { s ->
            canvas.drawCircle(worldXToView(s.x), worldYToView(s.y), 4f, shellPaint)
        }

        val windArrow = if (gs.windAccel >= 0) "WIND ▶" else "WIND ◀"
        canvas.drawText(windArrow, w / 2f, 16f, windPaint)

        canvas.drawRect(1f, 1f, w - 1f, h - 1f, borderPaint)
    }
}
