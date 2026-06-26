package com.pockettanks.ar.multiplayer

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Plain TCP transport over an already-connected WiFi Direct link. WiFi
 * Direct's own group-owner election decides who runs the [ServerSocket] -
 * callers resolve that via [android.net.wifi.p2p.WifiP2pInfo.isGroupOwner]
 * before choosing [startAsHost] or [startAsClient].
 */
class WifiDirectTransport(
    private val groupOwnerAddress: InetAddress? = null
) : StreamTransport() {

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    override fun startAsHost() {
        Thread {
            try {
                val server = ServerSocket(PORT)
                serverSocket = server
                val accepted = server.accept()
                socket = accepted
                beginSession(accepted.inputStream, accepted.outputStream)
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "WiFi Direct host failed")
            }
        }.also { it.isDaemon = true; it.start() }
    }

    override fun startAsClient() {
        val address = groupOwnerAddress
        if (address == null) {
            onError?.invoke("No group owner address")
            return
        }
        Thread {
            var lastError: Exception? = null
            for (attempt in 1..CONNECT_ATTEMPTS) {
                try {
                    val sock = Socket()
                    sock.connect(InetSocketAddress(address, PORT), CONNECT_TIMEOUT_MS)
                    socket = sock
                    beginSession(sock.inputStream, sock.outputStream)
                    return@Thread
                } catch (e: Exception) {
                    lastError = e
                    // The host may not have opened its ServerSocket yet right after WiFi
                    // Direct's group-owner negotiation completes - retry instead of failing fast.
                    try { Thread.sleep(RETRY_DELAY_MS) } catch (_: InterruptedException) { return@Thread }
                }
            }
            onError?.invoke(lastError?.message ?: "WiFi Direct connect failed")
        }.also { it.isDaemon = true; it.start() }
    }

    override fun close() {
        super.close()
        try { serverSocket?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }

    companion object {
        private const val PORT = 8988
        private const val CONNECT_ATTEMPTS = 8
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val RETRY_DELAY_MS = 1000L
    }
}
