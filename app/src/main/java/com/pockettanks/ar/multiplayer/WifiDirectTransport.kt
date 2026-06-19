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
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(address, PORT), 15000)
                socket = sock
                beginSession(sock.inputStream, sock.outputStream)
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "WiFi Direct connect failed")
            }
        }.also { it.isDaemon = true; it.start() }
    }

    override fun close() {
        super.close()
        try { serverSocket?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }

    companion object {
        private const val PORT = 8988
    }
}
