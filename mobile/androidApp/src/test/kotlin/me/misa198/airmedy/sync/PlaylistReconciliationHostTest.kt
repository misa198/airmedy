package me.misa198.airmedy.sync

import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.misa198.airmedy.sync.PlaylistMutation
import me.misa198.airmedy.sync.PlaylistMutationOperation
import me.misa198.airmedy.sync.PlaylistMutationPayload
import me.misa198.airmedy.sync.StagedPlaylistArtwork
import me.misa198.airmedy.pairing.MobileIdentity
import me.misa198.airmedy.pairing.PairingIdentityProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaylistReconciliationHostTest {
    @Test fun acknowledgedArtworkIsOnlyCleanedAfterItsPlanActivates() {
        val hash = "a".repeat(64)
        assertEquals(emptyList<String>(), unusedPlaylistArtworkHashes(listOf(hash), listOf(mutation("1", PlaylistMutationOperation.SET_ARTWORK, PlaylistMutationPayload(artworkSha256 = hash))), emptyList()))
        assertEquals(listOf(hash), unusedPlaylistArtworkHashes(listOf(hash), emptyList(), emptyList()))
    }

    @Test fun pendingRemoveTrackIsProjectedImmediately() {
        val playlist = LibraryPlaylist("p", "Original", listOf("a", "b", "c"), "{}")

        val projected = applyPendingPlaylistMutations(
            listOf(playlist),
            listOf(mutation("1", PlaylistMutationOperation.REMOVE_TRACK, PlaylistMutationPayload(trackId = "b"))),
        )

        assertEquals(listOf("a", "c"), projected.single().trackIds)
    }

    @Test fun pendingEditArtworkAndDeleteAreProjectedImmediately() {
        val playlist = LibraryPlaylist("p", "Original", listOf("a"), "{}")
        val edited = applyPendingPlaylistMutations(
            listOf(playlist),
            listOf(
                mutation("1", PlaylistMutationOperation.UPDATE, PlaylistMutationPayload(name = "Edited")),
                mutation("2", PlaylistMutationOperation.SET_ARTWORK, PlaylistMutationPayload(artworkSha256 = "a".repeat(64))),
            ),
        ).single()

        assertEquals("Edited", edited.name)
        assertEquals("a".repeat(64), ((LibrarySyncProtocol.json.parseToJsonElement(edited.metadataJson) as JsonObject)["playlist"] as JsonObject)["artwork_key"]?.let { (it as JsonPrimitive).content })
        assertEquals(emptyList<LibraryPlaylist>(), applyPendingPlaylistMutations(listOf(edited), listOf(mutation("3", PlaylistMutationOperation.DELETE, PlaylistMutationPayload()))))
    }

    @Test fun successfulMutationsStayProjectedUntilTheReplacementSnapshot() {
        assertEquals(true, PlaylistMutationStatus.APPLIED.awaitingReplacementSnapshot())
        assertEquals(true, PlaylistMutationStatus.DUPLICATE.awaitingReplacementSnapshot())
        assertEquals(false, PlaylistMutationStatus.STALE.awaitingReplacementSnapshot())
    }

    @Test fun pendingMutationsMergeInQueueOrderAndHonorScope() {
        val snapshot = listOf(playlist("p", "Original", listOf("a", "b", "c")))
        val pending = listOf(
            mutation("1", PlaylistMutationOperation.REMOVE_TRACK, PlaylistMutationPayload(trackId = "b")),
            mutation("2", PlaylistMutationOperation.ADD_TRACK, PlaylistMutationPayload(trackId = "d")),
            mutation("3", PlaylistMutationOperation.MOVE_TRACK, PlaylistMutationPayload(trackId = "d", previousTrackId = "a", nextTrackId = "c")),
            mutation("4", PlaylistMutationOperation.UPDATE, PlaylistMutationPayload(name = "Mobile")),
        )
        val merged = mergePlaylistSnapshot(snapshot, scope("all"), pending).single()
        assertEquals("Mobile", (merged["playlist"] as JsonObject)["name"]?.let { (it as JsonPrimitive).content })
        assertEquals(listOf("a", "d", "c"), (merged["track_ids"] as JsonArray).map { (it as JsonPrimitive).content })
        assertEquals(snapshot, mergePlaylistSnapshot(snapshot, scope("artists", listOf("artist")), pending))
    }

    @Test fun stagedArtworkRejectsMissingAndCorruptFiles() {
        val root = createTempDirectory("airmedy-artwork-").toFile()
        try {
            val bytes = "artwork".encodeToByteArray()
            val hash = bytes.sha256()
            val staged = StagedPlaylistArtwork(hash, "image/png", bytes.size.toLong(), "playlist-artwork/$hash")
            assertThrows(IllegalArgumentException::class.java) { readStagedPlaylistArtwork(root, staged, hash) }
            val file = File(root, staged.relativePath).apply { parentFile?.mkdirs(); writeBytes("corrupt".encodeToByteArray()) }
            assertThrows(IllegalArgumentException::class.java) { readStagedPlaylistArtwork(root, staged, hash) }
            file.writeBytes(bytes)
            assertEquals(bytes.toList(), readStagedPlaylistArtwork(root, staged, hash).toList())
        } finally { root.deleteRecursively() }
    }

    @Test fun artworkUploadsBeforeSetArtworkMutationBatch() = runTest {
        val root = createTempDirectory("airmedy-artwork-order-").toFile()
        try {
            val bytes = "artwork".encodeToByteArray(); val hash = bytes.sha256()
            val relativePath = "playlist-artwork/$hash"
            File(root, relativePath).apply { parentFile?.mkdirs(); writeBytes(bytes) }
            val calls = mutableListOf<String>()
            val resultBody = LibrarySyncProtocol.json.encodeToString(
                me.misa198.airmedy.sync.PlaylistMutationBatchResult.serializer(),
                me.misa198.airmedy.sync.PlaylistMutationBatchResult(1, "r", listOf(me.misa198.airmedy.sync.PlaylistMutationResult("m", me.misa198.airmedy.sync.PlaylistMutationStatus.APPLIED))),
            ).encodeToByteArray()
            val transport = AndroidPlaylistReconciliationTransport(
                FakeIdentity, root,
                object : me.misa198.airmedy.sync.PlaylistArtworkStagingStore {
                    override suspend fun stagedPlaylistArtwork(sha256: String) = StagedPlaylistArtwork(hash, "image/png", bytes.size.toLong(), relativePath)
                },
                PlaylistHttpRequester { method, _, _, _, _ -> calls += method; PlaylistHttpResponse(200, if (method == "POST") resultBody else byteArrayOf()) },
            )
            val mutation = PlaylistMutation("m", "p", PlaylistMutationOperation.SET_ARTWORK, 1, PlaylistMutationPayload(artworkSha256 = hash))
            transport.upload(me.misa198.airmedy.sync.PlaylistReconciliationRequest(reconciliationId="r",desktopId="desktop",mobileId="mobile",scope=me.misa198.airmedy.sync.PlaylistSyncScope("all",emptyList()),batchUrl="http://desktop/batch",artworkUrl="http://desktop/artwork",listeningUrl="http://desktop/listening",issuedAt=1,signature=""), listOf(mutation), me.misa198.airmedy.pairing.PairedDesktop("desktop", "Desktop", ByteArray(32)))
            assertEquals(listOf("PUT", "POST"), calls)
        } finally { root.deleteRecursively() }
    }

    private fun mutation(id: String, operation: PlaylistMutationOperation, payload: PlaylistMutationPayload) = PlaylistMutation(id, "p", operation, id.toLong(), payload)
    private fun playlist(id: String, name: String, tracks: List<String>) = JsonObject(mapOf("playlist" to JsonObject(mapOf("id" to JsonPrimitive(id), "name" to JsonPrimitive(name))), "track_ids" to JsonArray(tracks.map(::JsonPrimitive))))
    private fun scope(kind: String, ids: List<String> = emptyList()) = JsonObject(mapOf("kind" to JsonPrimitive(kind), "selected_ids" to JsonArray(ids.map(::JsonPrimitive))))
    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
    private object FakeIdentity : PairingIdentityProvider {
        override suspend fun identity() = MobileIdentity("mobile", "Phone", "Android", ByteArray(32))
        override suspend fun randomBytes(size: Int) = ByteArray(size)
        override suspend fun sign(input: ByteArray) = ByteArray(64)
        override suspend fun verify(publicKey: ByteArray, input: ByteArray, signature: ByteArray) = true
    }
}
