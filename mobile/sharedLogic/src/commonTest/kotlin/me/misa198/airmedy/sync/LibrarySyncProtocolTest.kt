package me.misa198.airmedy.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import me.misa198.airmedy.pairing.MobileIdentity
import me.misa198.airmedy.pairing.PairingIdentityProvider
import me.misa198.airmedy.pairing.PairedDesktop
import me.misa198.airmedy.pairing.Base64Url

class LibrarySyncProtocolTest {
    @Test
    fun requestSigningInputUsesDesktopFieldOrderAndEmptySignature() {
        val request = LibrarySyncRequest(
            version = 1,
            type = "library.sync.request",
            planId = "plan",
            desktopId = "desktop",
            mobileId = "mobile",
            manifestUrl = "http://10.0.0.2/manifest",
            manifestHash = "a".repeat(64),
            issuedAt = 42,
            signature = "signature",
        )

        assertEquals(
            "{\"version\":1,\"type\":\"library.sync.request\",\"plan_id\":\"plan\",\"desktop_id\":\"desktop\",\"mobile_id\":\"mobile\",\"manifest_url\":\"http://10.0.0.2/manifest\",\"manifest_hash\":\"${"a".repeat(64)}\",\"issued_at\":42,\"signature\":\"\"}",
            LibrarySyncProtocol.requestSigningInput(request),
        )
    }

    @Test
    fun usesDeviceScopedV1Topics() {
        assertEquals("airmedy/library-sync/v1/desktop/mobile/request", LibrarySyncProtocol.requestTopic("desktop", "mobile"))
        assertEquals("airmedy/library-sync/v1/desktop/mobile/receipt", LibrarySyncProtocol.receiptTopic("desktop", "mobile"))
    }

    @Test
    fun errorReceiptSigningInputKeepsZeroAvailableBytes() {
        val receipt = LibrarySyncReceipt(
            planId = "plan", mobileId = "mobile", assetId = "", complete = false,
            errorCode = "insufficient_storage", requiredBytes = 12, availableBytes = 0,
            issuedAt = 42, signature = "signature",
        )

        assertEquals(
            "{\"version\":1,\"type\":\"library.sync.receipt\",\"plan_id\":\"plan\",\"mobile_id\":\"mobile\",\"asset_id\":\"\",\"complete\":false,\"error_code\":\"insufficient_storage\",\"required_bytes\":12,\"available_bytes\":0,\"issued_at\":42,\"signature\":\"\"}",
            LibrarySyncProtocol.receiptSigningInput(receipt),
        )
    }

    @Test
    fun decodesDesktopManifestWithNullOptionalCollections() {
        val manifest = LibrarySyncProtocol.json.decodeFromString(
            LibrarySyncManifest.serializer(),
            """{"version":1,"plan_id":"plan","revision":"${"b".repeat(64)}","scope":{},"tracks":[],"playlists":null,"lyrics":{},"analysis":{},"assets":[]}""",
        )

        assertEquals(emptyList(), manifest.playlists.orEmpty())
        assertEquals(false, manifest.libraryAnalysisEnabled)
    }

    @Test
    fun roundTripsLibraryAnalysisFlag() {
        val manifest = LibrarySyncManifest(1, "plan", "b".repeat(64), buildJsonObject { }, lyrics = buildJsonObject { }, analysis = buildJsonObject { }, libraryAnalysisEnabled = true)
        assertEquals(true, LibrarySyncProtocol.json.decodeFromString(LibrarySyncManifest.serializer(), LibrarySyncProtocol.json.encodeToString(LibrarySyncManifest.serializer(), manifest)).libraryAnalysisEnabled)
    }

