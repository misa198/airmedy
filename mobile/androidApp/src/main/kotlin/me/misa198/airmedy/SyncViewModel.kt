package me.misa198.airmedy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.misa198.airmedy.pairing.PairingFailure
import me.misa198.airmedy.pairing.PairingEndpoint
import me.misa198.airmedy.pairing.PairingQrParser
import me.misa198.airmedy.pairing.PairingResult
import me.misa198.airmedy.pairing.MobilePairingUseCase
import me.misa198.airmedy.pairing.PairedDesktop
import me.misa198.airmedy.pairing.SyncSession
import me.misa198.airmedy.pairing.TrustedDesktopDiscovery
import me.misa198.airmedy.sync.AndroidSyncState
import me.misa198.airmedy.sync.AndroidSyncRuntime

import android.util.Log

data class SyncUiState(
    val desktop: PairedDesktop? = null,
    val isMqttConnected: Boolean = false,
    val isPairing: Boolean = false,
    val failure: PairingFailure? = null,
    val librarySync: AndroidSyncState = AndroidSyncState.Idle,
)

class SyncViewModel(
    private val pairing: MobilePairingUseCase,
    private val mqttSession: SyncSession,
    private val discovery: TrustedDesktopDiscovery,
    private val onSyncRequest: (String, PairingEndpoint, SyncSession) -> Unit = { _, _, _ -> },
    private val onBeforeUnpair: suspend () -> Unit = {},
    private val isForegroundSyncRunning: () -> Boolean = { AndroidSyncRuntime.state.value is AndroidSyncState.Running },
) : ViewModel() {
    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private var syncScreenVisible = false
    private var discoverySession = false
    private var discoveryEndpoint: PairingEndpoint? = null
    private var syncWasRunning = false

    init {
        viewModelScope.launch {
            pairing.pairedDesktop.collectLatest { desktop ->
                Log.d(LogTag, "Paired desktop updated: ${desktop?.desktopId} (${desktop?.displayName})")
                if (desktop == null) {
                    discoverySession = false
                    discoveryEndpoint = null
                    discovery.stop()
                    mqttSession.disconnect()
                } else if (!isForegroundSyncRunning()) {
                    val host = desktop.host
                    val port = desktop.port
                    if (host != null && port != null) {
                        discoverySession = false
                        discoveryEndpoint = null
                        mqttSession.connect(desktop, PairingEndpoint(host, port), pairing.mobileId(), reconnect = false)
                    }
                }
                _uiState.update { it.copy(desktop = desktop, isPairing = false, failure = null) }
                updateDiscovery()
            }
        }
        viewModelScope.launch {
            mqttSession.syncRequests.collectLatest { request ->
                Log.i(LogTag, "Handling sync request in ViewModel")
                mqttSession.connectedEndpoint.value?.let { endpoint -> onSyncRequest(request, endpoint, mqttSession) }
            }
        }
        viewModelScope.launch {
            AndroidSyncRuntime.state.collectLatest { state ->
                Log.d(LogTag, "AndroidSyncRuntime state changed to: $state")
                _uiState.update { it.copy(librarySync = state) }
                if (state is AndroidSyncState.Running) {
                    syncWasRunning = true
                } else if (syncWasRunning) {
                    syncWasRunning = false
                    _uiState.value.desktop?.let { desktop ->
                        desktop.host?.let { host -> desktop.port?.let { port ->
                            mqttSession.connect(desktop, PairingEndpoint(host, port), pairing.mobileId(), reconnect = false)
                        } }
                    }
                }
                updateDiscovery()
            }
        }
        viewModelScope.launch {
            mqttSession.isConnected.collectLatest { connected ->
                Log.d(LogTag, "MQTT isConnected: $connected")
                _uiState.update { it.copy(isMqttConnected = connected) }
                updateDiscovery()
            }
        }
        viewModelScope.launch {
            discovery.endpoints.collectLatest { endpoint ->
                val desktop = _uiState.value.desktop ?: return@collectLatest
                if (!syncScreenVisible || _uiState.value.isMqttConnected) return@collectLatest
                Log.i(LogTag, "Discovered trusted desktop endpoint via mDNS: $endpoint")
                discoverySession = true
                discoveryEndpoint = endpoint
                mqttSession.connect(desktop, endpoint, pairing.mobileId(), reconnect = true)
            }
        }
        viewModelScope.launch {
            discovery.unavailableEndpoints.collectLatest { endpoint ->
                if (discoverySession && endpoint == discoveryEndpoint) {
                    Log.w(LogTag, "mDNS endpoint became unavailable: $endpoint")
                    mqttSession.stopReconnecting()
                }
            }
        }
    }

    private companion object {
        private const val LogTag = "AirmedySyncViewModel"
    }

    fun acceptsQr(raw: String): Boolean = PairingQrParser.parse(raw).isSuccess

    fun pair(raw: String) {
        if (_uiState.value.desktop != null || _uiState.value.isPairing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPairing = true, failure = null) }
            when (val result = pairing.pair(raw)) {
                is PairingResult.Paired -> _uiState.update { it.copy(desktop = result.desktop, isPairing = false) }
                is PairingResult.Failed -> _uiState.update { it.copy(isPairing = false, failure = result.failure) }
            }
        }
    }

    fun clearFailure() = _uiState.update { it.copy(failure = null) }

    fun onSyncScreenVisible() {
        syncScreenVisible = true
        updateDiscovery()
    }

    fun onSyncScreenHidden() {
        syncScreenVisible = false
        updateDiscovery()
    }

    fun unpair() {
        viewModelScope.launch { onBeforeUnpair(); pairing.unpair() }
    }

    override fun onCleared() {
        discovery.stop()
        if (_uiState.value.librarySync !is AndroidSyncState.Running) mqttSession.disconnect()
    }

    private fun updateDiscovery() {
        val desktop = _uiState.value.desktop
        if (syncScreenVisible && desktop != null && !_uiState.value.isMqttConnected && _uiState.value.librarySync !is AndroidSyncState.Running) {
            discovery.start(desktop)
        } else {
            discovery.stop()
            if (discoverySession) mqttSession.stopReconnecting()
        }
    }

    class Factory(
        private val pairing: MobilePairingUseCase,
        private val mqttSession: SyncSession,
        private val discovery: TrustedDesktopDiscovery,
        private val onSyncRequest: (String, PairingEndpoint, SyncSession) -> Unit = { _, _, _ -> },
        private val onBeforeUnpair: suspend () -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(SyncViewModel::class.java))
            return SyncViewModel(pairing, mqttSession, discovery, onSyncRequest, onBeforeUnpair) as T
        }
    }
}
