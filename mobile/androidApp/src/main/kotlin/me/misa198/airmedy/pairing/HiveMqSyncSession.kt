package me.misa198.airmedy.pairing

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
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
    private val _connectedEndpoint = MutableStateFlow<PairingEndpoint?>(null)
    override val connectedEndpoint: StateFlow<PairingEndpoint?> = _connectedEndpoint
    private val _syncRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val syncRequests: Flow<String> = _syncRequests
    private val _playlistReconciliationRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val playlistReconciliationRequests: Flow<String> = _playlistReconciliationRequests

    private var sessionJob: Job? = null
    private var client: Mqtt3AsyncClient? = null
    private var endpoint: PairingEndpoint? = null
    @Volatile private var reconnectEnabled = false

    override fun connect(desktop: PairedDesktop, endpoint: PairingEndpoint, mobileId: String, reconnect: Boolean) {
        if (this.endpoint == endpoint && sessionJob?.isActive == true) {
            reconnectEnabled = reconnectEnabled || reconnect
            Log.d(LogTag, "Already connected/connecting to $endpoint")
            return
        }
        disconnect()
        this.endpoint = endpoint
        reconnectEnabled = reconnect
        Log.i(LogTag, "Connecting to desktop ${desktop.desktopId} at ${endpoint.host}:${endpoint.port} (reconnect=$reconnect)")
        sessionJob = scope.launch {
            val mqttClient = MqttClient.builder()
                .useMqttVersion3()
                .identifier("airmedy-sync-${desktop.desktopId}-$mobileId")
                .serverHost(endpoint.host)
                .serverPort(endpoint.port)
                .buildAsync()
            client = mqttClient
            while (isActive) {
                val connectResult = runCatching { mqttClient.connectWith().cleanSession(true).send().await() }
                if (connectResult.isFailure) {
                    Log.w(LogTag, "MQTT connect failed: ${connectResult.exceptionOrNull()?.message}")
                }
                _isConnected.value = mqttClient.state.isConnected
                _connectedEndpoint.value = if (mqttClient.state.isConnected) endpoint else null
                if (mqttClient.state.isConnected) {
                    Log.i(LogTag, "MQTT connected to ${endpoint.host}:${endpoint.port}")
                    runCatching {
                        val topic = "airmedy/library-sync/v1/${desktop.desktopId}/$mobileId/request"
                        mqttClient.subscribeWith()
                            .topicFilter(topic)
                            .qos(MqttQos.AT_LEAST_ONCE)
                            .callback { publish ->
                                publish.payload.orElse(null)?.let { payload ->
                                    val json = java.nio.charset.StandardCharsets.UTF_8.decode(payload).toString()
                                    Log.d(LogTag, "Received sync request on topic $topic: $json")
                                    _syncRequests.tryEmit(json)
                                }
                            }
                            .send().await()
                        Log.d(LogTag, "Subscribed to topic $topic")
                        val playlistTopic = "airmedy/playlist-sync/v1/${desktop.desktopId}/$mobileId/request"
                        mqttClient.subscribeWith()
                            .topicFilter(playlistTopic)
                            .qos(MqttQos.AT_LEAST_ONCE)
                            .callback { publish ->
                                publish.payload.orElse(null)?.let { bytes ->
                                    _playlistReconciliationRequests.tryEmit(java.nio.charset.StandardCharsets.UTF_8.decode(bytes).toString())
                                }
                            }
                            .send().await()
                    }.onFailure { err ->
                        Log.e(LogTag, "Failed to subscribe to sync request topic", err)
                    }
                }
                while (isActive && mqttClient.state.isConnected) {
                    delay(CONNECTION_POLL_INTERVAL_MS)
                }
                Log.w(LogTag, "MQTT session disconnected")
                _isConnected.value = false
                _connectedEndpoint.value = null
                if (!reconnectEnabled) return@launch
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    override fun stopReconnecting() {
        Log.d(LogTag, "Stopping MQTT reconnection")
        reconnectEnabled = false
    }

    override suspend fun publish(topic: String, payload: String) {
        Log.d(LogTag, "Publishing to topic $topic: $payload")
        val mqttClient = client ?: error("Sync MQTT is not connected")
        mqttClient.publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(false)
            .payload(payload.toByteArray(Charsets.UTF_8))
            .send()
            .await()
    }

    override fun disconnect() {
        if (client != null || sessionJob != null) {
            Log.i(LogTag, "Disconnecting MQTT session")
        }
        sessionJob?.cancel()
        sessionJob = null
        endpoint = null
        reconnectEnabled = false
        val mqttClient = client
        client = null
        _isConnected.value = false
        _connectedEndpoint.value = null
        if (mqttClient != null) {
            scope.launch { runCatching { mqttClient.disconnect().await() } }
        }
    }

    companion object {
        private const val LogTag = "AirmedySyncSession"
        private const val CONNECTION_POLL_INTERVAL_MS = 1_000L
        private const val RECONNECT_DELAY_MS = 3_000L
    }
}
