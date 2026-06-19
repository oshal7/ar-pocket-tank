package com.pockettanks.ar.game

import kotlin.random.Random

enum class Phase { PLAYER_AIM, FLYING, AI_THINKING, AWAITING_REMOTE_FIRE, GAME_OVER }

/**
 * Single source of truth for the match. Mutated exclusively from the UI
 * thread's game loop. In multiplayer the host owns the only [GameState]
 * instance in the match; the client never simulates, it only renders
 * snapshots broadcast by the host (see [com.pockettanks.ar.multiplayer.RenderSnapshot]).
 */
class GameState : BattlefieldSnapshot {

    override val terrain = Terrain()
    override val player = Tank(x = -terrain.halfWidth + 0.06f, facingRight = true)
    override val ai = Tank(x = terrain.halfWidth - 0.06f, facingRight = false)

    @Volatile var phase: Phase = Phase.PLAYER_AIM

    var selectedWeapon: Weapon = Weapon.STANDARD_HE
    override var windAccel: Float = 0f
    var isMultiplayer: Boolean = false

    @Volatile override var shell: Projectile? = null
    var turnIndex: Int = 0
    @Volatile var winnerIsPlayer: Boolean? = null
    var aiThinkTimer: Float = 0f
    var lastImpactX: Float? = null

    init {
        settleTanks()
        rollWind()
    }

    private fun settleTanks() {
        player.y = terrain.heightAt(player.x)
        ai.y = terrain.heightAt(ai.x)
    }

    private fun rollWind() {
        windAccel = (Random.nextFloat() - 0.5f) * 0.24f
    }

    fun startMatch() {
        phase = Phase.PLAYER_AIM
    }

    fun firePlayerShot(angleDeg: Float, powerPct: Float) {
        if (phase != Phase.PLAYER_AIM) return
        player.angleDeg = angleDeg
        player.powerPct = powerPct
        shell = Projectile(player.muzzleX(), player.muzzleY(), angleDeg, powerPct, selectedWeapon, firedByPlayer = true)
        phase = Phase.FLYING
    }

    /** Applies a shot chosen by the remote human player (multiplayer only). */
    fun fireRemoteShot(angleDeg: Float, powerPct: Float, weapon: Weapon) {
        if (phase != Phase.AWAITING_REMOTE_FIRE) return
        ai.angleDeg = angleDeg
        ai.powerPct = powerPct
        shell = Projectile(ai.muzzleX(), ai.muzzleY(), angleDeg, powerPct, weapon, firedByPlayer = false)
        phase = Phase.FLYING
    }

    private fun fireAiShot() {
        val distance = kotlin.math.abs(ai.x - player.x)
        val (angle, power) = AiOpponent.computeShot(distance, turnIndex)
        ai.angleDeg = angle
        ai.powerPct = power
        val weapon = if (Random.nextFloat() < 0.25f) Weapon.DIRT_MOVER else Weapon.STANDARD_HE
        shell = Projectile(ai.muzzleX(), ai.muzzleY(), angle, power, weapon, firedByPlayer = false)
        phase = Phase.FLYING
    }

    /** Advances the simulation. Called every frame from the UI-thread game loop. */
    fun update(dt: Float) {
        when (phase) {
            Phase.FLYING -> stepShell(dt)
            Phase.AI_THINKING -> {
                aiThinkTimer -= dt
                if (aiThinkTimer <= 0f) fireAiShot()
            }
            else -> Unit
        }
    }

    private fun stepShell(dt: Float) {
        val s = shell ?: return
        s.step(dt, windAccel)

        val groundY = terrain.heightAt(s.x)
        val hitGround = s.y <= groundY
        val hitPlayer = s.firedByPlayer.not() && distanceToTank(s, player) < 0.035f
        val hitAi = s.firedByPlayer && distanceToTank(s, ai) < 0.035f
        val outOfBounds = s.x < -terrain.halfWidth - 0.15f || s.x > terrain.halfWidth + 0.15f

        if (hitGround || hitPlayer || hitAi || outOfBounds) {
            resolveImpact(s, hitPlayer, hitAi, outOfBounds)
        }
    }

    private fun distanceToTank(s: Projectile, t: Tank): Float {
        val dx = s.x - t.x
        val dy = s.y - t.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun resolveImpact(s: Projectile, hitPlayer: Boolean, hitAi: Boolean, outOfBounds: Boolean) {
        if (!outOfBounds) {
            lastImpactX = s.x
            terrain.deform(s.x, s.weapon.blastRadius, s.weapon.terrainDelta)

            if (hitPlayer || nearBlast(s, player)) {
                player.applyDamage(damageFalloff(s, player))
            }
            if (hitAi || nearBlast(s, ai)) {
                ai.applyDamage(damageFalloff(s, ai))
            }
        }
        shell = null
        settleTanks()

        if (!player.isAlive || !ai.isAlive) {
            winnerIsPlayer = ai.isAlive.not()
            phase = Phase.GAME_OVER
            return
        }

        if (s.firedByPlayer) {
            turnIndex++
            rollWind()
            phase = if (isMultiplayer) Phase.AWAITING_REMOTE_FIRE else Phase.AI_THINKING
            aiThinkTimer = 1.1f
        } else {
            rollWind()
            phase = Phase.PLAYER_AIM
        }
    }

    private fun nearBlast(s: Projectile, t: Tank): Boolean = distanceToTank(s, t) < s.weapon.blastRadius

    private fun damageFalloff(s: Projectile, t: Tank): Float {
        val dist = distanceToTank(s, t)
        val falloff = (1f - (dist / s.weapon.blastRadius)).coerceIn(0f, 1f)
        return s.weapon.baseDamage * falloff
    }

    fun restart() {
        terrain.regenerate()
        player.hp = 100
        ai.hp = 100
        turnIndex = 0
        winnerIsPlayer = null
        shell = null
        settleTanks()
        rollWind()
        phase = Phase.PLAYER_AIM
    }
}
