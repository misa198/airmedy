package me.misa198.airmedy.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException
import me.misa198.airmedy.pairing.Base64Url
import me.misa198.airmedy.pairing.MobileIdentity
import me.misa198.airmedy.pairing.PairingIdentityProvider
import me.misa198.airmedy.pairing.PairedDesktop

/**
 * A durable, idempotent edit made on a mobile playlist.  It is deliberately a
 * delta rather than a playlist snapshot: a mobile library may only contain a
 * subset of a desktop playlist's tracks.
 */
@Serializable
data class PlaylistMutation(
    @SerialName("mutation_id") val mutationId: String,
    @SerialName("playlist_id") val playlistId: String,
    val operation: PlaylistMutationOperation,
    @SerialName("updated_at") val updatedAt: Long,
    val payload: PlaylistMutationPayload = PlaylistMutationPayload(),
)

@Serializable
enum class PlaylistMutationOperation {
    CREATE, UPDATE, DELETE, ADD_TRACK, REMOVE_TRACK, MOVE_TRACK, SET_ARTWORK, REMOVE_ARTWORK, SET_FAVORITE,
}

@Serializable
data class PlaylistMutationPayload(
    val name: String? = null,
    val description: String? = null,
    @SerialName("track_id") val trackId: String? = null,
    @SerialName("previous_track_id") val previousTrackId: String? = null,
    @SerialName("next_track_id") val nextTrackId: String? = null,
    @SerialName("artwork_sha256") val artworkSha256: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean? = null,
)

fun PlaylistMutation.validationError(): String? = when {
    mutationId.isBlank() || playlistId.isBlank() || updatedAt <= 0L -> "Missing mutation identity"
    operation == PlaylistMutationOperation.CREATE && payload.name.isNullOrBlank() -> "A playlist name is required"
    operation in setOf(PlaylistMutationOperation.ADD_TRACK, PlaylistMutationOperation.REMOVE_TRACK, PlaylistMutationOperation.MOVE_TRACK) && payload.trackId.isNullOrBlank() -> "A track ID is required"
    operation == PlaylistMutationOperation.SET_ARTWORK && !Sha256.matches(payload.artworkSha256.orEmpty()) -> "Invalid artwork hash"
    operation == PlaylistMutationOperation.SET_FAVORITE && (playlistId != "favorites" || payload.trackId.isNullOrBlank() || payload.isFavorite == null) -> "Invalid favorite mutation"
    else -> null
}

private val Sha256 = Regex("^[0-9a-f]{64}$")

private const val PlaylistSyncVersion = 1
private const val ReconciliationRequestType = "playlist.sync.reconcile.request"
private const val ReconciliationResultType = "playlist.sync.reconcile.result"

@Serializable
data class PlaylistReconciliationRequest(
    val version: Int = PlaylistSyncVersion,
    val type: String = ReconciliationRequestType,
    @SerialName("reconciliation_id") val reconciliationId: String,
    @SerialName("desktop_id") val desktopId: String,
    @SerialName("mobile_id") val mobileId: String,
    val scope: PlaylistSyncScope,
    @SerialName("batch_url") val batchUrl: String,
    @SerialName("artwork_url") val artworkUrl: String,
    @SerialName("listening_url") val listeningUrl: String,
    @SerialName("issued_at") val issuedAt: Long,
    val signature: String,
)

@Serializable
data class PlaylistSyncScope(val kind: String, @SerialName("selected_ids") val selectedIds: List<String>)

@Serializable
data class PlaylistMutationResult(@SerialName("mutation_id") val mutationId: String, val status: PlaylistMutationStatus)

@Serializable
enum class PlaylistMutationStatus {
    @SerialName("applied") APPLIED,
    @SerialName("duplicate") DUPLICATE,
    @SerialName("stale") STALE,
    @SerialName("rejected") REJECTED,
    @SerialName("scope-conflict") SCOPE_CONFLICT,
}

@Serializable
data class PlaylistMutationBatch(
    val version: Int = PlaylistSyncVersion,
    @SerialName("reconciliation_id") val reconciliationId: String,
    val mutations: List<PlaylistMutation>,
)

@Serializable
data class PlaylistMutationBatchResult(
    val version: Int,
    @SerialName("reconciliation_id") val reconciliationId: String,
    val results: List<PlaylistMutationResult>,
)

@Serializable
data class PlaylistReconciliationResult(
    val version: Int = PlaylistSyncVersion,
    val type: String = ReconciliationResultType,
    @SerialName("reconciliation_id") val reconciliationId: String,
    @SerialName("mobile_id") val mobileId: String,
    val results: List<PlaylistMutationResult>,
    @SerialName("issued_at") val issuedAt: Long,
    val signature: String,
)

object PlaylistSyncProtocol {
    fun requestTopic(desktopId: String, mobileId: String) = "airmedy/playlist-sync/v1/$desktopId/$mobileId/request"
    fun resultTopic(desktopId: String, mobileId: String) = "airmedy/playlist-sync/v1/$desktopId/$mobileId/result"
    fun requestSigningInput(value: PlaylistReconciliationRequest): String = LibrarySyncProtocol.json.encodeToString(PlaylistReconciliationRequest.serializer(), value.copy(signature = ""))
    fun resultSigningInput(value: PlaylistReconciliationResult): String = LibrarySyncProtocol.json.encodeToString(PlaylistReconciliationResult.serializer(), value.copy(signature = ""))
    fun validSignature(encoded: String) = Base64Url.decodeExact(encoded, 64) != null
}

