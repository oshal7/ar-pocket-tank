package com.pockettanks.ar.multiplayer

import com.pockettanks.ar.game.GameState
import com.pockettanks.ar.game.Phase
import com.pockettanks.ar.game.Weapon
import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire protocol between host and client. The host is authoritative and
 * broadcasts a full [stateSnapshot] every game-loop tick (terrain + both
 * tanks + shell + whose turn it is), so there's no need for separate
 * match-start or restart messages - the next snapshot after a restart just
 * shows fresh terrain and full HP. The client only ever sends [fire].
 */
object MessageType {
    const val FIRE = "FIRE"
    const val STATE_SNAPSHOT = "STATE_SNAPSHOT"
}

object Protocol {

    fun fire(angleDeg: Float, powerPct: Float, weapon: Weapon): JSONObject = JSONObject()
        .put("type", MessageType.FIRE)
        .put("angle", angleDeg.toDouble())
        .put("power", powerPct.toDouble())
        .put("weaponId", weapon.ordinal)

    fun stateSnapshot(state: GameState): JSONObject {
        val o = JSONObject()
        o.put("type", MessageType.STATE_SNAPSHOT)
        o.put("halfWidth", state.terrain.halfWidth.toDouble())
        val terrainArr = JSONArray()
        for (h in state.terrain.heights) terrainArr.put(h.toDouble())
        o.put("terrain", terrainArr)
        o.put("player", tankJson(state.player.x, state.player.y, state.player.angleDeg, state.player.hp))
        o.put("ai", tankJson(state.ai.x, state.ai.y, state.ai.angleDeg, state.ai.hp))

        val shell = state.shell
        if (shell != null) {
            val s = JSONObject()
            s.put("x", shell.x.toDouble())
            s.put("y", shell.y.toDouble())
            val trailArr = JSONArray()
            for (p in shell.trail) {
                trailArr.put(JSONArray().put(p[0].toDouble()).put(p[1].toDouble()))
            }
            s.put("trail", trailArr)
            o.put("shell", s)
        } else {
            o.put("shell", JSONObject.NULL)
        }

        o.put("wind", state.windAccel.toDouble())
        o.put("phase", state.phase.name)
        // Whose decision this currently is, from the host's "player"/"ai" slot naming -
        // mirrors the single-player turnText logic so both sides can derive HUD text identically.
        val turnSide = when (state.phase) {
            Phase.AI_THINKING, Phase.AWAITING_REMOTE_FIRE -> "ai"
            Phase.FLYING -> if (state.shell?.firedByPlayer == false) "ai" else "player"
            else -> "player"
        }
        o.put("turn", turnSide)
        if (state.winnerIsPlayer == null) {
            o.put("winner", JSONObject.NULL)
        } else {
            o.put("winner", state.winnerIsPlayer)
        }
        return o
    }

    private fun tankJson(x: Float, y: Float, angleDeg: Float, hp: Int): JSONObject = JSONObject()
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("angle", angleDeg.toDouble())
        .put("hp", hp)
}
