package com.pockettanks.ar.game

/**
 * A tank positioned in local AR-anchor space. [facingRight] controls which
 * direction (along +X or -X) the turret's 0deg elevation points toward.
 */
class Tank(
    var x: Float,
    val facingRight: Boolean,
    var hp: Int = 100
) {
    var y: Float = 0f
    var angleDeg: Float = 45f
    var powerPct: Float = 50f

    val isAlive: Boolean get() = hp > 0

    fun applyDamage(amount: Float) {
        hp = (hp - amount.toInt()).coerceAtLeast(0)
    }

    /** Muzzle launch point, slightly above the tank body, offset toward facing direction. */
    fun muzzleX(): Float = x + (if (facingRight) 0.015f else -0.015f)
    fun muzzleY(): Float = y + 0.02f
}
