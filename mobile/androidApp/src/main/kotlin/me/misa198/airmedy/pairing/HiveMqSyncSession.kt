package me.misa198.airmedy.pairing

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.misa198.airmedy.pairing.PairingEndpoint
import me.misa198.airmedy.pairing.PairedDesktop

/**
 * Long-lived mobile MQTT session. It is intentionally transport-only: future
 * sync features can add topic subscriptions and publishing here without making
 * UI state or pairing validation depend on HiveMQ APIs.
 */
class HiveMqSyncSession : SyncSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected

    private var sessionJob: Job? = null
    private var client: Mqtt3AsyncClient? = null
    private var endpoint: PairingEndpoint? = null
    @Volatile private var reconnectEnabled = false

    override fun connect(desktop: PairedDesktop, endpoint: PairingEndpoint, mobileId: String, reconnect: Boolean) {
        if (this.endpoint == endpoint && sessionJob?.isActive == true) {
            reconnectEnabled = reconnectEnabled || reconnect
            return
        }
        disconnect()
        this.endpoint = endpoint
        reconnectEnabled = reconnect
        sessionJob = scope.launch {
            val mqttClient = MqttClient.builder()
                .useMqttVersion3()
                .identifier("airmedy-sync-${desktop.desktopId}-$mobileId")
                .serverHost(endpoint.host)
                .serverPort(endpoint.port)
                .buildAsync()
            client = mqttClient
            while (isActive) {
                runCatching { mqttClient.connectWith().cleanSession(true).send().await() }
                _isConnected.value = mqttClient.state.isConnected
                while (isActive && mqttClient.state.isConnected) {
                    delay(CONNECTION_POLL_INTERVAL_MS)
                }
                _isConnected.value = false
                if (!reconnectEnabled) return@launch
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    override fun stopReconnecting() {
        reconnectEnabled = false
    }

    override fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        endpoint = null
        reconnectEnabled = false
        val mqttClient = client
        client = null
        _isConnected.value = false
        if (mqttClient != null) {
            scope.launch { runCatching { mqttClient.disconnect().await() } }
        }
    }

    companion object {
        private const val CONNECTION_POLL_INTERVAL_MS = 1_000L
        private const val RECONNECT_DELAY_MS = 3_000L
    }
}
