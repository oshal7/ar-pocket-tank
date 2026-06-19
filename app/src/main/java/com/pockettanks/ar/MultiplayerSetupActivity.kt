package com.pockettanks.ar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.pockettanks.ar.multiplayer.BluetoothDeviceListActivity
import com.pockettanks.ar.multiplayer.GameMode
import com.pockettanks.ar.multiplayer.Permissions
import com.pockettanks.ar.multiplayer.TransportType
import com.pockettanks.ar.multiplayer.WifiDirectPeerListActivity

class MultiplayerSetupActivity : AppCompatActivity() {

    private var selectedTransport = TransportType.BLUETOOTH
    private var pendingAction: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val action = pendingAction
        pendingAction = null
        if (results.values.all { it }) {
            action?.invoke()
        } else {
            Toast.makeText(this, getString(R.string.permissions_required), Toast.LENGTH_LONG).show()
        }
    }

    private val deviceListLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val address = result.data?.getStringExtra(BluetoothDeviceListActivity.EXTRA_DEVICE_ADDRESS)
        if (result.resultCode == RESULT_OK && address != null) {
            launchConnecting(GameMode.MULTIPLAYER_CLIENT, deviceAddress = address)
        }
    }

    private val wifiPeerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val isGroupOwner = data.getBooleanExtra(WifiDirectPeerListActivity.EXTRA_IS_GROUP_OWNER, false)
            val goAddress = data.getStringExtra(WifiDirectPeerListActivity.EXTRA_GROUP_OWNER_ADDRESS)
            val mode = if (isGroupOwner) GameMode.MULTIPLAYER_HOST else GameMode.MULTIPLAYER_CLIENT
            launchConnecting(mode, groupOwnerAddress = goAddress, isGroupOwner = isGroupOwner)
        }
    }

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
            withPermissions { launchConnecting(GameMode.MULTIPLAYER_HOST) }
        }
        findViewById<Button>(R.id.joinGameButton).setOnClickListener {
            withPermissions {
                if (selectedTransport == TransportType.BLUETOOTH) {
                    deviceListLauncher.launch(Intent(this, BluetoothDeviceListActivity::class.java))
                } else {
                    wifiPeerLauncher.launch(Intent(this, WifiDirectPeerListActivity::class.java))
                }
            }
        }
    }

    private fun withPermissions(action: () -> Unit) {
        val perms = Permissions.required(selectedTransport)
        if (Permissions.allGranted(this, perms)) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(perms)
        }
    }

    private fun launchConnecting(
        mode: GameMode,
        deviceAddress: String? = null,
        groupOwnerAddress: String? = null,
        isGroupOwner: Boolean? = null
    ) {
        val intent = Intent(this, ConnectingActivity::class.java)
        intent.putExtra(ConnectingActivity.EXTRA_GAME_MODE, mode.name)
        intent.putExtra(ConnectingActivity.EXTRA_TRANSPORT_TYPE, selectedTransport.name)
        deviceAddress?.let { intent.putExtra(ConnectingActivity.EXTRA_DEVICE_ADDRESS, it) }
        groupOwnerAddress?.let { intent.putExtra(ConnectingActivity.EXTRA_GROUP_OWNER_ADDRESS, it) }
        isGroupOwner?.let { intent.putExtra(ConnectingActivity.EXTRA_IS_GROUP_OWNER, it) }
        startActivity(intent)
    }
}
