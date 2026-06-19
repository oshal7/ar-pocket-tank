package com.pockettanks.ar.game

/**
 * Render-only view of a tank. Implemented directly by [Tank] (host/single-player)
 * and by a plain DTO built from network JSON (multiplayer client).
 */
interface TankSnapshot {
    val x: Float
    val y: Float
    val angleDeg: Float
    val facingRight: Boolean
    val hp: Int
}

interface ShellSnapshot {
    val x: Float
    val y: Float
    val trail: List<FloatArray>
}

interface TerrainSnapshot {
    val columns: Int
    val halfWidth: Float
    val heights: FloatArray
}

/** Everything [com.pockettanks.ar.ui.BattlefieldView] needs to draw one frame. */
interface BattlefieldSnapshot {
    val terrain: TerrainSnapshot
    val player: TankSnapshot
    val ai: TankSnapshot
    val shell: ShellSnapshot?
    val windAccel: Float
}
