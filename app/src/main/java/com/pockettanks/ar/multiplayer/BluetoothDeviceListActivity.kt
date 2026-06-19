package com.pockettanks.ar.multiplayer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pockettanks.ar.R

/** Lets the user pick which already-paired phone to connect to as a multiplayer client. */
@SuppressLint("MissingPermission")
class BluetoothDeviceListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "device_address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, getString(R.string.enable_bluetooth_hint), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val bonded = adapter.bondedDevices.toList()
        val listView = findViewById<ListView>(R.id.deviceListView)
        val emptyText = findViewById<TextView>(R.id.emptyText)

        if (bonded.isEmpty()) {
            emptyText.visibility = View.VISIBLE
        }

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            bonded.map { "${it.name}\n${it.address}" }
        )
        listView.setOnItemClickListener { _, _, position, _ ->
            val device = bonded[position]
            val result = Intent()
            result.putExtra(EXTRA_DEVICE_ADDRESS, device.address)
            setResult(RESULT_OK, result)
            finish()
        }
    }
}
