package me.misa198.airmedy.sync

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.misa198.airmedy.pairing.Base64Url
import me.misa198.airmedy.pairing.MobileIdentity
import me.misa198.airmedy.pairing.PairingIdentityProvider
import me.misa198.airmedy.pairing.PairedDesktop

private const val ProtocolVersion = 1
private const val RequestType = "library.sync.request"
private const val ReceiptType = "library.sync.receipt"
private const val ClockSkewMillis = 5 * 60 * 1000L

@Serializable
data class LibrarySyncRequest(
    val version: Int,
    val type: String,
    @SerialName("plan_id") val planId: String,
    @SerialName("desktop_id") val desktopId: String,
    @SerialName("mobile_id") val mobileId: String,
    @SerialName("manifest_url") val manifestUrl: String,
    @SerialName("manifest_hash") val manifestHash: String,
    @SerialName("issued_at") val issuedAt: Long,
    val signature: String,
)

@Serializable
data class LibrarySyncAsset(
    val id: String,
    val kind: String,
    val sha256: String,
    val size: Long,
)

@Serializable
data class LibrarySyncManifest(
    val version: Int,
    @SerialName("plan_id") val planId: String,
    val revision: String,
    val scope: JsonObject,
    // Go encodes nil slices as null. A plan without playlists therefore has
    // "playlists": null, which is a valid desktop manifest.
    val tracks: List<JsonObject>? = null,
    val playlists: List<JsonObject>? = null,
    val lyrics: JsonObject,
    val analysis: JsonObject,
    val assets: List<LibrarySyncAsset>? = null,
)

@Serializable
data class LibrarySyncReceipt(
    val version: Int = ProtocolVersion,
    val type: String = ReceiptType,
    @SerialName("plan_id") val planId: String,
    @SerialName("mobile_id") val mobileId: String,
    @SerialName("asset_id") val assetId: String,
    val complete: Boolean,
    @SerialName("issued_at") val issuedAt: Long,
    val signature: String,
)

data class PulledManifest(val body: String, val sha256: String)
data class PulledAsset(val relativePath: String, val sha256: String, val size: Long)

sealed interface LibrarySyncFailure {
    data class InvalidRequest(val reason: String) : LibrarySyncFailure
    data class InvalidManifest(val reason: String) : LibrarySyncFailure
    data class Transport(val reason: String) : LibrarySyncFailure
    data object Superseded : LibrarySyncFailure
    data object HashMismatch : LibrarySyncFailure
}

sealed interface LibrarySyncResult {
    data class Completed(val planId: String) : LibrarySyncResult
    data class Failed(val failure: LibrarySyncFailure) : LibrarySyncResult
}

fun interface LibrarySyncClock { fun nowMillis(): Long }

/** Platform HTTP code signs and streams every request without exposing asset bytes to common code. */
interface LibrarySyncPuller {
    suspend fun manifest(request: LibrarySyncRequest): PulledManifest
    suspend fun asset(request: LibrarySyncRequest, asset: LibrarySyncAsset): PulledAsset
}

/** Android/iOS persist their native files and metadata, while the state machine remains shared. */
interface LibrarySyncStore {
    suspend fun prepare(request: LibrarySyncRequest, manifest: LibrarySyncManifest)
    suspend fun isAssetCommitted(planId: String, asset: LibrarySyncAsset): Boolean
    suspend fun stageAsset(planId: String, asset: LibrarySyncAsset, pulled: PulledAsset)
    suspend fun activate(planId: String): List<String>
    suspend fun finalize(planId: String)
    suspend fun discard(planId: String)
}

fun interface LibrarySyncReceiptPublisher {
    suspend fun publish(payload: String)
}

fun interface LibrarySyncProgressReporter {
    fun report(completed: Int, total: Int)
}

object LibrarySyncProtocol {
    val json = Json { ignoreUnknownKeys = false; encodeDefaults = true; explicitNulls = false }

    fun requestSigningInput(request: LibrarySyncRequest): String = json.encodeToString(
        LibrarySyncRequest.serializer(), request.copy(signature = ""),
    )

    fun receiptSigningInput(receipt: LibrarySyncReceipt): String = json.encodeToString(
        LibrarySyncReceipt.serializer(), receipt.copy(signature = ""),
    )

    fun receiptTopic(desktopId: String, mobileId: String): String =
        "airmedy/library-sync/v1/$desktopId/$mobileId/receipt"

    fun requestTopic(desktopId: String, mobileId: String): String =
        "airmedy/library-sync/v1/$desktopId/$mobileId/request"
}

