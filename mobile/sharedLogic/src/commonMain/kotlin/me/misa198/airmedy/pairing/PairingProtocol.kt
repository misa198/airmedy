package me.misa198.airmedy.pairing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val ProtocolVersion = 1
internal const val RequestType = "pair.request"
internal const val ResponseType = "pair.response"
private const val RequestContext = "airmedy.mobile-pairing.request.v1"
private const val ResponseContext = "airmedy.mobile-pairing.response.v1"

data class DesktopDiscovery(
    val host: String,
    val port: Int,
    val desktopId: String,
    val desktopPublicKey: ByteArray,
    val displayName: String,
)

data class PairedDesktop(
    val desktopId: String,
    val displayName: String,
    val publicKey: ByteArray,
    /** MQTT endpoint retained from the verified QR payload for the sync session. */
    val host: String? = null,
    val port: Int? = null,
)

/** A transient MQTT route obtained from a trusted desktop's mDNS broadcast. */
data class PairingEndpoint(
    val host: String,
    val port: Int,
)

/** Raw DNS-SD data needed to validate an already resolved pairing broadcast. */
data class PairingBroadcastRecord(
    val srvPort: Int,
    val txt: Map<String, String>,
)

/**
 * Validates the endpoint-only discovery record. It deliberately has no access
 * to desktop public keys: mDNS may locate only a desktop already trusted by ID.
 */
object PairingBroadcastResolver {
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val decimalPort = Regex("^[0-9]+$")

    fun resolve(record: PairingBroadcastRecord, trustedDesktopId: String): PairingEndpoint? {
        val host = record.txt["ip"] ?: return null
        val advertisedPort = record.txt["port"] ?: return null
        val deviceId = record.txt["device_id"] ?: return null
        val port = advertisedPort.takeIf(decimalPort::matches)?.toIntOrNull() ?: return null
        if (!isIpv4(host) || port !in 1..65535 || record.srvPort !in 1..65535 ||
            port != record.srvPort || !uuid.matches(deviceId) || deviceId != trustedDesktopId
        ) return null
        return PairingEndpoint(host, port)
    }

    private fun isIpv4(value: String): Boolean = value.split('.').let { parts ->
        parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) && it.toIntOrNull() in 0..255 }
    }
}

sealed interface PairingFailure {
    data object AlreadyPaired : PairingFailure
    data class InvalidQr(val reason: String) : PairingFailure
    data class Transport(val reason: String) : PairingFailure
    data object TimedOut : PairingFailure
    data object Rejected : PairingFailure
    data object Expired : PairingFailure
    data object InvalidResponse : PairingFailure
}

sealed interface PairingResult {
    data class Paired(val desktop: PairedDesktop) : PairingResult
    data class Failed(val failure: PairingFailure) : PairingResult
}

@Serializable
internal data class HandshakeRequest(
    val version: Int,
    val type: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("desktop_id") val desktopId: String,
    @SerialName("desktop_public_key") val desktopPublicKey: String,
    @SerialName("mobile_id") val mobileId: String,
    @SerialName("mobile_name") val mobileName: String,
    @SerialName("mobile_platform") val mobilePlatform: String,
    @SerialName("mobile_public_key") val mobilePublicKey: String,
    val nonce: String,
    @SerialName("issued_at") val issuedAt: Long,
    val signature: String,
)

@Serializable
internal data class HandshakeResponse(
    val version: Int,
    val type: String,
    @SerialName("request_id") val requestId: String,
    val decision: String,
    @SerialName("desktop_id") val desktopId: String,
    @SerialName("desktop_nonce") val desktopNonce: String,
    @SerialName("issued_at") val issuedAt: Long,
    val signature: String,
)

