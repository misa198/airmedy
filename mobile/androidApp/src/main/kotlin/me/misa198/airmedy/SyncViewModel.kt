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

data class SyncUiState(
    val desktop: PairedDesktop? = null,
    val isMqttConnected: Boolean = false,
    val isPairing: Boolean = false,
    val failure: PairingFailure? = null,
)

class SyncViewModel(
    private val pairing: MobilePairingUseCase,
    private val mqttSession: SyncSession,
    private val discovery: TrustedDesktopDiscovery,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private var syncScreenVisible = false
    private var discoverySession = false
    private var discoveryEndpoint: PairingEndpoint? = null

    init {
        viewModelScope.launch {
            pairing.pairedDesktop.collectLatest { desktop ->
                if (desktop == null) {
                    discoverySession = false
                    discoveryEndpoint = null
                    discovery.stop()
                    mqttSession.disconnect()
                } else {
                    val host = desktop.host
                    val port = desktop.port
                    if (host != null && port != null) {
                        discoverySession = false
                        discoveryEndpoint = null
                        // The QR route gets one opportunistic connection on app start. Repeated
                        // reconnects are reserved for a foreground Sync Settings discovery.
                        mqttSession.connect(desktop, PairingEndpoint(host, port), pairing.mobileId(), reconnect = false)
                    }
                }
                _uiState.update { it.copy(desktop = desktop, isPairing = false, failure = null) }
                updateDiscovery()
            }
        }
        viewModelScope.launch {
            mqttSession.isConnected.collectLatest { connected ->
                _uiState.update { it.copy(isMqttConnected = connected) }
                updateDiscovery()
            }
        }
        viewModelScope.launch {
            discovery.endpoints.collectLatest { endpoint ->
                val desktop = _uiState.value.desktop ?: return@collectLatest
                if (!syncScreenVisible || _uiState.value.isMqttConnected) return@collectLatest
                discoverySession = true
                discoveryEndpoint = endpoint
                mqttSession.connect(desktop, endpoint, pairing.mobileId(), reconnect = true)
            }
        }
        viewModelScope.launch {
            discovery.unavailableEndpoints.collectLatest { endpoint ->
                if (discoverySession && endpoint == discoveryEndpoint) mqttSession.stopReconnecting()
            }
        }
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
        viewModelScope.launch { pairing.unpair() }
    }

    override fun onCleared() {
        discovery.stop()
        mqttSession.disconnect()
    }

    private fun updateDiscovery() {
        val desktop = _uiState.value.desktop
        if (syncScreenVisible && desktop != null && !_uiState.value.isMqttConnected) {
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(SyncViewModel::class.java))
            return SyncViewModel(pairing, mqttSession, discovery) as T
        }
    }
}
