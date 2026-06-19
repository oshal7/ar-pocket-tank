package com.pockettanks.ar.multiplayer

/** Hands a live, connected [Transport] from [com.pockettanks.ar.ConnectingActivity] to [com.pockettanks.ar.GameActivity] - sockets/threads aren't Intent-portable. */
object TransportHolder {
    var transport: Transport? = null
}