internal object PairingProtocol {
    val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }

    fun createRequest(
        discovery: DesktopDiscovery,
        mobile: MobileIdentity,
        requestId: String,
        nonce: ByteArray,
        issuedAt: Long,
        signature: ByteArray,
    ): HandshakeRequest = HandshakeRequest(
        version = ProtocolVersion,
        type = RequestType,
        requestId = requestId,
        desktopId = discovery.desktopId,
        desktopPublicKey = Base64Url.encode(discovery.desktopPublicKey),
        mobileId = mobile.id,
        mobileName = mobile.name,
        mobilePlatform = mobile.platform,
        mobilePublicKey = Base64Url.encode(mobile.publicKey),
        nonce = Base64Url.encode(nonce),
        issuedAt = issuedAt,
        signature = Base64Url.encode(signature),
    )

    fun requestSigningInput(
        discovery: DesktopDiscovery,
        mobile: MobileIdentity,
        requestId: String,
        nonce: ByteArray,
        issuedAt: Long,
    ): ByteArray = SigningBuffer()
        .string(RequestContext)
        .byte(ProtocolVersion)
        .string(RequestType)
        .string(requestId)
        .string(discovery.desktopId)
        .bytes(discovery.desktopPublicKey)
        .string(mobile.id)
        .string(mobile.name)
        .string(mobile.platform)
        .bytes(mobile.publicKey)
        .bytes(nonce)
        .long(issuedAt)
        .build()

    fun responseSigningInput(response: HandshakeResponse, request: HandshakeRequest, desktopPublicKey: ByteArray): ByteArray {
        val mobileKey = Base64Url.decodeExact(request.mobilePublicKey, 32) ?: error("invalid mobile key")
        val requestNonce = Base64Url.decodeExact(request.nonce, 32) ?: error("invalid request nonce")
        val desktopNonce = Base64Url.decodeExact(response.desktopNonce, 32) ?: error("invalid desktop nonce")
        return SigningBuffer()
            .string(ResponseContext)
            .byte(ProtocolVersion)
            .string(ResponseType)
            .string(response.requestId)
            .string(response.decision)
            .string(response.desktopId)
            .bytes(desktopPublicKey)
            .string(request.mobileId)
            .bytes(mobileKey)
            .bytes(requestNonce)
            .bytes(desktopNonce)
            .long(response.issuedAt)
            .build()
    }

    fun parseResponse(value: String): HandshakeResponse? = runCatching {
        json.decodeFromString<HandshakeResponse>(value)
    }.getOrNull()

    fun encodeRequest(value: HandshakeRequest): String = json.encodeToString(HandshakeRequest.serializer(), value)
}

object PairingQrParser {
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    fun parse(raw: String): Result<DesktopDiscovery> = runCatching {
        require(raw.startsWith("airmedy://pair/v1?")) { "Unsupported pairing QR" }
        val parameters = mutableMapOf<String, String>()
        raw.substringAfter('?').split('&').forEach { item ->
            val index = item.indexOf('=')
            require(index > 0) { "Malformed pairing QR" }
            val key = percentDecode(item.substring(0, index))
            require(parameters.put(key, percentDecode(item.substring(index + 1))) == null) { "Duplicate pairing QR field" }
        }
        require(parameters.keys.all { it in setOf("host", "port", "desktop_id", "public_key", "desktop_name") }) { "Unsupported pairing QR field" }
        val host = parameters.required("host")
        require(isIpv4(host)) { "Invalid desktop address" }
        val port = parameters.required("port").toIntOrNull()
        require(port != null && port in 1..65535) { "Invalid desktop port" }
        val desktopId = parameters.required("desktop_id")
        require(uuid.matches(desktopId)) { "Invalid desktop identity" }
        val publicKey = Base64Url.decodeExact(parameters.required("public_key"), 32)
            ?: error("Invalid desktop public key")
        val displayName = parameters["desktop_name"]?.also { validateDisplay(it, 1, 64) } ?: "Airmedy Desktop"
        DesktopDiscovery(host, port, desktopId, publicKey, displayName)
    }

    private fun Map<String, String>.required(key: String): String = this[key] ?: error("Missing $key")

