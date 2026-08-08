package me.misa198.airmedy.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.misa198.airmedy.pairing.PairingEndpoint
import me.misa198.airmedy.pairing.PairedDesktop
import me.misa198.airmedy.pairing.SyncSession
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySyncServiceTest {
    @Test
    fun retainsTheUiMqttSessionAfterSyncServiceStops() {
        val session = FakeSyncSession()

        releaseMqttSession(session, wasHandedOff = true)

        assertEquals(0, session.disconnectCalls)
    }

    @Test
    fun disconnectsAnMqttSessionCreatedByTheService() {
        val session = FakeSyncSession()

        releaseMqttSession(session, wasHandedOff = false)

        assertEquals(1, session.disconnectCalls)
    }

    private class FakeSyncSession : SyncSession {
        override val isConnected: StateFlow<Boolean> = MutableStateFlow(false)
        override val connectedEndpoint: StateFlow<PairingEndpoint?> = MutableStateFlow(null)
        override val syncRequests: Flow<String> = MutableSharedFlow()
        var disconnectCalls = 0

        override fun connect(desktop: PairedDesktop, endpoint: PairingEndpoint, mobileId: String, reconnect: Boolean) = Unit
        override fun stopReconnecting() = Unit
        override suspend fun publish(topic: String, payload: String) = Unit
        override fun disconnect() { disconnectCalls++ }
    }
}
