package com.pockettanks.ar.render

import com.pockettanks.ar.game.Terrain
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Builds raw vertex data for the simple shapes used by the AR scene. */
object Geometry {

    fun toBuffer(values: FloatArray): FloatBuffer {
        val buf = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(values)
        buf.position(0)
        return buf
    }

    /** Unit cube centered at the origin, spanning -0.5..0.5 on each axis, as GL_TRIANGLES. */
    val unitCube: FloatArray by lazy {
        val v = arrayOf(
            floatArrayOf(-0.5f, -0.5f, -0.5f), floatArrayOf(0.5f, -0.5f, -0.5f),
            floatArrayOf(0.5f, 0.5f, -0.5f), floatArrayOf(-0.5f, 0.5f, -0.5f),
            floatArrayOf(-0.5f, -0.5f, 0.5f), floatArrayOf(0.5f, -0.5f, 0.5f),
            floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(-0.5f, 0.5f, 0.5f)
        )
        val faces = arrayOf(
            intArrayOf(0, 1, 2, 0, 2, 3), // back
            intArrayOf(5, 4, 7, 5, 7, 6), // front
            intArrayOf(4, 0, 3, 4, 3, 7), // left
            intArrayOf(1, 5, 6, 1, 6, 2), // right
            intArrayOf(3, 2, 6, 3, 6, 7), // top
            intArrayOf(4, 5, 1, 4, 1, 0)  // bottom
        )
        val out = ArrayList<Float>(36 * 3)
        for (face in faces) for (idx in face) out.addAll(v[idx].toList())
        out.toFloatArray()
    }

    /**
     * Builds a zero-thickness "diorama" wall following the terrain silhouette
     * (a simplification of full 3D voxel terrain, double-sided rendered),
     * plus a glowing ridge line and periodic vertical grid lines.
     */
    fun buildTerrain(terrain: Terrain): TerrainMesh {
        val wall = ArrayList<Float>()
        val ridge = ArrayList<Float>()
        val grid = ArrayList<Float>()

        for (i in 0..terrain.columns) {
            val x = (i.toFloat() / terrain.columns) * 2f * terrain.halfWidth - terrain.halfWidth
            val h = terrain.heights[i]
            wall.add(x); wall.add(h); wall.add(0f)
            wall.add(x); wall.add(0f); wall.add(0f)
            ridge.add(x); ridge.add(h + 0.002f); ridge.add(0f)
            if (i % 4 == 0) {
                grid.add(x); grid.add(0f); grid.add(0f)
                grid.add(x); grid.add(h); grid.add(0f)
            }
        }
        return TerrainMesh(wall.toFloatArray(), ridge.toFloatArray(), grid.toFloatArray())
    }

    fun buildPolyline(points: List<FloatArray>): FloatArray {
        val out = FloatArray(points.size * 3)
        for ((i, p) in points.withIndex()) {
            out[i * 3] = p[0]
            out[i * 3 + 1] = p[1]
            out[i * 3 + 2] = if (p.size > 2) p[2] else 0f
        }
        return out
    }

    data class TerrainMesh(val wallTriStrip: FloatArray, val ridgeLineStrip: FloatArray, val gridLines: FloatArray)
}