    private fun isIpv4(value: String): Boolean = value.split('.').let { parts ->
        parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) && it.toIntOrNull() in 0..255 }
    }

    private fun validateDisplay(value: String, min: Int, max: Int) {
        val size = value.encodeToByteArray().size
        require(size in min..max && !value.contains('\u0000') && !value.contains('\r') && !value.contains('\n')) { "Invalid desktop name" }
    }

    private fun percentDecode(value: String): String {
        val bytes = mutableListOf<Byte>()
        var index = 0
        while (index < value.length) {
            if (value[index] == '%') {
                require(index + 2 < value.length) { "Invalid URL encoding" }
                bytes += value.substring(index + 1, index + 3).toInt(16).toByte()
                index += 3
            } else {
                bytes += value[index].toString().encodeToByteArray().toList()
                index++
            }
        }
        return bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
    }
}

internal object Base64Url {
    private const val Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 4 + 2) / 3)
        var index = 0
        while (index + 2 < bytes.size) {
            val value = ((bytes[index].toInt() and 0xff) shl 16) or
                ((bytes[index + 1].toInt() and 0xff) shl 8) or
                (bytes[index + 2].toInt() and 0xff)
            out.append(Alphabet[(value ushr 18) and 63]).append(Alphabet[(value ushr 12) and 63]).append(Alphabet[(value ushr 6) and 63]).append(Alphabet[value and 63])
            index += 3
        }
        when (bytes.size - index) {
            1 -> { val value = (bytes[index].toInt() and 0xff) shl 16; out.append(Alphabet[(value ushr 18) and 63]).append(Alphabet[(value ushr 12) and 63]) }
            2 -> { val value = ((bytes[index].toInt() and 0xff) shl 16) or ((bytes[index + 1].toInt() and 0xff) shl 8); out.append(Alphabet[(value ushr 18) and 63]).append(Alphabet[(value ushr 12) and 63]).append(Alphabet[(value ushr 6) and 63]) }
        }
        return out.toString()
    }

    fun decodeExact(value: String, length: Int): ByteArray? = runCatching { decode(value) }.getOrNull()?.takeIf { it.size == length }

    private fun decode(value: String): ByteArray {
        require(value.none { it == '=' || Alphabet.indexOf(it) < 0 }) { "Invalid base64url" }
        require(value.length % 4 != 1) { "Invalid base64url" }
        val out = mutableListOf<Byte>(); var index = 0
        while (index + 3 < value.length) {
            val number = (Alphabet.indexOf(value[index]) shl 18) or (Alphabet.indexOf(value[index + 1]) shl 12) or (Alphabet.indexOf(value[index + 2]) shl 6) or Alphabet.indexOf(value[index + 3])
            out += (number ushr 16).toByte(); out += (number ushr 8).toByte(); out += number.toByte(); index += 4
        }
        when (value.length - index) {
            2 -> { val number = (Alphabet.indexOf(value[index]) shl 18) or (Alphabet.indexOf(value[index + 1]) shl 12); out += (number ushr 16).toByte() }
            3 -> { val number = (Alphabet.indexOf(value[index]) shl 18) or (Alphabet.indexOf(value[index + 1]) shl 12) or (Alphabet.indexOf(value[index + 2]) shl 6); out += (number ushr 16).toByte(); out += (number ushr 8).toByte() }
        }
        return out.toByteArray()
    }
}

private class SigningBuffer {
    private val data = mutableListOf<Byte>()
    fun byte(value: Int) = apply { data += value.toByte() }
    fun string(value: String) = bytes(value.encodeToByteArray())
    fun bytes(value: ByteArray) = apply { require(value.size <= 0xffff); data += (value.size ushr 8).toByte(); data += value.size.toByte(); data += value.toList() }
    fun long(value: Long) = apply { for (shift in 56 downTo 0 step 8) data += (value ushr shift).toByte() }
    fun build(): ByteArray = data.toByteArray()
}
