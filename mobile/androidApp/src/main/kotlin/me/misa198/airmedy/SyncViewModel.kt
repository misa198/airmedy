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
import me.misa198.airmedy.pairing.PairingQrParser
import me.misa198.airmedy.pairing.PairingResult
import me.misa198.airmedy.pairing.MobilePairingUseCase
import me.misa198.airmedy.pairing.PairedDesktop
import me.misa198.airmedy.pairing.HiveMqSyncSession

data class SyncUiState(
    val desktop: PairedDesktop? = null,
    val isMqttConnected: Boolean = false,
    val isPairing: Boolean = false,
    val failure: PairingFailure? = null,
)

class SyncViewModel(
    private val pairing: MobilePairingUseCase,
    private val mqttSession: HiveMqSyncSession,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            pairing.pairedDesktop.collectLatest { desktop ->
                if (desktop?.host == null || desktop.port == null) mqttSession.disconnect() else mqttSession.connect(desktop, pairing.mobileId())
                _uiState.update { it.copy(desktop = desktop, isPairing = false, failure = null) }
            }
        }
        viewModelScope.launch {
            mqttSession.isConnected.collectLatest { connected ->
                _uiState.update { it.copy(isMqttConnected = connected) }
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

    fun unpair() {
        viewModelScope.launch { pairing.unpair() }
    }

    override fun onCleared() {
        mqttSession.disconnect()
    }

    class Factory(
        private val pairing: MobilePairingUseCase,
        private val mqttSession: HiveMqSyncSession,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(SyncViewModel::class.java))
            return SyncViewModel(pairing, mqttSession) as T
        }
    }
}
