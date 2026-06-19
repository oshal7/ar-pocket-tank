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
import com.pockettanks.ar.ui.BattlefieldView
import com.pockettanks.ar.ui.RadialDialView

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME_MODE = "game_mode"
    }

    private val gameState = GameState()
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
            gameState.update(dt)
            refreshUi()
            loopHandler.postDelayed(this, 16)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

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

        battlefieldView.gameState = gameState
        gameState.startMatch()

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

        fireButton.setOnClickListener {
            if (gameState.phase == Phase.PLAYER_AIM) {
                gameState.firePlayerShot(currentAngle, currentPower)
            }
        }

        weaponToggleButton.setOnClickListener {
            gameState.selectedWeapon =
                if (gameState.selectedWeapon == Weapon.STANDARD_HE) Weapon.DIRT_MOVER else Weapon.STANDARD_HE
            weaponToggleButton.text = gameState.selectedWeapon.displayName
        }

        playAgainButton.setOnClickListener {
            gameState.restart()
            gameOverPanel.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        lastFrameNanos = 0L
        loopHandler.post(loopRunnable)
    }

    override fun onPause() {
        super.onPause()
        loopHandler.removeCallbacks(loopRunnable)
    }

    private fun refreshUi() {
        gameOverPanel.visibility = if (gameState.phase == Phase.GAME_OVER) View.VISIBLE else View.GONE
        if (gameState.phase == Phase.GAME_OVER) {
            resultText.text = if (gameState.winnerIsPlayer == true) getString(R.string.victory) else getString(R.string.defeat)
        }
        fireButton.visibility = if (gameState.phase == Phase.PLAYER_AIM) View.VISIBLE else View.INVISIBLE
        turnText.text = when (gameState.phase) {
            Phase.AI_THINKING -> getString(R.string.ai_turn)
            Phase.FLYING -> if (gameState.shell?.firedByPlayer == false) getString(R.string.ai_turn) else getString(R.string.your_turn)
            else -> getString(R.string.your_turn)
        }
        hpText.text = "YOU ${gameState.player.hp}  |  AI ${gameState.ai.hp}"
        battlefieldView.refresh()
    }
}
