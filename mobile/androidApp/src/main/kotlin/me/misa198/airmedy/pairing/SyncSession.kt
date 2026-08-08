package me.misa198.airmedy.pairing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.misa198.airmedy.pairing.PairingEndpoint
import me.misa198.airmedy.pairing.PairedDesktop

interface SyncSession {
    val isConnected: StateFlow<Boolean>
    val connectedEndpoint: StateFlow<PairingEndpoint?>
    val syncRequests: Flow<String>

    /** Reconnects only while [reconnect] remains enabled for this in-memory route. */
    fun connect(desktop: PairedDesktop, endpoint: PairingEndpoint, mobileId: String, reconnect: Boolean)
    fun stopReconnecting()
    suspend fun publish(topic: String, payload: String)
    fun disconnect()
}
