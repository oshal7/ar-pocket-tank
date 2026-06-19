package com.pockettanks.ar.game

import kotlin.random.Random

/**
 * 1D heightfield representing the destructible mountain cross-section,
 * anchored in local AR-anchor space. X spans [-halfWidth, halfWidth].
 */
class Terrain(
    override val columns: Int = 40,
    override val halfWidth: Float = 0.35f,
    val baseHeight: Float = 0.03f,
    val heightScale: Float = 0.18f
) : TerrainSnapshot {
    override val heights = FloatArray(columns + 1)

    init {
        regenerate()
    }

    /** Procedural midpoint-displacement fractal terrain. */
    fun regenerate(seed: Long = Random.nextLong()) {
        val rnd = Random(seed)
        val n = columns
        val work = FloatArray(n + 1)
        work[0] = 0.4f + rnd.nextFloat() * 0.2f
        work[n] = 0.4f + rnd.nextFloat() * 0.2f
        midpointDisplace(work, 0, n, 0.5f, rnd)
        for (i in 0..n) {
            heights[i] = baseHeight + work[i].coerceIn(0f, 1f) * heightScale
        }
    }

    private fun midpointDisplace(arr: FloatArray, left: Int, right: Int, roughness: Float, rnd: Random) {
        if (right - left < 2) return
        val mid = (left + right) / 2
        val avg = (arr[left] + arr[right]) / 2f
        val offset = (rnd.nextFloat() - 0.5f) * roughness
        arr[mid] = (avg + offset).coerceIn(0f, 1f)
        midpointDisplace(arr, left, mid, roughness * 0.55f, rnd)
        midpointDisplace(arr, mid, right, roughness * 0.55f, rnd)
    }

    fun xToColumn(x: Float): Float {
        val t = ((x + halfWidth) / (2f * halfWidth)).coerceIn(0f, 1f)
        return t * columns
    }

    fun heightAt(x: Float): Float {
        val c = xToColumn(x)
        val i0 = c.toInt().coerceIn(0, columns)
        val i1 = (i0 + 1).coerceIn(0, columns)
        val frac = c - i0
        return heights[i0] * (1 - frac) + heights[i1] * frac
    }

    /** Carves a crater centered at world x with the given radius/depth. */
    fun deform(x: Float, radiusWorld: Float, amount: Float) {
        val radiusCols = (radiusWorld / (2f * halfWidth) * columns).coerceAtLeast(1f)
        val centerCol = xToColumn(x)
        val minI = (centerCol - radiusCols).toInt().coerceIn(0, columns)
        val maxI = (centerCol + radiusCols).toInt().coerceIn(0, columns)
        for (i in minI..maxI) {
            val dist = kotlin.math.abs(i - centerCol)
            val falloff = (1f - (dist / radiusCols)).coerceIn(0f, 1f)
            heights[i] = (heights[i] + amount * falloff).coerceIn(0.005f, heightScale * 1.4f)
        }
    }
}
