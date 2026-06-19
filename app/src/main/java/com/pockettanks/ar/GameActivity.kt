package com.pockettanks.ar

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pockettanks.ar.game.GameState
import com.pockettanks.ar.game.Phase
import com.pockettanks.ar.game.Weapon
import com.pockettanks.ar.multiplayer.GameMode
import com.pockettanks.ar.multiplayer.Protocol
import com.pockettanks.ar.multiplayer.RenderSnapshot
import com.pockettanks.ar.multiplayer.Transport
import com.pockettanks.ar.multiplayer.TransportHolder
import com.pockettanks.ar.ui.BattlefieldView
import com.pockettanks.ar.ui.RadialDialView

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME_MODE = "game_mode"
    }

    private lateinit var gameMode: GameMode
    private var gameState: GameState? = null
    private var transport: Transport? = null

    /** Multiplayer client only - the host never runs physics, just renders whatever the host last broadcast. */
    @Volatile private var clientSnapshot: RenderSnapshot? = null
    private var clientSelectedWeapon: Weapon = Weapon.STANDARD_HE

    private lateinit var battlefieldView: BattlefieldView

    private lateinit var radialDial: RadialDialView
    private lateinit var powerSeekBar: SeekBar
    private lateinit var fireButton: Button
    private lateinit var weaponToggleButton: Button
    private lateinit var turnText: TextView
    private lateinit var hpText: TextView
    private lateinit var angleText: TextView
    private lateinit var powerText: TextView
    private lateinit var gameOverPanel: View
    private lateinit var resultText: TextView
    private lateinit var playAgainButton: Button

    private var currentAngle = 45f
    private var currentPower = 50f

    private val loopHandler = Handler(Looper.getMainLooper())
    private var lastFrameNanos = 0L
    private val loopRunnable = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            val dt = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            lastFrameNanos = now
            val gs = gameState
            if (gs != null) {
                gs.update(dt)
                if (gameMode == GameMode.MULTIPLAYER_HOST) {
                    transport?.send(Protocol.stateSnapshot(gs))
                }
            }
            refreshUi()
            loopHandler.postDelayed(this, 16)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        val modeName = intent.getStringExtra(EXTRA_GAME_MODE)
        gameMode = GameMode.entries.firstOrNull { it.name == modeName } ?: GameMode.SINGLE_PLAYER

        battlefieldView = findViewById(R.id.battlefieldView)
        radialDial = findViewById(R.id.radialDialView)
        powerSeekBar = findViewById(R.id.powerSeekBar)
        fireButton = findViewById(R.id.fireButton)
        weaponToggleButton = findViewById(R.id.weaponToggleButton)
        turnText = findViewById(R.id.turnText)
        hpText = findViewById(R.id.hpText)
        angleText = findViewById(R.id.angleText)
        powerText = findViewById(R.id.powerText)
        gameOverPanel = findViewById(R.id.gameOverPanel)
        resultText = findViewById(R.id.resultText)
        playAgainButton = findViewById(R.id.playAgainButton)

        radialDial.setAngle(currentAngle)
        radialDial.onAngleChanged = { angle ->
            currentAngle = angle
            angleText.text = "${angle.toInt()}°"
        }

        powerSeekBar.progress = currentPower.toInt()
        powerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                currentPower = value.toFloat()
                powerText.text = "POWER $value"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        when (gameMode) {
            GameMode.SINGLE_PLAYER -> setUpSinglePlayer()
            GameMode.MULTIPLAYER_HOST -> setUpHost()
            GameMode.MULTIPLAYER_CLIENT -> setUpClient()
        }
    }

    private fun setUpSinglePlayer() {
        val gs = GameState()
        gameState = gs
        battlefieldView.snapshot = gs
        gs.startMatch()

        fireButton.setOnClickListener {
            if (gs.phase == Phase.PLAYER_AIM) {
                gs.firePlayerShot(currentAngle, currentPower)
            }
        }
        weaponToggleButton.setOnClickListener {
            gs.selectedWeapon = if (gs.selectedWeapon == Weapon.STANDARD_HE) Weapon.DIRT_MOVER else Weapon.STANDARD_HE
            weaponToggleButton.text = gs.selectedWeapon.displayName
        }
        playAgainButton.setOnClickListener {
            gs.restart()
            gameOverPanel.visibility = View.GONE
        }
    }

    private fun setUpHost() {
        val gs = GameState()
        gs.isMultiplayer = true
        gameState = gs
        battlefieldView.snapshot = gs
        gs.startMatch()

        val t = TransportHolder.transport
        transport = t
        t?.onMessage = { json ->
            if (json.optString("type") == "FIRE") {
                val angle = json.getDouble("angle").toFloat()
                val power = json.getDouble("power").toFloat()
                val weapon = Weapon.entries.getOrElse(json.getInt("weaponId")) { Weapon.STANDARD_HE }
                runOnUiThread { gs.fireRemoteShot(angle, power, weapon) }
            }
        }
        t?.onDisconnected = { runOnUiThread { finish() } }

        fireButton.setOnClickListener {
            if (gs.phase == Phase.PLAYER_AIM) {
                gs.firePlayerShot(currentAngle, currentPower)
            }
        }
        weaponToggleButton.setOnClickListener {
            gs.selectedWeapon = if (gs.selectedWeapon == Weapon.STANDARD_HE) Weapon.DIRT_MOVER else Weapon.STANDARD_HE
            weaponToggleButton.text = gs.selectedWeapon.displayName
        }
        playAgainButton.setOnClickListener {
            gs.restart()
            gameOverPanel.visibility = View.GONE
        }
    }

    private fun setUpClient() {
        battlefieldView.snapshot = null
        playAgainButton.visibility = View.INVISIBLE

        val t = TransportHolder.transport
        transport = t
        t?.onMessage = { json ->
            if (json.optString("type") == "STATE_SNAPSHOT") {
                val snap = RenderSnapshot.fromJson(json)
                clientSnapshot = snap
                runOnUiThread {
                    battlefieldView.snapshot = snap
                    refreshUi()
                }
            }
        }
        t?.onDisconnected = { runOnUiThread { finish() } }

        fireButton.setOnClickListener {
            if (clientSnapshot?.phase == Phase.AWAITING_REMOTE_FIRE.name) {
                transport?.send(Protocol.fire(currentAngle, currentPower, clientSelectedWeapon))
            }
        }
        weaponToggleButton.setOnClickListener {
            clientSelectedWeapon = if (clientSelectedWeapon == Weapon.STANDARD_HE) Weapon.DIRT_MOVER else Weapon.STANDARD_HE
            weaponToggleButton.text = clientSelectedWeapon.displayName
        }
    }

    override fun onResume() {
        super.onResume()
        if (gameMode != GameMode.MULTIPLAYER_CLIENT) {
            lastFrameNanos = 0L
            loopHandler.post(loopRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        loopHandler.removeCallbacks(loopRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (gameMode != GameMode.SINGLE_PLAYER) {
            transport?.close()
            TransportHolder.transport = null
        }
    }

    private fun refreshUi() {
        when (gameMode) {
            GameMode.SINGLE_PLAYER -> refreshSinglePlayerUi()
            GameMode.MULTIPLAYER_HOST -> refreshHostUi()
            GameMode.MULTIPLAYER_CLIENT -> refreshClientUi()
        }
    }

    private fun refreshSinglePlayerUi() {
        val gs = gameState ?: return
        gameOverPanel.visibility = if (gs.phase == Phase.GAME_OVER) View.VISIBLE else View.GONE
        if (gs.phase == Phase.GAME_OVER) {
            resultText.text = if (gs.winnerIsPlayer == true) getString(R.string.victory) else getString(R.string.defeat)
        }
        fireButton.visibility = if (gs.phase == Phase.PLAYER_AIM) View.VISIBLE else View.INVISIBLE
        turnText.text = when (gs.phase) {
            Phase.AI_THINKING -> getString(R.string.ai_turn)
            Phase.FLYING -> if (gs.shell?.firedByPlayer == false) getString(R.string.ai_turn) else getString(R.string.your_turn)
            else -> getString(R.string.your_turn)
        }
        hpText.text = "YOU ${gs.player.hp}  |  AI ${gs.ai.hp}"
        battlefieldView.refresh()
    }

    private fun refreshHostUi() {
        val gs = gameState ?: return
        gameOverPanel.visibility = if (gs.phase == Phase.GAME_OVER) View.VISIBLE else View.GONE
        if (gs.phase == Phase.GAME_OVER) {
            resultText.text = if (gs.winnerIsPlayer == true) getString(R.string.victory) else getString(R.string.defeat)
        }
        fireButton.visibility = if (gs.phase == Phase.PLAYER_AIM) View.VISIBLE else View.INVISIBLE
        turnText.text = when (gs.phase) {
            Phase.AWAITING_REMOTE_FIRE -> getString(R.string.opponent_turn)
            Phase.FLYING -> if (gs.shell?.firedByPlayer == false) getString(R.string.opponent_turn) else getString(R.string.your_turn)
            else -> getString(R.string.your_turn)
        }
        hpText.text = "YOU ${gs.player.hp}  |  OPPONENT ${gs.ai.hp}"
        battlefieldView.refresh()
    }

    private fun refreshClientUi() {
        val snap = clientSnapshot ?: return
        val isGameOver = snap.phase == Phase.GAME_OVER.name
        gameOverPanel.visibility = if (isGameOver) View.VISIBLE else View.GONE
        if (isGameOver) {
            resultText.text = if (snap.winnerIsPlayer == false) getString(R.string.victory) else getString(R.string.defeat)
        }
        val isYourTurn = snap.phase == Phase.AWAITING_REMOTE_FIRE.name
        fireButton.visibility = if (isYourTurn) View.VISIBLE else View.INVISIBLE
        turnText.text = if (snap.turnSide == "ai") getString(R.string.your_turn) else getString(R.string.opponent_turn)
        hpText.text = "YOU ${snap.ai.hp}  |  OPPONENT ${snap.player.hp}"
        battlefieldView.refresh()
    }
}
