package com.pockettanks.ar.multiplayer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pockettanks.ar.R

/**
 * Discovers nearby WiFi Direct peers and connects to whichever one the user
 * taps. Once the connection completes, hands back the resolved group-owner
 * role/address - WiFi Direct's own negotiation decides who the group owner
 * is, it doesn't necessarily match whoever tapped "Join".
 */
@SuppressLint("MissingPermission")
class WifiDirectPeerListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IS_GROUP_OWNER = "is_group_owner"
        const val EXTRA_GROUP_OWNER_ADDRESS = "group_owner_address"
    }

    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private var peers: List<WifiP2pDevice> = emptyList()
    private lateinit var listView: ListView
    private lateinit var emptyText: TextView
    private var resolved = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager.requestPeers(channel) { list: WifiP2pDeviceList ->
                        peers = list.deviceList.toList()
                        refreshList()
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        manager.requestConnectionInfo(channel) { info -> finishWithInfo(info) }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)
        title = getString(R.string.select_peer)

        listView = findViewById(R.id.deviceListView)
        emptyText = findViewById(R.id.emptyText)
        emptyText.text = getString(R.string.no_peers_found)
        emptyText.visibility = View.VISIBLE
        findViewById<TextView>(R.id.titleText).text = getString(R.string.select_peer)

        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {
                Toast.makeText(this@WifiDirectPeerListActivity, getString(R.string.peer_discovery_failed), Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    private fun refreshList() {
        emptyText.visibility = if (peers.isEmpty()) View.VISIBLE else View.GONE
        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            peers.map { "${it.deviceName}\n${it.deviceAddress}" }
        )
        listView.setOnItemClickListener { _, _, position, _ ->
            val device = peers[position]
            val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(reason: Int) {
                    Toast.makeText(this@WifiDirectPeerListActivity, getString(R.string.peer_connect_failed), Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun finishWithInfo(info: android.net.wifi.p2p.WifiP2pInfo) {
        if (resolved) return
        resolved = true
        val result = Intent()
        result.putExtra(EXTRA_IS_GROUP_OWNER, info.isGroupOwner)
        result.putExtra(EXTRA_GROUP_OWNER_ADDRESS, info.groupOwnerAddress?.hostAddress)
        setResult(RESULT_OK, result)
        finish()
    }
}
