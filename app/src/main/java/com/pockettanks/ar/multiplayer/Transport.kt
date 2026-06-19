package com.pockettanks.ar.multiplayer

import org.json.JSONObject

/**
 * A connected, ordered, message-based pipe between two phones. Implementations
 * differ only in how the underlying socket is established (Bluetooth RFCOMM vs
 * a plain TCP socket over WiFi Direct) - everything above this interface is
 * transport-agnostic.
 *
 * [onMessage] fires on a background thread; callers must marshal back to the
 * UI thread themselves before touching any UI or [com.pockettanks.ar.game.GameState].
 */
interface Transport {
    fun startAsHost()
    fun startAsClient()
    fun send(message: JSONObject)
    fun close()

    var onConnected: (() -> Unit)?
    var onMessage: ((JSONObject) -> Unit)?
    var onDisconnected: (() -> Unit)?
    var onError: ((String) -> Unit)?
}
