package com.pockettanks.ar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.pockettanks.ar.multiplayer.GameMode

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        findViewById<Button>(R.id.singlePlayerButton).setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra(GameActivity.EXTRA_GAME_MODE, GameMode.SINGLE_PLAYER.name)
            startActivity(intent)
        }

        findViewById<Button>(R.id.multiplayerButton).setOnClickListener {
            startActivity(Intent(this, MultiplayerSetupActivity::class.java))
        }
    }
}
