package com.pockettanks.ar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.pockettanks.ar.multiplayer.GameMode
import com.pockettanks.ar.multiplayer.TransportType

class MultiplayerSetupActivity : AppCompatActivity() {

    private var selectedTransport = TransportType.BLUETOOTH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multiplayer_setup)

        val bluetoothToggle = findViewById<Button>(R.id.bluetoothToggle)
        val wifiDirectToggle = findViewById<Button>(R.id.wifiDirectToggle)

        fun updateToggleColors() {
            bluetoothToggle.setTextColor(
                getColor(if (selectedTransport == TransportType.BLUETOOTH) R.color.neon_cyan else R.color.hud_text)
            )
            wifiDirectToggle.setTextColor(
                getColor(if (selectedTransport == TransportType.WIFI_DIRECT) R.color.neon_cyan else R.color.hud_text)
            )
        }

        bluetoothToggle.setOnClickListener {
            selectedTransport = TransportType.BLUETOOTH
            updateToggleColors()
        }
        wifiDirectToggle.setOnClickListener {
            selectedTransport = TransportType.WIFI_DIRECT
            updateToggleColors()
        }

        findViewById<Button>(R.id.hostGameButton).setOnClickListener {
            launchConnecting(GameMode.MULTIPLAYER_HOST)
        }
        findViewById<Button>(R.id.joinGameButton).setOnClickListener {
            launchConnecting(GameMode.MULTIPLAYER_CLIENT)
        }
    }

    private fun launchConnecting(mode: GameMode) {
        val intent = Intent(this, ConnectingActivity::class.java)
        intent.putExtra(ConnectingActivity.EXTRA_GAME_MODE, mode.name)
        intent.putExtra(ConnectingActivity.EXTRA_TRANSPORT_TYPE, selectedTransport.name)
        startActivity(intent)
    }
}
