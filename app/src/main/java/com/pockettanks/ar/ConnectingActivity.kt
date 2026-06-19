package com.pockettanks.ar

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pockettanks.ar.multiplayer.BluetoothTransport
import com.pockettanks.ar.multiplayer.GameMode
import com.pockettanks.ar.multiplayer.Transport
import com.pockettanks.ar.multiplayer.TransportHolder
import com.pockettanks.ar.multiplayer.TransportType
import com.pockettanks.ar.multiplayer.WifiDirectTransport
import java.net.InetAddress

class ConnectingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME_MODE = "game_mode"
        const val EXTRA_TRANSPORT_TYPE = "transport_type"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_GROUP_OWNER_ADDRESS = "group_owner_address"
        const val EXTRA_IS_GROUP_OWNER = "is_group_owner"
    }

    private var mode = GameMode.MULTIPLAYER_HOST
    private var transportType = TransportType.BLUETOOTH
    private var transport: Transport? = null
    private var finished = false
    private var wifiReceiverRegistered = false

    /** Used only for the WiFi Direct host path, which has no resolved peer yet and must wait for an incoming connection broadcast. */
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                if (networkInfo?.isConnected == true) {
                    val manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
                    manager.requestConnectionInfo(wifiChannel) { info: WifiP2pInfo ->
                        startWifiTransport(info.isGroupOwner, info.groupOwnerAddress)
                    }
                }
            }
        }
    }
    private var wifiChannel: WifiP2pManager.Channel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connecting)

        val modeName = intent.getStringExtra(EXTRA_GAME_MODE)
        mode = GameMode.entries.firstOrNull { it.name == modeName } ?: GameMode.MULTIPLAYER_HOST
        val transportName = intent.getStringExtra(EXTRA_TRANSPORT_TYPE)
        transportType = TransportType.entries.firstOrNull { it.name == transportName } ?: TransportType.BLUETOOTH

        updateStatusText()
        findViewById<Button>(R.id.cancelButton).setOnClickListener { finish() }

        when (transportType) {
            TransportType.BLUETOOTH -> startBluetooth()
            TransportType.WIFI_DIRECT -> startWifiDirect()
        }
    }

    private fun updateStatusText() {
        findViewById<TextView>(R.id.statusText).text = if (mode == GameMode.MULTIPLAYER_HOST) {
            getString(R.string.connecting_waiting)
        } else {
            getString(R.string.connecting_searching)
        }
    }

    private fun startBluetooth() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            failAndFinish(getString(R.string.bluetooth_unavailable))
            return
        }
        @Suppress("MissingPermission")
        val remoteDevice = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)?.let { address ->
            adapter.bondedDevices.firstOrNull { it.address == address }
        }
        val bt = BluetoothTransport(adapter, remoteDevice)
        attach(bt)
        if (mode == GameMode.MULTIPLAYER_HOST) bt.startAsHost() else bt.startAsClient()
    }

    private fun startWifiDirect() {
        if (intent.hasExtra(EXTRA_IS_GROUP_OWNER)) {
            // Resolved already by WifiDirectPeerListActivity (the "Join" flow).
            val isGroupOwner = intent.getBooleanExtra(EXTRA_IS_GROUP_OWNER, false)
            val addressStr = intent.getStringExtra(EXTRA_GROUP_OWNER_ADDRESS)
            val address = if (addressStr != null) InetAddress.getByName(addressStr) else null
            startWifiTransport(isGroupOwner, address)
            return
        }
        // The "Host" flow: become discoverable and wait for the other phone to connect to us.
        val manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        wifiChannel = channel
        ContextCompat.registerReceiver(
            this, wifiReceiver,
            IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        wifiReceiverRegistered = true
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {
                failAndFinish(getString(R.string.peer_discovery_failed))
            }
        })
    }

    private fun startWifiTransport(isGroupOwner: Boolean, groupOwnerAddress: InetAddress?) {
        val wifi = WifiDirectTransport(groupOwnerAddress)
        attach(wifi)
        mode = if (isGroupOwner) GameMode.MULTIPLAYER_HOST else GameMode.MULTIPLAYER_CLIENT
        if (isGroupOwner) wifi.startAsHost() else wifi.startAsClient()
    }

    private fun attach(t: Transport) {
        transport = t
        t.onConnected = { runOnUiThread { proceedToGame() } }
        t.onError = { message -> runOnUiThread { failAndFinish(message) } }
        t.onDisconnected = { runOnUiThread { failAndFinish(getString(R.string.connection_lost)) } }
    }

    private fun proceedToGame() {
        if (finished) return
        finished = true
        val t = transport ?: return
        TransportHolder.transport = t
        val gameIntent = Intent(this, GameActivity::class.java)
        gameIntent.putExtra(GameActivity.EXTRA_GAME_MODE, mode.name)
        startActivity(gameIntent)
        finish()
    }

    private fun failAndFinish(message: String) {
        if (finished) return
        finished = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        transport?.close()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!finished) {
            transport?.close()
        }
        if (wifiReceiverRegistered) {
            try { unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
        }
    }
}