    @Test
    fun coordinatorActivatesThenPublishesFinalReceipt() = kotlinx.coroutines.test.runTest {
        val manifest = LibrarySyncManifest(1, "plan", "b".repeat(64), buildJsonObject { }, emptyList(), emptyList(), buildJsonObject { }, buildJsonObject { }, emptyList())
        val events = mutableListOf<String>()
        val coordinator = LibrarySyncCoordinator(
            identityProvider = FakeIdentity,
            clock = LibrarySyncClock { 42 },
            puller = object : LibrarySyncPuller {
                override suspend fun manifest(request: LibrarySyncRequest) = PulledManifest(LibrarySyncProtocol.json.encodeToString(LibrarySyncManifest.serializer(), manifest), "a".repeat(64))
                override suspend fun asset(request: LibrarySyncRequest, asset: LibrarySyncAsset): PulledAsset = error("no assets")
            },
            store = object : LibrarySyncStore {
                override suspend fun prepare(request: LibrarySyncRequest, manifest: LibrarySyncManifest) { events += "prepare" }
                override suspend fun isAssetCommitted(planId: String, asset: LibrarySyncAsset) = false
                override suspend fun stageAsset(planId: String, asset: LibrarySyncAsset, pulled: PulledAsset) = Unit
                override suspend fun activate(planId: String): List<String> { events += "activate"; return emptyList() }
                override suspend fun finalize(planId: String) { events += "finalize" }
                override suspend fun discard(planId: String) = Unit
            },
            receipts = LibrarySyncReceiptPublisher { events += "receipt:$it" },
        )
        val request = LibrarySyncRequest(1, "library.sync.request", "plan", "desktop", "mobile", "http://desktop/manifest", "a".repeat(64), 42, Base64Url.encode(ByteArray(64)))

        val result = coordinator.handle(LibrarySyncProtocol.json.encodeToString(LibrarySyncRequest.serializer(), request), PairedDesktop("desktop", "Desktop", ByteArray(32)))

        assertIs<LibrarySyncResult.Completed>(result)
        assertEquals(listOf("prepare", "activate", "finalize"), events.take(3))
        assertEquals(true, events.last().contains("\"complete\":true"))
    }

    @Test
    fun coordinatorDownloadsDistinctContentInBoundedParallelAndDeduplicatesHashes() = kotlinx.coroutines.test.runTest {
        val assets = listOf(
            LibrarySyncAsset("audio:one", "audio", "1".repeat(64), 1),
            LibrarySyncAsset("artwork:one", "artwork", "1".repeat(64), 1),
            LibrarySyncAsset("audio:two", "audio", "2".repeat(64), 1),
            LibrarySyncAsset("audio:three", "audio", "3".repeat(64), 1),
        )
        val manifest = LibrarySyncManifest(1, "plan", "b".repeat(64), buildJsonObject { }, emptyList(), emptyList(), buildJsonObject { }, buildJsonObject { }, assets)
        var activePulls = 0
        var maxPulls = 0
        val pulled = mutableListOf<String>()
        val staged = mutableListOf<String>()
        val receipts = mutableListOf<LibrarySyncReceipt>()
        val coordinator = LibrarySyncCoordinator(
            identityProvider = FakeIdentity,
            clock = LibrarySyncClock { 42 },
            puller = object : LibrarySyncPuller {
                override suspend fun manifest(request: LibrarySyncRequest) = PulledManifest(LibrarySyncProtocol.json.encodeToString(LibrarySyncManifest.serializer(), manifest), "a".repeat(64))
                override suspend fun asset(request: LibrarySyncRequest, asset: LibrarySyncAsset): PulledAsset {
                    activePulls += 1
                    maxPulls = maxOf(maxPulls, activePulls)
                    delay(1)
                    activePulls -= 1
                    pulled += asset.id
                    return PulledAsset("library-sync/assets/${asset.sha256}", asset.sha256, asset.size)
                }
            },
            store = object : LibrarySyncStore {
                override suspend fun prepare(request: LibrarySyncRequest, manifest: LibrarySyncManifest) = Unit
                override suspend fun isAssetCommitted(planId: String, asset: LibrarySyncAsset) = false
                override suspend fun stageAsset(planId: String, asset: LibrarySyncAsset, pulled: PulledAsset) { staged += asset.id }
                override suspend fun activate(planId: String) = emptyList<String>()
                override suspend fun finalize(planId: String) = Unit
                override suspend fun discard(planId: String) = Unit
            },
            receipts = LibrarySyncReceiptPublisher { receipts += LibrarySyncProtocol.json.decodeFromString(LibrarySyncReceipt.serializer(), it) },
            assetParallelism = 2,
        )

        val result = coordinator.handle(requestPayload(), PairedDesktop("desktop", "Desktop", ByteArray(32)))

        assertIs<LibrarySyncResult.Completed>(result)
        assertEquals(listOf("audio:one", "audio:two", "audio:three"), pulled)
        assertEquals(2, maxPulls)
        assertEquals(assets.map(LibrarySyncAsset::id), staged)
        assertEquals(assets.map(LibrarySyncAsset::id), receipts.filterNot(LibrarySyncReceipt::complete).map(LibrarySyncReceipt::assetId))
    }

