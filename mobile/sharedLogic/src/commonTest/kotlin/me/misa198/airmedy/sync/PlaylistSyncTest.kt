package me.misa198.airmedy.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertIs
import me.misa198.airmedy.pairing.Base64Url
import me.misa198.airmedy.pairing.MobileIdentity
import me.misa198.airmedy.pairing.PairingIdentityProvider
import me.misa198.airmedy.pairing.PairedDesktop

class PlaylistSyncTest {
    @Test fun `validates mutation data required by each operation`() {
        val add = PlaylistMutation("m1", "p1", PlaylistMutationOperation.ADD_TRACK, 1)
        assertEquals("A track ID is required", add.validationError())
        assertNull(add.copy(payload = PlaylistMutationPayload(trackId = "t1")).validationError())
    }

    @Test fun `validates desired favorite state`() {
        val favorite = PlaylistMutation("m1", "favorites", PlaylistMutationOperation.SET_FAVORITE, 1)
        assertEquals("Invalid favorite mutation", favorite.validationError())
        assertNull(favorite.copy(payload = PlaylistMutationPayload(trackId = "t1", isFavorite = true)).validationError())
    }
}

class PlaylistSyncProtocolTest {
    @Test fun `uses isolated reconciliation topics and compact signing input`() {
        val request = PlaylistReconciliationRequest(
            reconciliationId = "r", desktopId = "desktop", mobileId = "mobile",
            scope = PlaylistSyncScope("all", emptyList()), batchUrl = "http://desktop/batch", artworkUrl = "http://desktop/artwork", listeningUrl = "http://desktop/listening",
            issuedAt = 42, signature = "",
        )
        assertEquals("airmedy/playlist-sync/v1/desktop/mobile/request", PlaylistSyncProtocol.requestTopic("desktop", "mobile"))
        assertEquals("airmedy/playlist-sync/v1/desktop/mobile/result", PlaylistSyncProtocol.resultTopic("desktop", "mobile"))
        assertEquals("{\"version\":1,\"type\":\"playlist.sync.reconcile.request\",\"reconciliation_id\":\"r\",\"desktop_id\":\"desktop\",\"mobile_id\":\"mobile\",\"scope\":{\"kind\":\"all\",\"selected_ids\":[]},\"batch_url\":\"http://desktop/batch\",\"artwork_url\":\"http://desktop/artwork\",\"listening_url\":\"http://desktop/listening\",\"issued_at\":42,\"signature\":\"\"}", PlaylistSyncProtocol.requestSigningInput(request))
        val result = PlaylistReconciliationResult(reconciliationId = "r", mobileId = "mobile", results = listOf(PlaylistMutationResult("m", PlaylistMutationStatus.APPLIED)), issuedAt = 43, signature = "signed")
        assertEquals("{\"version\":1,\"type\":\"playlist.sync.reconcile.result\",\"reconciliation_id\":\"r\",\"mobile_id\":\"mobile\",\"results\":[{\"mutation_id\":\"m\",\"status\":\"applied\"}],\"issued_at\":43,\"signature\":\"\"}", PlaylistSyncProtocol.resultSigningInput(result))
        assertTrue(PlaylistSyncProtocol.validSignature(Base64Url.encode(ByteArray(64))))
        assertFalse(PlaylistSyncProtocol.validSignature("invalid"))
    }
}

class PlaylistSyncResultTest {
    @Test fun `every desktop acknowledgement status is terminal`() {
        PlaylistMutationStatus.entries.forEach { assertTrue(it.isTerminal()) }
    }

    @Test fun `invalid artwork hash is rejected before transport`() {
        val mutation = PlaylistMutation("m", "p", PlaylistMutationOperation.SET_ARTWORK, 1, PlaylistMutationPayload(artworkSha256 = "bad"))
        assertFalse(mutation.validationError() == null)
    }
}

