package com.pockettanks.ar.multiplayer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.util.UUID

/**
 * Bluetooth Classic RFCOMM transport. Requires the two phones to already be
 * paired via the system Bluetooth settings - there is no in-app pairing UI,
 * [remoteDevice] is picked from [BluetoothAdapter.getBondedDevices].
 */
@SuppressLint("MissingPermission")
class BluetoothTransport(
    private val adapter: BluetoothAdapter,
    private val remoteDevice: BluetoothDevice? = null
) : StreamTransport() {

    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null

    override fun startAsHost() {
        Thread {
            try {
                val server = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID)
                serverSocket = server
                val accepted = server.accept()
                socket = accepted
                try { server.close() } catch (_: Exception) {}
                beginSession(accepted.inputStream, accepted.outputStream)
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "Bluetooth host failed")
            }
        }.also { it.isDaemon = true; it.start() }
    }

    override fun startAsClient() {
        val device = remoteDevice
        if (device == null) {
            onError?.invoke("No paired device selected")
            return
        }
        Thread {
            adapter.cancelDiscovery()
            var lastError: Exception? = null
            for (attempt in 1..CONNECT_ATTEMPTS) {
                var sock: BluetoothSocket? = null
                try {
                    sock = device.createRfcommSocketToServiceRecord(APP_UUID)
                    sock.connect()
                    socket = sock
                    beginSession(sock.inputStream, sock.outputStream)
                    return@Thread
                } catch (e: Exception) {
                    lastError = e
                    try { sock?.close() } catch (_: Exception) {}
                    // The other phone may not have started listenUsingRfcommWithServiceRecord
                    // yet if "Host" and "Join" weren't tapped at the same instant - retry.
                    try { Thread.sleep(RETRY_DELAY_MS) } catch (_: InterruptedException) { return@Thread }
                }
            }
            onError?.invoke(lastError?.message ?: "Bluetooth connect failed")
        }.also { it.isDaemon = true; it.start() }
    }

    override fun close() {
        super.close()
        try { serverSocket?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }

    companion object {
        private const val SERVICE_NAME = "PocketTanksAR"
        private val APP_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
        private const val CONNECT_ATTEMPTS = 6
        private const val RETRY_DELAY_MS = 1500L
    }
}
