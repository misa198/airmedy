package me.misa198.airmedy.sync

import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.misa198.airmedy.pairing.PairingEndpoint
import me.misa198.airmedy.pairing.PairedDesktop
import me.misa198.airmedy.pairing.SyncSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class LibrarySyncServiceTest {
    @Test
    fun capacityUsesTheVolumeContainingFilesDir() = runTest {
        val filesDir = File("app-files")
        val volume = UUID.randomUUID()
        var requestedPath: File? = null

        val available = AndroidLibrarySyncCapacity(
            filesDir,
            uuidForPath = { requestedPath = it; volume },
            allocatableBytes = { assertEquals(volume, it); 42L },
        ).availableBytes()

        assertEquals(filesDir, requestedPath)
        assertEquals(42L, available)
    }

    @Test
    fun progressNotificationUsesADeterminatePercentageBar() {
        assertEquals(
            SyncNotificationProgress(max = 100, current = 45, indeterminate = false),
            syncNotificationProgress(percent = 45, indeterminate = false),
        )
    }

    @Test
    fun connectingNotificationUsesAnIndeterminateProgressBar() {
        assertEquals(
            SyncNotificationProgress(max = 0, current = 0, indeterminate = true),
            syncNotificationProgress(percent = null, indeterminate = true),
        )
    }

    @Test
    fun downloadParallelismUsesHalfTheCpuCountBoundedToTwoThroughFour() {
        assertEquals(2, syncDownloadParallelism(1))
        assertEquals(2, syncDownloadParallelism(4))
        assertEquals(4, syncDownloadParallelism(16))
    }

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

    @Test fun reconciliationWaitsUntilForegroundSyncReleasesMqttOwnership() = runTest {
        AndroidSyncRuntime.running()
        val waiter = async { AndroidSyncRuntime.awaitNoForegroundSync() }
        yield()
        assertFalse(waiter.isCompleted)
        AndroidSyncRuntime.idle()
        waiter.await()
    }

    private class FakeSyncSession : SyncSession {
        override val isConnected: StateFlow<Boolean> = MutableStateFlow(false)
        override val connectedEndpoint: StateFlow<PairingEndpoint?> = MutableStateFlow(null)
        override val syncRequests: Flow<String> = MutableSharedFlow()
        override val playlistReconciliationRequests: Flow<String> = MutableSharedFlow()
        var disconnectCalls = 0

        override fun connect(desktop: PairedDesktop, endpoint: PairingEndpoint, mobileId: String, reconnect: Boolean) = Unit
        override fun stopReconnecting() = Unit
        override suspend fun publish(topic: String, payload: String) = Unit
        override fun disconnect() { disconnectCalls++ }
    }
}
