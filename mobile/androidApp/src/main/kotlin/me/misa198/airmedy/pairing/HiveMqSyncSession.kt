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
import me.misa198.airmedy.pairing.PairedDesktop

/**
 * Long-lived mobile MQTT session. It is intentionally transport-only: future
 * sync features can add topic subscriptions and publishing here without making
 * UI state or pairing validation depend on HiveMQ APIs.
 */
class HiveMqSyncSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var sessionJob: Job? = null
    private var client: Mqtt3AsyncClient? = null
    private var endpoint: PairedDesktop? = null

    fun connect(desktop: PairedDesktop, mobileID: String) {
        val host = desktop.host ?: return disconnect()
        val port = desktop.port ?: return disconnect()
        if (endpoint == desktop && sessionJob?.isActive == true) return
        disconnect()
        endpoint = desktop
        sessionJob = scope.launch {
            val mqttClient = MqttClient.builder()
                .useMqttVersion3()
                .identifier("airmedy-sync-${desktop.desktopId}-$mobileID")
                .serverHost(host)
                .serverPort(port)
                .buildAsync()
            client = mqttClient
            while (isActive) {
                runCatching { mqttClient.connectWith().cleanSession(true).send().await() }
                _isConnected.value = mqttClient.state.isConnected
                while (isActive && mqttClient.state.isConnected) {
                    delay(CONNECTION_POLL_INTERVAL_MS)
                }
                _isConnected.value = false
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        endpoint = null
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
