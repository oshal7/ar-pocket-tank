package com.pockettanks.ar.game

import kotlin.math.cos
import kotlin.math.sin

/**
 * Ballistic shell. Physics follows the PRD's trajectory equation in
 * incremental (Euler-integrated) form rather than the closed form, since the
 * simulation must react to live wind acceleration and terrain collisions.
 */
class Projectile(
    override var x: Float,
    override var y: Float,
    angleDeg: Float,
    powerPct: Float,
    val weapon: Weapon,
    val firedByPlayer: Boolean,
    maxSpeed: Float = MAX_SPEED
) : ShellSnapshot {
    companion object {
        const val GRAVITY = 0.5f
        const val MAX_SPEED = 0.9f
    }

    var vx: Float
    var vy: Float
    override val trail = ArrayList<FloatArray>()

    init {
        val rad = Math.toRadians(angleDeg.toDouble())
        val speed = (powerPct.coerceIn(0f, 100f) / 100f) * maxSpeed
        val dir = if (firedByPlayer) 1f else -1f
        vx = (speed * cos(rad)).toFloat() * dir
        vy = (speed * sin(rad)).toFloat()
    }

    /** Advances physics by dt seconds under gravity + horizontal wind accel. */
    fun step(dt: Float, windAccel: Float) {
        vx += windAccel * dt
        vy -= GRAVITY * dt
        x += vx * dt
        y += vy * dt
        trail.add(floatArrayOf(x, y))
        if (trail.size > 60) trail.removeAt(0)
    }
}