class PlaylistReconciliationCoordinatorTest {
    private val mutation = PlaylistMutation("mutation", "playlist", PlaylistMutationOperation.UPDATE, 1, PlaylistMutationPayload(name = "Name"))
    private val desktop = PairedDesktop("desktop", "Desktop", ByteArray(32))
    private val request = PlaylistReconciliationRequest(
        reconciliationId = "reconciliation", desktopId = "desktop", mobileId = "mobile",
        scope = PlaylistSyncScope("all", emptyList()), batchUrl = "http://desktop/batch", artworkUrl = "http://desktop/artwork", listeningUrl = "http://desktop/listening",
        issuedAt = 1_000, signature = Base64Url.encode(ByteArray(64)),
    )

    @Test fun `acknowledges only after terminal result is published`() = kotlinx.coroutines.test.runTest {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            transport = { _, _, _ -> events += "upload"; listOf(PlaylistMutationResult("mutation", PlaylistMutationStatus.APPLIED)) },
            publisher = { events += "publish" },
            acknowledge = { events += "ack" },
        )
        assertIs<PlaylistReconciliationOutcome.Completed>(coordinator.handle(encoded(request), desktop))
        assertEquals(listOf("upload", "publish", "ack"), events)
    }

    @Test fun `transport and publish failures retain pending queue`() = kotlinx.coroutines.test.runTest {
        var acknowledgements = 0
        val transportFailure = coordinator(transport = { _, _, _ -> error("offline") }, acknowledge = { acknowledgements++ })
        assertIs<PlaylistReconciliationOutcome.TransportFailed>(transportFailure.handle(encoded(request), desktop))
        val publishFailure = coordinator(
            transport = { _, _, _ -> listOf(PlaylistMutationResult("mutation", PlaylistMutationStatus.APPLIED)) },
            publisher = { error("mqtt offline") }, acknowledge = { acknowledgements++ },
        )
        assertIs<PlaylistReconciliationOutcome.TransportFailed>(publishFailure.handle(encoded(request), desktop))
        assertEquals(0, acknowledgements)
    }

    @Test fun `rejects foreign expired invalid scope and incomplete results`() = kotlinx.coroutines.test.runTest {
        val base = coordinator(transport = { _, _, _ -> emptyList() })
        assertIs<PlaylistReconciliationOutcome.Rejected>(base.handle(encoded(request.copy(mobileId = "foreign")), desktop))
        assertIs<PlaylistReconciliationOutcome.Rejected>(base.handle(encoded(request.copy(issuedAt = -400_000)), desktop))
        assertIs<PlaylistReconciliationOutcome.Rejected>(base.handle(encoded(request.copy(scope = PlaylistSyncScope("playlists", emptyList()))), desktop))
        assertIs<PlaylistReconciliationOutcome.Rejected>(base.handle(encoded(request), desktop))
    }

    private fun coordinator(
        transport: PlaylistReconciliationTransport = PlaylistReconciliationTransport { _, _, _ -> listOf(PlaylistMutationResult("mutation", PlaylistMutationStatus.APPLIED)) },
        publisher: PlaylistReconciliationPublisher = PlaylistReconciliationPublisher { },
        acknowledge: suspend (List<PlaylistMutationResult>) -> Unit = {},
    ) = PlaylistReconciliationCoordinator(
        identityProvider = FakeIdentity, clock = PlaylistReconciliationClock { 1_000 },
        store = object : PlaylistMutationStore {
            override suspend fun pendingPlaylistMutations() = listOf(mutation)
            override suspend fun acknowledgePlaylistMutations(results: List<PlaylistMutationResult>) = acknowledge(results)
        }, transport = transport, publisher = publisher,
    )
    private fun encoded(value: PlaylistReconciliationRequest) = LibrarySyncProtocol.json.encodeToString(PlaylistReconciliationRequest.serializer(), value)
    private object FakeIdentity : PairingIdentityProvider {
        override suspend fun identity() = MobileIdentity("mobile", "Phone", "Android", ByteArray(32))
        override suspend fun randomBytes(size: Int) = ByteArray(size)
        override suspend fun sign(input: ByteArray) = ByteArray(64)
        override suspend fun verify(publicKey: ByteArray, input: ByteArray, signature: ByteArray) = true
    }
}
