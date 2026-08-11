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

    private fun useCase(bindings: FakeBindings) = MobilePairingUseCase(
        identityProvider = FakeIdentity,
        bindingStore = bindings,
        transport = object : PairingTransport {
            override suspend fun exchange(endpoint: me.misa198.airmedy.pairing.DesktopDiscovery, responseTopic: String, requestTopic: String, requestPayload: String) = TransportResult.Timeout
        },
        clock = object : PairingClock { override fun nowMillis() = 0L },
        ids = object : PairingIdGenerator { override fun newId() = DesktopId },
    )

    private class FakeBindings(initial: PairedDesktop?) : PairingBindingStore {
        private val state = MutableStateFlow(initial)
        override val pairedDesktop: Flow<PairedDesktop?> = state
        override suspend fun current(): PairedDesktop? = state.value
        override suspend fun save(desktop: PairedDesktop) { state.value = desktop }
        override suspend fun clear() { state.value = null }
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
        data class Connect(val endpoint: PairingEndpoint, val reconnect: Boolean)
        val connects = mutableListOf<Connect>()
        var stopReconnectCalls = 0
        override fun connect(desktop: PairedDesktop, endpoint: PairingEndpoint, mobileId: String, reconnect: Boolean) { connects += Connect(endpoint, reconnect) }
        override fun stopReconnecting() { stopReconnectCalls++ }
        override suspend fun publish(topic: String, payload: String) = Unit
        override fun disconnect() = Unit
        fun setConnected(connected: Boolean) { state.value = connected }
        fun setEndpoint(value: PairingEndpoint?) { endpoint.value = value }
        suspend fun emitRequest(value: String) { requests.emit(value) }
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
