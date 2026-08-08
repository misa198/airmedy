package me.misa198.airmedy.pairing

import kotlinx.coroutines.flow.StateFlow
import me.misa198.airmedy.pairing.PairingEndpoint
import me.misa198.airmedy.pairing.PairedDesktop

interface SyncSession {
    val isConnected: StateFlow<Boolean>

    /** Reconnects only while [reconnect] remains enabled for this in-memory route. */
    fun connect(desktop: PairedDesktop, endpoint: PairingEndpoint, mobileId: String, reconnect: Boolean)
    fun stopReconnecting()
    fun disconnect()
}
