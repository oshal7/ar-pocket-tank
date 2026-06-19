package com.pockettanks.ar.multiplayer

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue

/**
 * Shared 4-byte length-prefixed JSON framing over any connected stream socket.
 * Concrete subclasses only need to establish the socket and call [beginSession]
 * once a readable/writable pair of streams exists.
 *
 * A single persistent writer thread drains [sendQueue] so that successive
 * [send] calls are written to the wire in the order they were made - spawning
 * a new thread per send would let the OS schedule them out of order and
 * corrupt the snapshot stream.
 */
abstract class StreamTransport : Transport {

    override var onConnected: (() -> Unit)? = null
    override var onMessage: ((JSONObject) -> Unit)? = null
    override var onDisconnected: (() -> Unit)? = null
    override var onError: ((String) -> Unit)? = null

    @Volatile private var running = false
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private val sendQueue = LinkedBlockingQueue<JSONObject>()
    private var readThread: Thread? = null
    private var writeThread: Thread? = null

    protected fun beginSession(rawInput: InputStream, rawOutput: OutputStream) {
        input = DataInputStream(rawInput)
        output = DataOutputStream(rawOutput)
        running = true

        readThread = Thread {
            try {
                val dataIn = input!!
                while (running) {
                    val len = dataIn.readInt()
                    if (len <= 0 || len > MAX_MESSAGE_BYTES) throw IOException("bad frame length $len")
                    val bytes = ByteArray(len)
                    dataIn.readFully(bytes)
                    val json = JSONObject(String(bytes, Charsets.UTF_8))
                    onMessage?.invoke(json)
                }
            } catch (e: Exception) {
                notifyDisconnected()
            }
        }.also { it.isDaemon = true; it.start() }

        writeThread = Thread {
            try {
                val dataOut = output!!
                while (running) {
                    val msg = sendQueue.take()
                    val bytes = msg.toString().toByteArray(Charsets.UTF_8)
                    dataOut.writeInt(bytes.size)
                    dataOut.write(bytes)
                    dataOut.flush()
                }
            } catch (e: Exception) {
                notifyDisconnected()
            }
        }.also { it.isDaemon = true; it.start() }

        onConnected?.invoke()
    }

    private fun notifyDisconnected() {
        if (running) {
            running = false
            onDisconnected?.invoke()
        }
    }

    override fun send(message: JSONObject) {
        if (running) sendQueue.offer(message)
    }

    override fun close() {
        running = false
        readThread?.interrupt()
        writeThread?.interrupt()
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
    }

    companion object {
        private const val MAX_MESSAGE_BYTES = 1 shl 20
    }
}
