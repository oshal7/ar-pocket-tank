package com.pockettanks.ar

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.pockettanks.ar.game.GameState
import com.pockettanks.ar.game.Phase
import com.pockettanks.ar.game.Weapon
import com.pockettanks.ar.render.SceneRenderer
import com.pockettanks.ar.ui.RadarView
import com.pockettanks.ar.ui.RadialDialView

class GameActivity : AppCompatActivity() {

    private val gameState = GameState()
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var sceneRenderer: SceneRenderer
    private var session: Session? = null
    private var installRequested = false

    private lateinit var radarView: RadarView
    private lateinit var radialDial: RadialDialView
    private lateinit var powerSeekBar: SeekBar
    private lateinit var fireButton: Button
    private lateinit var weaponToggleButton: Button
    private lateinit var calibrationOverlay: View
    private lateinit var topHud: View
    private lateinit var dialContainer: View
    private lateinit var powerContainer: View
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

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) tryCreateSession() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        radarView = findViewById(R.id.radarView)
        radialDial = findViewById(R.id.radialDialView)
        powerSeekBar = findViewById(R.id.powerSeekBar)
        fireButton = findViewById(R.id.fireButton)
        weaponToggleButton = findViewById(R.id.weaponToggleButton)
        calibrationOverlay = findViewById(R.id.calibrationOverlay)
        topHud = findViewById(R.id.topHud)
        dialContainer = findViewById(R.id.dialContainer)
        powerContainer = findViewById(R.id.powerContainer)
        turnText = findViewById(R.id.turnText)
        hpText = findViewById(R.id.hpText)
        angleText = findViewById(R.id.angleText)
        powerText = findViewById(R.id.powerText)
        gameOverPanel = findViewById(R.id.gameOverPanel)
        resultText = findViewById(R.id.resultText)
        playAgainButton = findViewById(R.id.playAgainButton)

        radarView.gameState = gameState

        sceneRenderer = SceneRenderer(this, gameState)
        glSurfaceView.preserveEGLContextOnPause = true
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        glSurfaceView.setRenderer(sceneRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

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
        if (session == null) {
            requestCameraIfNeeded()
        } else {
            try {
                session?.resume()
                lastFrameNanos = 0L
                loopHandler.post(loopRunnable)
            } catch (e: CameraNotAvailableException) {
                finish()
                return
            }
        }
        glSurfaceView.onResume()
        sceneRenderer.onResume()
    }

    override fun onPause() {
        super.onPause()
        loopHandler.removeCallbacks(loopRunnable)
        sceneRenderer.onPause()
        glSurfaceView.onPause()
        session?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.close()
        session = null
    }

    private fun requestCameraIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            tryCreateSession()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun tryCreateSession() {
        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }

            val newSession = Session(this)
            val config = Config(newSession)
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.focusMode = Config.FocusMode.AUTO
            newSession.configure(config)
            newSession.resume()
            session = newSession
            sceneRenderer.session = newSession

            lastFrameNanos = 0L
            loopHandler.post(loopRunnable)
        } catch (e: UnavailableUserDeclinedInstallationException) {
            finish()
        } catch (e: UnavailableArcoreNotInstalledException) {
            finish()
        } catch (e: UnavailableApkTooOldException) {
            finish()
        } catch (e: UnavailableSdkTooOldException) {
            finish()
        } catch (e: UnavailableDeviceNotCompatibleException) {
            finish()
        } catch (e: CameraNotAvailableException) {
            finish()
        } catch (e: Exception) {
            finish()
        }
    }

    private fun refreshUi() {
        when (gameState.phase) {
            Phase.SCANNING -> {
                calibrationOverlay.visibility = View.VISIBLE
                topHud.visibility = View.INVISIBLE
                radarView.visibility = View.INVISIBLE
                fireButton.visibility = View.INVISIBLE
                dialContainer.visibility = View.INVISIBLE
                powerContainer.visibility = View.INVISIBLE
            }
            Phase.GAME_OVER -> {
                gameOverPanel.visibility = View.VISIBLE
                resultText.text = if (gameState.winnerIsPlayer == true) getString(R.string.victory) else getString(R.string.defeat)
            }
            else -> {
                calibrationOverlay.visibility = View.GONE
                topHud.visibility = View.VISIBLE
                radarView.visibility = View.VISIBLE
                fireButton.visibility = if (gameState.phase == Phase.PLAYER_AIM) View.VISIBLE else View.INVISIBLE
                dialContainer.visibility = View.VISIBLE
                powerContainer.visibility = View.VISIBLE
            }
        }
        turnText.text = when (gameState.phase) {
            Phase.AI_THINKING -> getString(R.string.ai_turn)
            Phase.FLYING -> if (gameState.shell?.firedByPlayer == false) getString(R.string.ai_turn) else getString(R.string.your_turn)
            else -> getString(R.string.your_turn)
        }
        hpText.text = "YOU ${gameState.player.hp}  |  AI ${gameState.ai.hp}"
        radarView.refresh()
    }
}
