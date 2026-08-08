package me.misa198.airmedy.pairing

import kotlinx.coroutines.flow.Flow

data class MobileIdentity(val id: String, val name: String, val platform: String, val publicKey: ByteArray)

object MobilePlatform {
    const val Android = "Android"
    const val IOS = "iOS"
    const val IPadOS = "iPadOS"
}

interface PairingIdentityProvider {
    suspend fun identity(): MobileIdentity
    suspend fun randomBytes(size: Int): ByteArray
    suspend fun sign(input: ByteArray): ByteArray
    suspend fun verify(publicKey: ByteArray, input: ByteArray, signature: ByteArray): Boolean
}

interface PairingBindingStore {
    val pairedDesktop: Flow<PairedDesktop?>
    suspend fun current(): PairedDesktop?
    suspend fun save(desktop: PairedDesktop)
    suspend fun clear()
}

interface PairingTransport {
    /** Subscribes before publishing and returns one response or a typed transport failure. */
    suspend fun exchange(endpoint: DesktopDiscovery, responseTopic: String, requestTopic: String, requestPayload: String): TransportResult
}

sealed interface TransportResult {
    data class Response(val payload: String) : TransportResult
    data object Timeout : TransportResult
    data class Failure(val reason: String) : TransportResult
}

interface PairingClock { fun nowMillis(): Long }
interface PairingIdGenerator { fun newId(): String }

class MobilePairingUseCase(
    private val identityProvider: PairingIdentityProvider,
    private val bindingStore: PairingBindingStore,
    private val transport: PairingTransport,
    private val clock: PairingClock,
    private val ids: PairingIdGenerator,
) {
    val pairedDesktop: Flow<PairedDesktop?> = bindingStore.pairedDesktop

    suspend fun mobileId(): String = identityProvider.identity().id

    suspend fun pair(qrValue: String): PairingResult {
        if (bindingStore.current() != null) return PairingResult.Failed(PairingFailure.AlreadyPaired)
        val discovery = PairingQrParser.parse(qrValue).getOrElse { return PairingResult.Failed(PairingFailure.InvalidQr(it.message ?: "Invalid pairing QR")) }
        val mobile = identityProvider.identity()
        val requestId = ids.newId()
        val nonce = identityProvider.randomBytes(32)
        val issuedAt = clock.nowMillis()
        val signingInput = PairingProtocol.requestSigningInput(discovery, mobile, requestId, nonce, issuedAt)
        val signature = identityProvider.sign(signingInput)
        if (signature.size != 64) return PairingResult.Failed(PairingFailure.Transport("Unable to sign pairing request"))
        val request = PairingProtocol.createRequest(discovery, mobile, requestId, nonce, issuedAt, signature)
        val responseTopic = "airmedy/pairing/v1/${discovery.desktopId}/response/${mobile.id}"
        val requestTopic = "airmedy/pairing/v1/${discovery.desktopId}/request"
        return when (val exchange = transport.exchange(discovery, responseTopic, requestTopic, PairingProtocol.encodeRequest(request))) {
            TransportResult.Timeout -> PairingResult.Failed(PairingFailure.TimedOut)
            is TransportResult.Failure -> PairingResult.Failed(PairingFailure.Transport(exchange.reason))
            is TransportResult.Response -> validateResponse(exchange.payload, discovery, request, mobile)
        }
    }

    suspend fun unpair() = bindingStore.clear()

    private suspend fun validateResponse(payload: String, discovery: DesktopDiscovery, request: HandshakeRequest, mobile: MobileIdentity): PairingResult {
        val response = PairingProtocol.parseResponse(payload) ?: return PairingResult.Failed(PairingFailure.InvalidResponse)
        if (response.version != ProtocolVersion || response.type != ResponseType || response.requestId != request.requestId || response.desktopId != discovery.desktopId || response.decision !in setOf("approved", "rejected", "expired") || kotlin.math.abs(clock.nowMillis() - response.issuedAt) > 5 * 60 * 1000L) return PairingResult.Failed(PairingFailure.InvalidResponse)
        val signature = Base64Url.decodeExact(response.signature, 64) ?: return PairingResult.Failed(PairingFailure.InvalidResponse)
        val signingInput = runCatching { PairingProtocol.responseSigningInput(response, request, discovery.desktopPublicKey) }.getOrNull() ?: return PairingResult.Failed(PairingFailure.InvalidResponse)
        if (!identityProvider.verify(discovery.desktopPublicKey, signingInput, signature)) return PairingResult.Failed(PairingFailure.InvalidResponse)
        return when (response.decision) {
            "approved" -> PairedDesktop(
                desktopId = discovery.desktopId,
                displayName = discovery.displayName,
                publicKey = discovery.desktopPublicKey,
                host = discovery.host,
                port = discovery.port,
            ).also { bindingStore.save(it) }.let(PairingResult::Paired)
            "rejected" -> PairingResult.Failed(PairingFailure.Rejected)
            else -> PairingResult.Failed(PairingFailure.Expired)
        }
    }
}