class LibrarySyncCoordinator(
    private val identityProvider: PairingIdentityProvider,
    private val clock: LibrarySyncClock,
    private val puller: LibrarySyncPuller,
    private val store: LibrarySyncStore,
    private val receipts: LibrarySyncReceiptPublisher,
    private val progress: LibrarySyncProgressReporter = LibrarySyncProgressReporter { _, _ -> },
    private val assetParallelism: Int = 1,
) {
    private val mutex = Mutex()

    init {
        require(assetParallelism > 0) { "Asset parallelism must be positive" }
    }

    suspend fun handle(payload: String, desktop: PairedDesktop): LibrarySyncResult = mutex.withLock {
        val request = runCatching { LibrarySyncProtocol.json.decodeFromString(LibrarySyncRequest.serializer(), payload) }
            .getOrElse { return LibrarySyncResult.Failed(LibrarySyncFailure.InvalidRequest("Malformed sync request")) }
        val mobile = identityProvider.identity()
        val invalid = validateRequest(request, desktop, mobile)
        if (invalid != null) return LibrarySyncResult.Failed(LibrarySyncFailure.InvalidRequest(invalid))
        val signature = Base64Url.decodeExact(request.signature, 64)
            ?: return LibrarySyncResult.Failed(LibrarySyncFailure.InvalidRequest("Invalid request signature"))
        if (!identityProvider.verify(desktop.publicKey, LibrarySyncProtocol.requestSigningInput(request).encodeToByteArray(), signature)) {
            return LibrarySyncResult.Failed(LibrarySyncFailure.InvalidRequest("Invalid request signature"))
        }

        val pulledManifest = try {
            puller.manifest(request)
        } catch (error: LibrarySyncPullException) {
            return LibrarySyncResult.Failed(error.failure)
        }
        if (!pulledManifest.sha256.equals(request.manifestHash, ignoreCase = true)) {
            return LibrarySyncResult.Failed(LibrarySyncFailure.HashMismatch)
        }
        val manifest = runCatching {
            LibrarySyncProtocol.json.decodeFromString(LibrarySyncManifest.serializer(), pulledManifest.body)
        }.getOrElse { return LibrarySyncResult.Failed(LibrarySyncFailure.InvalidManifest("Malformed manifest")) }
        val manifestError = validateManifest(manifest, request)
        if (manifestError != null) return LibrarySyncResult.Failed(LibrarySyncFailure.InvalidManifest(manifestError))

        try {
            store.prepare(request, manifest)
            val assets = manifest.assets.orEmpty()
            progress.report(0, assets.size)
            val groups = assets.groupBy { AssetContent(it.sha256, it.size) }.values
            var completed = 0
            groups.chunked(assetParallelism).forEach { batch ->
                coroutineScope {
                    batch.map { group -> async {
                        if (group.any { !store.isAssetCommitted(request.planId, it) }) {
                            val pulled = puller.asset(request, group.first())
                            group.forEach { store.stageAsset(request.planId, it, pulled) }
                        }
                        group
                    } }.awaitAll()
                }
                batch.forEach { group -> group.forEach { asset ->
                    publishReceipt(request.planId, mobile, asset.id, complete = false)
                    completed += 1
                    progress.report(completed, assets.size)
                } }
            }
            store.activate(request.planId)
            store.finalize(request.planId)
            publishReceipt(request.planId, mobile, assetId = "", complete = true)
            LibrarySyncResult.Completed(request.planId)
        } catch (error: LibrarySyncPullException) {
            LibrarySyncResult.Failed(error.failure)
        } catch (error: Throwable) {
            currentCoroutineContext().ensureActive()
            if (error is kotlinx.coroutines.CancellationException) throw error
            LibrarySyncResult.Failed(LibrarySyncFailure.Transport(error.message ?: "Sync failed"))
        }
    }

    private suspend fun publishReceipt(planId: String, mobile: MobileIdentity, assetId: String, complete: Boolean) {
        val unsigned = LibrarySyncReceipt(planId = planId, mobileId = mobile.id, assetId = assetId, complete = complete, issuedAt = clock.nowMillis(), signature = "")
        val signature = identityProvider.sign(LibrarySyncProtocol.receiptSigningInput(unsigned).encodeToByteArray())
        check(signature.size == 64) { "Unable to sign sync receipt" }
        val payload = LibrarySyncProtocol.json.encodeToString(
            LibrarySyncReceipt.serializer(), unsigned.copy(signature = Base64Url.encode(signature)),
        )
        receipts.publish(payload)
    }

    private fun validateRequest(request: LibrarySyncRequest, desktop: PairedDesktop, mobile: MobileIdentity): String? = when {
        request.version != ProtocolVersion || request.type != RequestType -> "Unsupported sync request"
        request.planId.isBlank() || request.manifestUrl.isBlank() -> "Missing sync request fields"
        request.desktopId != desktop.desktopId || request.mobileId != mobile.id -> "Foreign sync request"
        kotlin.math.abs(clock.nowMillis() - request.issuedAt) > ClockSkewMillis -> "Expired sync request"
        !Hash.matches(request.manifestHash) -> "Invalid manifest hash"
        else -> null
    }

    private fun validateManifest(manifest: LibrarySyncManifest, request: LibrarySyncRequest): String? {
        if (manifest.version != ProtocolVersion || manifest.planId != request.planId || !Hash.matches(manifest.revision)) return "Invalid manifest identity"
        val assetIds = mutableSetOf<String>()
        manifest.assets.orEmpty().forEach { asset ->
            if (!assetIds.add(asset.id) || asset.size < 0 || !Hash.matches(asset.sha256)) return "Invalid manifest asset"
            if ((asset.kind == "audio" && !asset.id.startsWith("audio:")) || (asset.kind == "artwork" && !asset.id.startsWith("artwork:"))) return "Invalid asset kind"
        }
        return null
    }

    private companion object { val Hash = Regex("^[0-9a-f]{64}$") }
}

private data class AssetContent(val sha256: String, val size: Long)

class LibrarySyncPullException(val failure: LibrarySyncFailure) : Exception()
