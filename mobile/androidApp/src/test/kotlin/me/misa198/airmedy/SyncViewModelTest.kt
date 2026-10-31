package me.misa198.airmedy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import java.util.Base64
import me.misa198.airmedy.pairing.MobileIdentity
import me.misa198.airmedy.pairing.MobilePairingUseCase
import me.misa198.airmedy.pairing.PairingBindingStore
import me.misa198.airmedy.pairing.PairingClock
import me.misa198.airmedy.pairing.PairingEndpoint
import me.misa198.airmedy.pairing.PairingIdGenerator
import me.misa198.airmedy.pairing.PairingIdentityProvider
import me.misa198.airmedy.pairing.PairingTransport
import me.misa198.airmedy.pairing.PairedDesktop
import me.misa198.airmedy.pairing.SyncSession
import me.misa198.airmedy.pairing.TransportResult
import me.misa198.airmedy.pairing.TrustedDesktopDiscovery
import me.misa198.airmedy.sync.AndroidSyncRuntime
import me.misa198.airmedy.sync.AndroidSyncState
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {
    @Before
    fun resetSyncRuntime() = AndroidSyncRuntime.idle()

    @After
    fun tearDown() {
        AndroidSyncRuntime.idle()
        Dispatchers.resetMain()
    }

    @Test
    fun discoversOnlyWhileTheVisibleSyncScreenIsOfflineAndConnectsMatchingEndpoint() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val bindings = FakeBindings(PairedDesktop(DesktopId, "Studio Mac", ByteArray(32)))
        val session = FakeSyncSession()
        val discovery = FakeDiscovery()
        val viewModel = SyncViewModel(useCase(bindings), session, discovery)
        advanceUntilIdle()

        viewModel.onSyncScreenVisible()
        advanceUntilIdle()
        assertEquals(listOf(DesktopId), discovery.startedDesktopIds)

        discovery.emit(PairingEndpoint("192.168.1.20", 1883))
        advanceUntilIdle()
        assertEquals(PairingEndpoint("192.168.1.20", 1883), session.connects.single().endpoint)

        session.setConnected(true)
        advanceUntilIdle()
        assertEquals(1, session.stopReconnectCalls)

        viewModel.onSyncScreenHidden()
        advanceUntilIdle()
        assertEquals(0, discovery.active)
    }

    @Test
    fun doesNotDiscoverWithoutATrustedDesktopOrWhenAlreadyConnected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val noDesktopDiscovery = FakeDiscovery()
        val noDesktop = SyncViewModel(useCase(FakeBindings(null)), FakeSyncSession(), noDesktopDiscovery)
        advanceUntilIdle()
        noDesktop.onSyncScreenVisible()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), noDesktopDiscovery.startedDesktopIds)

        val connectedSession = FakeSyncSession(connected = true)
        val connectedDiscovery = FakeDiscovery()
        val connected = SyncViewModel(
            useCase(FakeBindings(PairedDesktop(DesktopId, "Studio Mac", ByteArray(32)))),
            connectedSession,
            connectedDiscovery,
        )
        advanceUntilIdle()
        connected.onSyncScreenVisible()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), connectedDiscovery.startedDesktopIds)
    }

    @Test
    fun savedQrRouteConnectsOnceWithoutBackgroundRetries() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = FakeSyncSession()
        val viewModel = SyncViewModel(
            useCase(FakeBindings(PairedDesktop(DesktopId, "Studio Mac", ByteArray(32), "192.168.1.20", 1883))),
            session,
            FakeDiscovery(),
        )
        advanceUntilIdle()

        assertEquals(false, session.connects.single().reconnect)
        viewModel.onSyncScreenHidden()
    }

    @Test
    fun doesNotReplaceTheForegroundSyncServiceMqttSessionWhenAppOpens() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = FakeSyncSession()
        SyncViewModel(
            useCase(FakeBindings(PairedDesktop(DesktopId, "Studio Mac", ByteArray(32), "192.168.1.20", 1883))),
            session,
            FakeDiscovery(),
            isForegroundSyncRunning = { true },
        )
        advanceUntilIdle()

        assertEquals(emptyList<FakeSyncSession.Connect>(), session.connects)
    }

    @Test
    fun handsTheReceivingMqttSessionToTheSyncServiceStarter() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = FakeSyncSession()
        val endpoint = PairingEndpoint("192.168.1.20", 1883)
        var received: Triple<String, PairingEndpoint, SyncSession>? = null
        SyncViewModel(
            useCase(FakeBindings(PairedDesktop(DesktopId, "Studio Mac", ByteArray(32)))),
            session,
            FakeDiscovery(),
            onSyncRequest = { payload, route, mqtt -> received = Triple(payload, route, mqtt) },
        )
        advanceUntilIdle()
        session.setEndpoint(endpoint)
        session.emitRequest("request")
        advanceUntilIdle()

        assertEquals(Triple("request", endpoint, session), received)
    }

    @Test
    fun doesNotUnpairWhileLibrarySyncIsRunning() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val desktop = PairedDesktop(DesktopId, "Studio Mac", ByteArray(32))
        val bindings = FakeBindings(desktop)
        var beforeUnpairCalls = 0
        val viewModel = SyncViewModel(
            useCase(bindings),
            FakeSyncSession(),
            FakeDiscovery(),
            onBeforeUnpair = { beforeUnpairCalls++ },
        )
        advanceUntilIdle()
        AndroidSyncRuntime.running()
        advanceUntilIdle()

        viewModel.unpair()
        advanceUntilIdle()

        assertEquals(0, beforeUnpairCalls)
        assertEquals(desktop, bindings.current())
    }

    @Test
    fun unpairCleansUpBeforeRemovingTheBinding() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<String>()
        val bindings = FakeBindings(PairedDesktop(DesktopId, "Studio Mac", ByteArray(32)), events)
        val viewModel = SyncViewModel(
            useCase(bindings),
            FakeSyncSession(),
            FakeDiscovery(),
            onBeforeUnpair = { events += "cleanup" },
        )
        advanceUntilIdle()

        viewModel.unpair()
        advanceUntilIdle()

        assertEquals(listOf("cleanup", "clear"), events)
        assertEquals(null, bindings.current())
    }

    @Test
    fun exposesInsufficientStorageByteCounts() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = SyncViewModel(useCase(FakeBindings(null)), FakeSyncSession(), FakeDiscovery())
        advanceUntilIdle()

        AndroidSyncRuntime.failed("storage", requiredBytes = 12, availableBytes = 0)
        advanceUntilIdle()

        assertEquals(AndroidSyncState.Failed("storage", 12, 0), viewModel.uiState.value.librarySync)
    }

    @Test
    fun missingPermissionBlocksStartupDiscoveryAndIncomingSyncUntilGranted() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val desktop = PairedDesktop(DesktopId, "Studio Mac", ByteArray(32), "192.168.1.20", 1883)
        val bindings = FakeBindings(desktop)
        val session = FakeSyncSession()
        val discovery = FakeDiscovery()
        var syncRequests = 0
        var reconciliationRequests = 0
        val viewModel = SyncViewModel(
            useCase(bindings), session, discovery,
            onSyncRequest = { _, _, _ -> syncRequests++ },
            onPlaylistReconciliationRequest = { reconciliationRequests++ },
            initialLocalNetworkAccess = LocalNetworkAccess.Required,
        )
        advanceUntilIdle()
        viewModel.onSyncScreenVisible()
        session.setEndpoint(PairingEndpoint("192.168.1.20", 1883))
        discovery.emit(PairingEndpoint("192.168.1.21", 1883))
        session.emitRequest("request")
        session.emitReconciliation("request")
        advanceUntilIdle()

        assertEquals(emptyList<FakeSyncSession.Connect>(), session.connects)
        assertEquals(0, discovery.active)
        assertEquals(0, syncRequests)
        assertEquals(0, reconciliationRequests)
        assertEquals(desktop, bindings.current())

        viewModel.setLocalNetworkAccess(LocalNetworkAccess.Granted)
        advanceUntilIdle()
        assertEquals(PairingEndpoint("192.168.1.20", 1883), session.connects.single().endpoint)
        assertEquals(1, discovery.active)
        session.emitRequest("request")
        session.emitReconciliation("request")
        advanceUntilIdle()
        assertEquals(1, syncRequests)
        assertEquals(1, reconciliationRequests)

        viewModel.setLocalNetworkAccess(LocalNetworkAccess.Granted)
        advanceUntilIdle()
        assertEquals(1, session.connects.size)
    }

    @Test
    fun revokingPermissionStopsNetworkWorkAndRetainsThePairing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val desktop = PairedDesktop(DesktopId, "Studio Mac", ByteArray(32), "192.168.1.20", 1883)
        val bindings = FakeBindings(desktop)
        val session = FakeSyncSession()
        val discovery = FakeDiscovery()
        var revokedCalls = 0
        var reconciliationCancelled = false
        val viewModel = SyncViewModel(
            useCase(bindings), session, discovery,
            onPlaylistReconciliationRequest = {
                try { awaitCancellation() } finally { reconciliationCancelled = true }
            },
            onLocalNetworkAccessRevoked = { revokedCalls++ },
        )
        advanceUntilIdle()
        viewModel.onSyncScreenVisible()
        session.emitReconciliation("request")
        advanceUntilIdle()
        val priorDisconnects = session.disconnectCalls

        viewModel.setLocalNetworkAccess(LocalNetworkAccess.Settings)
        advanceUntilIdle()
        assertEquals(priorDisconnects + 1, session.disconnectCalls)
        assertEquals(0, discovery.active)
        assertEquals(1, revokedCalls)
        assertEquals(true, reconciliationCancelled)
        assertEquals(false, viewModel.uiState.value.isMqttConnected)
        assertEquals(desktop, bindings.current())

        AndroidSyncRuntime.running()
        advanceUntilIdle()
        AndroidSyncRuntime.idle()
        discovery.emit(PairingEndpoint("192.168.1.22", 1883))
        advanceUntilIdle()
        assertEquals(1, session.connects.size)
    }

    @Test
    fun pairingNeedsPermissionAndRevocationCancelsAnActivePairing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var exchanges = 0
        var cancelled = false
        val viewModel = SyncViewModel(
            useCase(FakeBindings(null)) {
                exchanges++
                try { awaitCancellation() } catch (_: CancellationException) {
                    // A transport may translate cancellation into a failure; it must not reach the UI.
                    cancelled = true
                    TransportResult.Failure("cancelled")
                }
            },
            FakeSyncSession(), FakeDiscovery(),
            initialLocalNetworkAccess = LocalNetworkAccess.Required,
        )
        val key = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32))
        val qr = "airmedy://pair/v1?host=192.168.1.20&port=1883&desktop_id=$DesktopId&public_key=$key"
        advanceUntilIdle()
        assertEquals(true, viewModel.acceptsQr(qr))
        viewModel.pair(qr)
        advanceUntilIdle()
        assertEquals(0, exchanges)
        assertEquals(false, viewModel.uiState.value.isPairing)

        viewModel.setLocalNetworkAccess(LocalNetworkAccess.Granted)
        viewModel.pair(qr)
        advanceUntilIdle()
        assertEquals(1, exchanges)
        assertEquals(true, viewModel.uiState.value.isPairing)

        viewModel.setLocalNetworkAccess(LocalNetworkAccess.Required)
        advanceUntilIdle()
        assertEquals(true, cancelled)
        assertEquals(false, viewModel.uiState.value.isPairing)
        assertEquals(null, viewModel.uiState.value.desktop)
        assertEquals(null, viewModel.uiState.value.failure)
    }

    private fun useCase(bindings: FakeBindings, exchange: suspend () -> TransportResult = { TransportResult.Timeout }) = MobilePairingUseCase(
        identityProvider = FakeIdentity,
        bindingStore = bindings,
        transport = object : PairingTransport {
            override suspend fun exchange(endpoint: me.misa198.airmedy.pairing.DesktopDiscovery, responseTopic: String, requestTopic: String, requestPayload: String) = exchange()
        },
        clock = object : PairingClock { override fun nowMillis() = 0L },
        ids = object : PairingIdGenerator { override fun newId() = DesktopId },
    )

    private class FakeBindings(initial: PairedDesktop?, private val events: MutableList<String>? = null) : PairingBindingStore {
        private val state = MutableStateFlow(initial)
        override val pairedDesktop: Flow<PairedDesktop?> = state
        override suspend fun current(): PairedDesktop? = state.value
        override suspend fun save(desktop: PairedDesktop) { state.value = desktop }
        override suspend fun clear() { events?.add("clear"); state.value = null }
    }

    private class FakeDiscovery : TrustedDesktopDiscovery {
        private val found = MutableSharedFlow<PairingEndpoint>()
        override val endpoints: Flow<PairingEndpoint> = found
        override val unavailableEndpoints: Flow<PairingEndpoint> = MutableSharedFlow()
        val startedDesktopIds = mutableListOf<String>()
        var active = 0
        override fun start(desktop: PairedDesktop) { startedDesktopIds += desktop.desktopId; active = 1 }
        override fun stop() { active = 0 }
        suspend fun emit(endpoint: PairingEndpoint) { found.emit(endpoint) }
    }

    private class FakeSyncSession(connected: Boolean = false) : SyncSession {
        private val state = MutableStateFlow(connected)
        override val isConnected: StateFlow<Boolean> = state.asStateFlow()
        private val endpoint = MutableStateFlow<PairingEndpoint?>(null)
        override val connectedEndpoint: StateFlow<PairingEndpoint?> = endpoint.asStateFlow()
        private val requests = MutableSharedFlow<String>()
        override val syncRequests: Flow<String> = requests
        private val reconciliationRequests = MutableSharedFlow<String>()
        override val playlistReconciliationRequests: Flow<String> = reconciliationRequests
        data class Connect(val endpoint: PairingEndpoint, val reconnect: Boolean)
        val connects = mutableListOf<Connect>()
        var stopReconnectCalls = 0
        var disconnectCalls = 0
        override fun connect(desktop: PairedDesktop, endpoint: PairingEndpoint, mobileId: String, reconnect: Boolean) { connects += Connect(endpoint, reconnect) }
        override fun stopReconnecting() { stopReconnectCalls++ }
        override suspend fun publish(topic: String, payload: String) = Unit
        override fun disconnect() { disconnectCalls++; state.value = false; endpoint.value = null }
        fun setConnected(connected: Boolean) { state.value = connected }
        fun setEndpoint(value: PairingEndpoint?) { endpoint.value = value }
        suspend fun emitRequest(value: String) { requests.emit(value) }
        suspend fun emitReconciliation(value: String) { reconciliationRequests.emit(value) }
    }

    private object FakeIdentity : PairingIdentityProvider {
        override suspend fun identity() = MobileIdentity("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "Phone", "Android", ByteArray(32))
        override suspend fun randomBytes(size: Int) = ByteArray(size)
        override suspend fun sign(input: ByteArray) = ByteArray(64)
        override suspend fun verify(publicKey: ByteArray, input: ByteArray, signature: ByteArray) = true
    }

    private companion object {
        const val DesktopId = "01234567-89ab-cdef-0123-456789abcdef"
    }
}