fun interface PlaylistReconciliationClock { fun nowMillis(): Long }
interface PlaylistMutationStore {
    suspend fun pendingPlaylistMutations(): List<PlaylistMutation>
    suspend fun acknowledgePlaylistMutations(results: List<PlaylistMutationResult>)
}
data class StagedPlaylistArtwork(val sha256: String, val mime: String, val size: Long, val relativePath: String)
interface PlaylistArtworkStagingStore { suspend fun stagedPlaylistArtwork(sha256: String): StagedPlaylistArtwork? }
fun interface PlaylistReconciliationTransport {
    suspend fun upload(request: PlaylistReconciliationRequest, mutations: List<PlaylistMutation>, desktop: PairedDesktop): List<PlaylistMutationResult>
}
fun interface PlaylistReconciliationPublisher { suspend fun publish(payload: String) }

sealed interface PlaylistReconciliationOutcome {
    data class Completed(val results: List<PlaylistMutationResult>) : PlaylistReconciliationOutcome
    data class Rejected(val reason: String) : PlaylistReconciliationOutcome
    data class TransportFailed(val reason: String) : PlaylistReconciliationOutcome
}

fun PlaylistMutationStatus.isTerminal(): Boolean = this in setOf(
    PlaylistMutationStatus.APPLIED, PlaylistMutationStatus.DUPLICATE, PlaylistMutationStatus.STALE,
    PlaylistMutationStatus.REJECTED, PlaylistMutationStatus.SCOPE_CONFLICT,
)

class PlaylistReconciliationCoordinator(
    private val identityProvider: PairingIdentityProvider,
    private val clock: PlaylistReconciliationClock,
    private val store: PlaylistMutationStore,
    private val transport: PlaylistReconciliationTransport,
    private val publisher: PlaylistReconciliationPublisher,
) {
    suspend fun handle(payload: String, desktop: PairedDesktop): PlaylistReconciliationOutcome {
        val request = runCatching { LibrarySyncProtocol.json.decodeFromString(PlaylistReconciliationRequest.serializer(), payload) }
            .getOrElse { return PlaylistReconciliationOutcome.Rejected("Malformed reconciliation request") }
        val mobile = identityProvider.identity()
        validate(request, desktop, mobile)?.let { return PlaylistReconciliationOutcome.Rejected(it) }
        val signature = Base64Url.decodeExact(request.signature, 64)
            ?: return PlaylistReconciliationOutcome.Rejected("Invalid request signature")
        if (!identityProvider.verify(desktop.publicKey, PlaylistSyncProtocol.requestSigningInput(request).encodeToByteArray(), signature)) {
            return PlaylistReconciliationOutcome.Rejected("Invalid request signature")
        }
        val mutations = store.pendingPlaylistMutations()
        val results = try { transport.upload(request, mutations, desktop) } catch (error: Throwable) {
            if (error is CancellationException) throw error
            return PlaylistReconciliationOutcome.TransportFailed(error.message ?: "Reconciliation upload failed")
        }
        val mutationIds = mutations.map(PlaylistMutation::mutationId)
        if (results.any { !it.status.isTerminal() } || results.map(PlaylistMutationResult::mutationId).toSet() != mutationIds.toSet() || results.size != mutationIds.size) return PlaylistReconciliationOutcome.Rejected("Invalid mutation results")
        val unsigned = PlaylistReconciliationResult(
            reconciliationId = request.reconciliationId, mobileId = mobile.id, results = results,
            issuedAt = clock.nowMillis(), signature = "",
        )
        val signed = identityProvider.sign(PlaylistSyncProtocol.resultSigningInput(unsigned).encodeToByteArray())
        if (signed.size != 64) return PlaylistReconciliationOutcome.TransportFailed("Unable to sign reconciliation result")
        try {
            publisher.publish(LibrarySyncProtocol.json.encodeToString(PlaylistReconciliationResult.serializer(), unsigned.copy(signature = Base64Url.encode(signed))))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            return PlaylistReconciliationOutcome.TransportFailed(error.message ?: "Unable to publish reconciliation result")
        }
        // A result is terminal only after the desktop has received it; failures
        // before publishing intentionally leave Room rows pending for retry.
        store.acknowledgePlaylistMutations(results)
        return PlaylistReconciliationOutcome.Completed(results)
    }

    private fun validate(request: PlaylistReconciliationRequest, desktop: PairedDesktop, mobile: MobileIdentity): String? = when {
        request.version != PlaylistSyncVersion || request.type != ReconciliationRequestType -> "Unsupported reconciliation request"
        request.reconciliationId.isBlank() || request.batchUrl.isBlank() || request.artworkUrl.isBlank() || request.listeningUrl.isBlank() -> "Missing reconciliation fields"
        request.desktopId != desktop.desktopId || request.mobileId != mobile.id -> "Foreign reconciliation request"
        kotlin.math.abs(clock.nowMillis() - request.issuedAt) > 5 * 60 * 1000L -> "Expired reconciliation request"
        request.scope.kind !in setOf("all", "artists", "albums", "genres", "playlists") -> "Invalid reconciliation scope"
        request.scope.kind == "all" && request.scope.selectedIds.isNotEmpty() -> "Invalid reconciliation scope"
        request.scope.kind != "all" && request.scope.selectedIds.isEmpty() -> "Invalid reconciliation scope"
        else -> null
    }
}
