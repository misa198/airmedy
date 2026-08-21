package me.misa198.airmedy.pairing

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import me.misa198.airmedy.pairing.DesktopDiscovery
import me.misa198.airmedy.pairing.PairingTransport
import me.misa198.airmedy.pairing.TransportResult

class HiveMqPairingTransport : PairingTransport {
    override suspend fun exchange(
        endpoint: DesktopDiscovery,
        responseTopic: String,
        requestTopic: String,
        requestPayload: String,
    ): TransportResult {
        val client = MqttClient.builder()
            .useMqttVersion3()
            .identifier("airmedy-pairing-${UUID.randomUUID()}")
            .serverHost(endpoint.host)
            .serverPort(endpoint.port)
            .buildAsync()
        return try {
            client.connectWith().cleanSession(true).send().await()
            val response = CompletableFuture<String>()
            client.subscribeWith()
                .topicFilter(responseTopic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback { publish ->
                    publish.payload.orElse(null)?.let { payload ->
                        response.complete(StandardCharsets.UTF_8.decode(payload).toString())
                    }
                }
                .send().await()
            client.publishWith()
                .topic(requestTopic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .retain(false)
                .payload(requestPayload.toByteArray(StandardCharsets.UTF_8))
                .send().await()
            withTimeoutOrNull(135_000) { response.await() }?.let(TransportResult::Response) ?: TransportResult.Timeout
        } catch (error: Exception) {
            TransportResult.Failure(error.message ?: "Unable to connect to desktop")
        } finally {
            runCatching { client.disconnect().await() }
        }
    }
}

internal suspend fun <T> CompletableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    whenComplete { value, error ->
        if (error != null) continuation.resumeWith(Result.failure(error)) else continuation.resume(value)
    }
    continuation.invokeOnCancellation { cancel(true) }
}
