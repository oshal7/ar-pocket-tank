package com.pockettanks.ar

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pockettanks.ar.multiplayer.GameMode

class ConnectingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME_MODE = "game_mode"
        const val EXTRA_TRANSPORT_TYPE = "transport_type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connecting)

        val modeName = intent.getStringExtra(EXTRA_GAME_MODE)
        val mode = GameMode.entries.firstOrNull { it.name == modeName } ?: GameMode.MULTIPLAYER_HOST

        findViewById<TextView>(R.id.statusText).text = if (mode == GameMode.MULTIPLAYER_HOST) {
            getString(R.string.connecting_waiting)
        } else {
            getString(R.string.connecting_searching)
        }

        findViewById<Button>(R.id.cancelButton).setOnClickListener { finish() }
    }
}
