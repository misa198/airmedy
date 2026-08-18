package me.misa198.airmedy.sync

import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.misa198.airmedy.pairing.PairingIdentityProvider
import me.misa198.airmedy.sync.LibrarySyncProtocol
import me.misa198.airmedy.sync.PlaylistMutation
import me.misa198.airmedy.sync.PlaylistMutationResult
import me.misa198.airmedy.sync.PlaylistMutationBatch
import me.misa198.airmedy.sync.PlaylistMutationBatchResult
import me.misa198.airmedy.sync.PlaylistReconciliationRequest
import me.misa198.airmedy.sync.PlaylistReconciliationTransport
import me.misa198.airmedy.sync.PlaylistArtworkStagingStore
import me.misa198.airmedy.sync.ListeningSyncSnapshot
import me.misa198.airmedy.sync.ListeningSyncStore
import me.misa198.airmedy.sync.ListeningSyncProtocol
import me.misa198.airmedy.pairing.PairedDesktop

/** Android HTTP adapter for the short-lived, plan-independent reconciliation session. */
internal class AndroidPlaylistReconciliationTransport(
    private val identity: PairingIdentityProvider,
    private val filesDir: File,
    private val staging: PlaylistArtworkStagingStore,
    private val requester: PlaylistHttpRequester? = null,
    private val listening: ListeningSyncStore? = null,
) : PlaylistReconciliationTransport {
    override suspend fun upload(request: PlaylistReconciliationRequest, mutations: List<PlaylistMutation>, desktop: PairedDesktop): List<PlaylistMutationResult> = withContext(Dispatchers.IO) {
        mutations.filter { it.operation.name == "SET_ARTWORK" }.forEach { mutation ->
            val hash = mutation.payload.artworkSha256 ?: error("Missing artwork hash")
            val staged = staging.stagedPlaylistArtwork(hash) ?: error("Staged playlist artwork is missing")
            val bytes = readStagedPlaylistArtwork(filesDir, staged, hash)
            put("${request.artworkUrl}/$hash", request.mobileId, bytes, staged.mime)
        }
        val body = LibrarySyncProtocol.json.encodeToString(PlaylistMutationBatch.serializer(), PlaylistMutationBatch(reconciliationId = request.reconciliationId, mutations = mutations)).encodeToByteArray()
        val response = performRequest("POST", request.batchUrl, request.mobileId, body, "application/json")
        if (response.code !in 200..299) error("Playlist mutation upload failed (${response.code})")
        val results = LibrarySyncProtocol.json.decodeFromString(PlaylistMutationBatchResult.serializer(), response.body.decodeToString()).results
        listening?.let { store ->
            val snapshot = store.listeningSnapshot(request.reconciliationId, System.currentTimeMillis() - ListeningRetentionMs)
            val listeningBody = LibrarySyncProtocol.json.encodeToString(ListeningSyncSnapshot.serializer(), snapshot).encodeToByteArray()
            val listeningResponse = performRequest("POST", request.listeningUrl, request.mobileId, listeningBody, "application/json")
            if (listeningResponse.code !in 200..299) error("Listening sync failed (${listeningResponse.code})")
            val merged = LibrarySyncProtocol.json.decodeFromString(ListeningSyncSnapshot.serializer(), listeningResponse.body.decodeToString())
            require(merged.version == 1 && merged.reconciliationId == request.reconciliationId) { "Invalid listening sync response" }
            val signature = runCatching { Base64.decode(merged.signature, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }
                .getOrNull()?.takeIf { it.size == 64 } ?: error("Invalid listening sync signature")
            require(identity.verify(desktop.publicKey, ListeningSyncProtocol.signingInput(merged), signature)) { "Invalid listening sync signature" }
            store.mergeListeningSnapshot(merged)
        }
        results
    }

    private suspend fun put(url: String, mobileId: String, body: ByteArray, mime: String) {
        val response = performRequest("PUT", url, mobileId, body, mime)
        if (response.code !in 200..299) error("Playlist artwork upload failed (${response.code})")
    }

    private suspend fun performRequest(method: String, rawUrl: String, mobileId: String, body: ByteArray, contentType: String): PlaylistHttpResponse =
        requester?.request(method, rawUrl, mobileId, body, contentType) ?: request(method, rawUrl, mobileId, body, contentType)

    private suspend fun request(method: String, rawUrl: String, mobileId: String, body: ByteArray, contentType: String): PlaylistHttpResponse {
        val uri = URI(rawUrl).takeIf { it.scheme == "http" && it.host != null } ?: error("Invalid reconciliation URL")
        val mobile = identity.identity()
        check(mobile.id == mobileId) { "Pairing identity changed" }
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val nonce = identity.randomBytes(32).base64Url()
        val input = "$method\n${uri.rawPath}\n${body.sha256()}\n$timestamp\n$nonce".encodeToByteArray()
        val signature = identity.sign(input).base64Url()
        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15_000; readTimeout = 30_000; doOutput = true
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("X-Airmedy-Mobile-ID", mobile.id)
            setRequestProperty("X-Airmedy-Timestamp", timestamp)
            setRequestProperty("X-Airmedy-Nonce", nonce)
            setRequestProperty("X-Airmedy-Signature", signature)
        }
        return try {
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val bytes = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readNBytes(MaxReconciliationBodyBytes + 1) } ?: byteArrayOf()
            require(bytes.size <= MaxReconciliationBodyBytes) { "Reconciliation response is too large" }
            PlaylistHttpResponse(code, bytes)
        } finally { connection.disconnect() }
    }

    private fun ByteArray.sha256(): String = sha256Hex()
    private fun ByteArray.base64Url(): String = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

private const val ListeningRetentionMs = 180L * 24 * 60 * 60 * 1_000
private const val MaxReconciliationBodyBytes = 32 * 1024 * 1024

internal data class PlaylistHttpResponse(val code: Int, val body: ByteArray)
internal fun interface PlaylistHttpRequester {
    suspend fun request(method: String, url: String, mobileId: String, body: ByteArray, contentType: String): PlaylistHttpResponse
}

internal fun readStagedPlaylistArtwork(filesDir: File, staged: me.misa198.airmedy.sync.StagedPlaylistArtwork, expectedHash: String): ByteArray {
    require(!staged.relativePath.startsWith('/') && ".." !in staged.relativePath.split('/')) { "Invalid staged playlist artwork path" }
    require(staged.mime in setOf("image/jpeg", "image/png", "image/webp")) { "Invalid staged playlist artwork MIME" }
    val artwork = File(filesDir, staged.relativePath)
    require(artwork.isFile) { "Staged playlist artwork is missing" }
    val bytes = artwork.readBytes()
    require(bytes.size.toLong() == staged.size && bytes.sha256Hex() == expectedHash && staged.sha256 == expectedHash) { "Staged playlist artwork is corrupt" }
    return bytes
}

private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
