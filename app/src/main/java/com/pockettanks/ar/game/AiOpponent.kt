package com.pockettanks.ar.game

import kotlin.math.sin
import kotlin.random.Random

/**
 * Computes the AI's shot. Solves the inverse ballistic formula for a fixed
 * 45deg launch angle (optimal range angle), then injects a difficulty- and
 * turn-scaled error so early shots are noticeably off and later shots
 * converge on the player, per the PRD's "smart scaling difficulty" spec.
 */
object AiOpponent {

    fun computeShot(distance: Float, turnIndex: Int): Pair<Float, Float> {
        val angleDeg = 45f
        val g = Projectile.GRAVITY
        // R = V^2 * sin(2*theta) / g  =>  V = sqrt(R * g / sin(2*theta))
        val sin2theta = sin(Math.toRadians((2 * angleDeg).toDouble())).toFloat()
        val idealV = kotlin.math.sqrt((distance * g / sin2theta).coerceAtLeast(0f))
        val idealPowerPct = (idealV / Projectile.MAX_SPEED * 100f).coerceIn(5f, 100f)

        // Error shrinks each AI turn: starts wide (sloppy warning shots), tightens with experience.
        val maxEpsilon = (22f - turnIndex * 4f).coerceIn(2f, 22f)
        val error = Random.nextFloat() * 2f * maxEpsilon - maxEpsilon
        val finalPower = (idealPowerPct + error).coerceIn(5f, 100f)

        return Pair(angleDeg, finalPower)
    }
}
