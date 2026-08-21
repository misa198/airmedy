package me.misa198.airmedy.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingBroadcastResolverTest {
    private val desktopId = "01234567-89ab-cdef-0123-456789abcdef"

    @Test
    fun resolvesOnlyAMatchingTrustedDesktopRecord() {
        val endpoint = PairingBroadcastResolver.resolve(
            PairingBroadcastRecord(1883, mapOf("ip" to "192.168.1.20", "port" to "1883", "device_id" to desktopId)),
            desktopId,
        )

        assertEquals(PairingEndpoint("192.168.1.20", 1883), endpoint)
    }

    @Test
    fun rejectsMalformedUnknownAndMismatchedRecords() {
        val valid = mapOf("ip" to "192.168.1.20", "port" to "1883", "device_id" to desktopId)
        listOf(
            PairingBroadcastRecord(1884, valid),
            PairingBroadcastRecord(1883, valid + ("device_id" to "01234567-89ab-cdef-0123-456789abcdee")),
            PairingBroadcastRecord(1883, valid + ("device_id" to desktopId.uppercase())),
            PairingBroadcastRecord(1883, valid + ("ip" to "not-an-ip")),
            PairingBroadcastRecord(1883, valid + ("port" to "mqtt")),
            PairingBroadcastRecord(1883, valid - "ip"),
        ).forEach { record ->
            assertNull(PairingBroadcastResolver.resolve(record, desktopId))
        }
    }
}
