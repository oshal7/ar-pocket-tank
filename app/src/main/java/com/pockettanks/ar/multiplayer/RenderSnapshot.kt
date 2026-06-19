package com.pockettanks.ar.multiplayer

import com.pockettanks.ar.game.BattlefieldSnapshot
import com.pockettanks.ar.game.ShellSnapshot
import com.pockettanks.ar.game.TankSnapshot
import com.pockettanks.ar.game.TerrainSnapshot
import org.json.JSONObject

class RenderTank(
    override val x: Float,
    override val y: Float,
    override val angleDeg: Float,
    override val facingRight: Boolean,
    override val hp: Int
) : TankSnapshot

class RenderShell(
    override val x: Float,
    override val y: Float,
    override val trail: List<FloatArray>
) : ShellSnapshot

class RenderTerrain(
    override val columns: Int,
    override val halfWidth: Float,
    override val heights: FloatArray
) : TerrainSnapshot

/**
 * Client-side render-only mirror of the host's [com.pockettanks.ar.game.GameState],
 * rebuilt from each incoming STATE_SNAPSHOT message. The client never runs
 * physics - this DTO is all it ever has.
 */
class RenderSnapshot(
    override val terrain: TerrainSnapshot,
    override val player: TankSnapshot,
    override val ai: TankSnapshot,
    override val shell: ShellSnapshot?,
    override val windAccel: Float,
    val phase: String,
    val turnSide: String,
    val winnerIsPlayer: Boolean?
) : BattlefieldSnapshot {

    companion object {
        fun fromJson(o: JSONObject): RenderSnapshot {
            val terrainArr = o.getJSONArray("terrain")
            val heights = FloatArray(terrainArr.length()) { terrainArr.getDouble(it).toFloat() }
            val terrain = RenderTerrain(
                columns = heights.size - 1,
                halfWidth = o.getDouble("halfWidth").toFloat(),
                heights = heights
            )
            val player = tankFrom(o.getJSONObject("player"), facingRight = true)
            val ai = tankFrom(o.getJSONObject("ai"), facingRight = false)
            val shell = if (!o.isNull("shell")) {
                val s = o.getJSONObject("shell")
                val trailArr = s.getJSONArray("trail")
                val trail = (0 until trailArr.length()).map {
                    val p = trailArr.getJSONArray(it)
                    floatArrayOf(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
                }
                RenderShell(x = s.getDouble("x").toFloat(), y = s.getDouble("y").toFloat(), trail = trail)
            } else null
            return RenderSnapshot(
                terrain = terrain,
                player = player,
                ai = ai,
                shell = shell,
                windAccel = o.getDouble("wind").toFloat(),
                phase = o.getString("phase"),
                turnSide = o.getString("turn"),
                winnerIsPlayer = if (o.isNull("winner")) null else o.getBoolean("winner")
            )
        }

        private fun tankFrom(t: JSONObject, facingRight: Boolean): RenderTank = RenderTank(
            x = t.getDouble("x").toFloat(),
            y = t.getDouble("y").toFloat(),
            angleDeg = t.getDouble("angle").toFloat(),
            facingRight = facingRight,
            hp = t.getInt("hp")
        )
    }
}