    @Test
    fun failedParallelBatchDoesNotActivateOrPublishReceipts() = kotlinx.coroutines.test.runTest {
        val assets = listOf(
            LibrarySyncAsset("audio:one", "audio", "1".repeat(64), 1),
            LibrarySyncAsset("audio:two", "audio", "2".repeat(64), 1),
        )
        val manifest = LibrarySyncManifest(1, "plan", "b".repeat(64), buildJsonObject { }, emptyList(), emptyList(), buildJsonObject { }, buildJsonObject { }, assets)
        var activated = false
        val receipts = mutableListOf<String>()
        val coordinator = LibrarySyncCoordinator(
            identityProvider = FakeIdentity,
            clock = LibrarySyncClock { 42 },
            puller = object : LibrarySyncPuller {
                override suspend fun manifest(request: LibrarySyncRequest) = PulledManifest(LibrarySyncProtocol.json.encodeToString(LibrarySyncManifest.serializer(), manifest), "a".repeat(64))
                override suspend fun asset(request: LibrarySyncRequest, asset: LibrarySyncAsset): PulledAsset {
                    if (asset.id == "audio:two") throw LibrarySyncPullException(LibrarySyncFailure.Transport("offline"))
                    delay(1)
                    return PulledAsset("library-sync/assets/${asset.sha256}", asset.sha256, asset.size)
                }
            },
            store = object : LibrarySyncStore {
                override suspend fun prepare(request: LibrarySyncRequest, manifest: LibrarySyncManifest) = Unit
                override suspend fun isAssetCommitted(planId: String, asset: LibrarySyncAsset) = false
                override suspend fun stageAsset(planId: String, asset: LibrarySyncAsset, pulled: PulledAsset) = Unit
                override suspend fun activate(planId: String): List<String> { activated = true; return emptyList() }
                override suspend fun finalize(planId: String) = Unit
                override suspend fun discard(planId: String) = Unit
            },
            receipts = LibrarySyncReceiptPublisher { receipts += it },
            assetParallelism = 2,
        )

        assertIs<LibrarySyncResult.Failed>(coordinator.handle(requestPayload(), PairedDesktop("desktop", "Desktop", ByteArray(32))))
        assertEquals(false, activated)
        assertEquals(emptyList(), receipts)
    }

    @Test
    fun storagePreflightDeduplicatesContentAndSkipsCachedAssets() = kotlinx.coroutines.test.runTest {
        val shared = LibrarySyncAsset("audio:one", "audio", "1".repeat(64), 8)
        val result = storagePreflight(
            listOf(shared, shared.copy(id = "artwork:one", kind = "artwork"), LibrarySyncAsset("audio:two", "audio", "2".repeat(64), 5)),
            cachedHashes = setOf(shared.sha256),
            availableBytes = 5,
        )
        assertIs<LibrarySyncResult.Completed>(result.result)
        assertEquals(1, result.pulls)
    }

    @Test
    fun storagePreflightAllowsAnExactFit() = kotlinx.coroutines.test.runTest {
        val result = storagePreflight(listOf(LibrarySyncAsset("audio:one", "audio", "1".repeat(64), 5)), availableBytes = 5)
        assertIs<LibrarySyncResult.Completed>(result.result)
    }

    @Test
    fun insufficientStorageDoesNotPullAndPublishesErrorReceipt() = kotlinx.coroutines.test.runTest {
        val result = storagePreflight(listOf(LibrarySyncAsset("audio:one", "audio", "1".repeat(64), 6)), availableBytes = 5)
        val failure = assertIs<LibrarySyncFailure.InsufficientStorage>(assertIs<LibrarySyncResult.Failed>(result.result).failure)
        assertEquals(6, failure.requiredBytes)
        assertEquals(5, failure.availableBytes)
        assertEquals(0, result.pulls)
        assertEquals(true, result.discarded)
        assertEquals("insufficient_storage", result.receipts.single().errorCode)
    }

