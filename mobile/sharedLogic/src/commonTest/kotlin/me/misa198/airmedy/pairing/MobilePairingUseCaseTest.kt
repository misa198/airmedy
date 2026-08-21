package me.misa198.airmedy.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class MobilePairingUseCaseTest {
    @Test
    fun parsesDesktopNameAndFallsBackForLegacyQr() {
        val key = Base64Url.encode(ByteArray(32) { it.toByte() })
        val named = PairingQrParser.parse("airmedy://pair/v1?host=192.168.1.8&port=1883&desktop_id=01234567-89ab-cdef-0123-456789abcdef&public_key=$key&desktop_name=Living%20Room")
        assertEquals("Living Room", named.getOrThrow().displayName)
        val legacy = PairingQrParser.parse("airmedy://pair/v1?host=192.168.1.8&port=1883&desktop_id=01234567-89ab-cdef-0123-456789abcdef&public_key=$key")
        assertEquals("Airmedy Desktop", legacy.getOrThrow().displayName)
    }

    @Test
    fun base64UrlUsesRawUnpaddedEncoding() {
        assertEquals("_w", Base64Url.encode(byteArrayOf(0xff.toByte())))
        assertEquals("__4", Base64Url.encode(byteArrayOf(0xff.toByte(), 0xfe.toByte())))
    }

    @Test
    fun requestSignsAndPublishesToTheV1Topics() = runTest {
        val bindings = FakeBindings()
        val transport = FakeTransport(TransportResult.Timeout)
        val useCase = MobilePairingUseCase(FakeIdentity(), bindings, transport, FixedClock, FixedIds)
        val key = Base64Url.encode(ByteArray(32) { 7 })

        val result = useCase.pair("airmedy://pair/v1?host=10.0.0.2&port=1234&desktop_id=01234567-89ab-cdef-0123-456789abcdef&public_key=$key")

        assertIs<PairingResult.Failed>(result)
        assertEquals(PairingFailure.TimedOut, result.failure)
        assertEquals("airmedy/pairing/v1/01234567-89ab-cdef-0123-456789abcdef/response/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", transport.responseTopic)
        assertEquals("airmedy/pairing/v1/01234567-89ab-cdef-0123-456789abcdef/request", transport.requestTopic)
        assertTrue(transport.payload.contains("\"type\":\"pair.request\""))
    }

    @Test
    fun approvedPairingPersistsVerifiedMqttEndpointForTheSyncSession() = runTest {
        val bindings = FakeBindings()
        val key = Base64Url.encode(ByteArray(32) { 7 })
        val transport = FakeTransport(TransportResult.Response(approvedResponse()))
        val useCase = MobilePairingUseCase(FakeIdentity(), bindings, transport, FixedClock, FixedIds)

        val result = useCase.pair("airmedy://pair/v1?host=10.0.0.2&port=1234&desktop_id=01234567-89ab-cdef-0123-456789abcdef&public_key=$key")

        val paired = assertIs<PairingResult.Paired>(result).desktop
        assertEquals("10.0.0.2", paired.host)
        assertEquals(1234, paired.port)
        assertEquals(paired, bindings.current())
    }

    private fun approvedResponse(): String = """{"version":1,"type":"pair.response","request_id":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","decision":"approved","desktop_id":"01234567-89ab-cdef-0123-456789abcdef","desktop_nonce":"${Base64Url.encode(ByteArray(32))}","issued_at":1000,"signature":"${Base64Url.encode(ByteArray(64))}"}"""
}

private class FakeBindings : PairingBindingStore {
    private val state = MutableStateFlow<PairedDesktop?>(null)
    override val pairedDesktop: Flow<PairedDesktop?> = state
    override suspend fun current(): PairedDesktop? = state.value
    override suspend fun save(desktop: PairedDesktop) { state.value = desktop }
    override suspend fun clear() { state.value = null }
}

private class FakeIdentity : PairingIdentityProvider {
    override suspend fun identity() = MobileIdentity("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "Pixel", "android", ByteArray(32) { 1 })
    override suspend fun randomBytes(size: Int): ByteArray = ByteArray(size) { 2 }
    override suspend fun sign(input: ByteArray): ByteArray = ByteArray(64) { 3 }
    override suspend fun verify(publicKey: ByteArray, input: ByteArray, signature: ByteArray) = true
}

private class FakeTransport(private val result: TransportResult) : PairingTransport {
    var responseTopic = ""
    var requestTopic = ""
    var payload = ""
    override suspend fun exchange(endpoint: DesktopDiscovery, responseTopic: String, requestTopic: String, requestPayload: String): TransportResult {
        this.responseTopic = responseTopic
        this.requestTopic = requestTopic
        payload = requestPayload
        return result
    }
}

private object FixedClock : PairingClock { override fun nowMillis(): Long = 1_000L }
private object FixedIds : PairingIdGenerator { override fun newId(): String = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb" }
