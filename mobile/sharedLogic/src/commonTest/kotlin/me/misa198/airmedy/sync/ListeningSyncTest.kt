package me.misa198.airmedy.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class ListeningSyncTest {
    @Test
    fun responseSignatureIsExcludedFromSigningInput() {
        val snapshot = ListeningSyncSnapshot(reconciliationId = "r", signature = "signed")
        assertEquals(
            "{\"version\":1,\"reconciliation_id\":\"r\",\"sessions\":[],\"attempts\":[],\"daily_tracks\":[],\"daily_attempts\":[],\"signature\":\"\"}",
            ListeningSyncProtocol.signingInput(snapshot).decodeToString(),
        )
        assertEquals(
            ListeningSyncProtocol.signingInput(snapshot.copy(signature = "")).toList(),
            ListeningSyncProtocol.signingInput(snapshot).toList(),
        )
    }
}