    @Test
    fun storagePreflightSaturatesOverflow() = kotlinx.coroutines.test.runTest {
        val result = storagePreflight(
            listOf(
                LibrarySyncAsset("audio:one", "audio", "1".repeat(64), Long.MAX_VALUE),
                LibrarySyncAsset("audio:two", "audio", "2".repeat(64), 1),
            ),
            availableBytes = Long.MAX_VALUE,
        )
        assertEquals(Long.MAX_VALUE, assertIs<LibrarySyncFailure.InsufficientStorage>(assertIs<LibrarySyncResult.Failed>(result.result).failure).requiredBytes)
    }

    private data class StoragePreflightResult(val result: LibrarySyncResult, val pulls: Int, val discarded: Boolean, val receipts: List<LibrarySyncReceipt>)

    private suspend fun storagePreflight(
        assets: List<LibrarySyncAsset>,
        cachedHashes: Set<String> = emptySet(),
        availableBytes: Long,
    ): StoragePreflightResult {
        val manifest = LibrarySyncManifest(1, "plan", "b".repeat(64), buildJsonObject { }, emptyList(), emptyList(), buildJsonObject { }, buildJsonObject { }, assets)
        var pulls = 0
        var discarded = false
        val receipts = mutableListOf<LibrarySyncReceipt>()
        val coordinator = LibrarySyncCoordinator(
            identityProvider = FakeIdentity,
            clock = LibrarySyncClock { 42 },
            puller = object : LibrarySyncPuller {
                override suspend fun manifest(request: LibrarySyncRequest) = PulledManifest(LibrarySyncProtocol.json.encodeToString(LibrarySyncManifest.serializer(), manifest), "a".repeat(64))
                override suspend fun asset(request: LibrarySyncRequest, asset: LibrarySyncAsset): PulledAsset {
                    pulls++
                    return PulledAsset("asset", asset.sha256, asset.size)
                }
            },
            store = object : LibrarySyncStore {
                override suspend fun prepare(request: LibrarySyncRequest, manifest: LibrarySyncManifest) = Unit
                override suspend fun isAssetCommitted(planId: String, asset: LibrarySyncAsset) = asset.sha256 in cachedHashes
                override suspend fun cachedAssetContents() = assets.filter { it.sha256 in cachedHashes }
                    .map { LibrarySyncAssetContent(it.sha256, it.size) }.toSet()
                override suspend fun stageAsset(planId: String, asset: LibrarySyncAsset, pulled: PulledAsset) = Unit
                override suspend fun activate(planId: String) = emptyList<String>()
                override suspend fun finalize(planId: String) = Unit
                override suspend fun discard(planId: String) { discarded = true }
            },
            receipts = LibrarySyncReceiptPublisher { receipts += LibrarySyncProtocol.json.decodeFromString(LibrarySyncReceipt.serializer(), it) },
            capacity = LibrarySyncCapacity { availableBytes },
        )
        return StoragePreflightResult(coordinator.handle(requestPayload(), PairedDesktop("desktop", "Desktop", ByteArray(32))), pulls, discarded, receipts)
    }

    private fun requestPayload(): String {
        val request = LibrarySyncRequest(1, "library.sync.request", "plan", "desktop", "mobile", "http://desktop/manifest", "a".repeat(64), 42, Base64Url.encode(ByteArray(64)))
        return LibrarySyncProtocol.json.encodeToString(LibrarySyncRequest.serializer(), request)
    }

    private object FakeIdentity : PairingIdentityProvider {
        override suspend fun identity() = MobileIdentity("mobile", "Phone", "Android", ByteArray(32))
        override suspend fun randomBytes(size: Int) = ByteArray(size)
        override suspend fun sign(input: ByteArray) = ByteArray(64)
        override suspend fun verify(publicKey: ByteArray, input: ByteArray, signature: ByteArray) = true
    }
}
