package com.pockettanks.ar.game

enum class Weapon(
    val displayName: String,
    val blastRadius: Float,
    val baseDamage: Float,
    /** Positive raises terrain (Dirt Mover); negative carves a crater (Standard HE). */
    val terrainDelta: Float
) {
    STANDARD_HE("Standard HE", blastRadius = 0.08f, baseDamage = 34f, terrainDelta = -0.10f),
    DIRT_MOVER("Dirt Mover", blastRadius = 0.06f, baseDamage = 6f, terrainDelta = 0.09f)
}
