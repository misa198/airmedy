package me.misa198.airmedy.sync

import android.util.Base64
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.misa198.airmedy.pairing.PairingIdentityProvider
import me.misa198.airmedy.sync.LibrarySyncAsset
import me.misa198.airmedy.sync.LibrarySyncFailure
import me.misa198.airmedy.sync.LibrarySyncPullException
import me.misa198.airmedy.sync.LibrarySyncPuller
import me.misa198.airmedy.sync.LibrarySyncRequest
import me.misa198.airmedy.sync.PulledAsset
import me.misa198.airmedy.sync.PulledManifest

internal class AndroidLibrarySyncPuller(
    private val identity: PairingIdentityProvider,
    private val filesDir: File,
) : LibrarySyncPuller {
    override suspend fun manifest(request: LibrarySyncRequest): PulledManifest = withContext(Dispatchers.IO) {
        val uri = request.manifestUrl.toUriOrThrow()
        Log.i(LogTag, "Pulling manifest from $uri")
        val bytes = get(uri, request.mobileId)
        val hash = bytes.sha256()
        Log.i(LogTag, "Manifest pulled successfully (size=${bytes.size} bytes, sha256=$hash)")
        PulledManifest(bytes.decodeToString(), hash)
    }

    override suspend fun asset(request: LibrarySyncRequest, asset: LibrarySyncAsset): PulledAsset = withContext(Dispatchers.IO) {
        val uri = "${request.manifestUrl.substringBeforeLast('/')}/assets/${asset.id}".toUriOrThrow()
        Log.d(LogTag, "Pulling asset ${asset.id} (${asset.size} bytes) from $uri")
        val destination = File(filesDir, "library-sync/assets/${asset.sha256}")
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, ".${destination.name}.part")
        val result = getToFile(uri, request.mobileId, temporary)
        if (!result.headerHash.equals(asset.sha256, ignoreCase = true) || !result.sha256.equals(asset.sha256, ignoreCase = true) || result.size != asset.size) {
            temporary.delete()
            Log.e(LogTag, "Asset verification failed for ${asset.id}: expected sha256=${asset.sha256} size=${asset.size}, got sha256=${result.sha256} size=${result.size}")
            throw LibrarySyncPullException(LibrarySyncFailure.HashMismatch)
        }
        if (destination.exists()) destination.delete()
        check(temporary.renameTo(destination)) { "Unable to commit downloaded asset" }
        Log.d(LogTag, "Committed asset ${asset.id} to ${destination.relativeTo(filesDir).path}")
        PulledAsset(destination.relativeTo(filesDir).path, result.sha256, result.size)
    }

    private suspend fun get(uri: URI, expectedMobileId: String): ByteArray {
        repeat(2) { attempt ->
            Log.d(LogTag, "HTTP GET request to $uri (attempt ${attempt + 1})")
            val connection = open(uri, expectedMobileId)
            try {
                when (val code = connection.responseCode) {
                    in 200..299 -> {
                        val data = connection.inputStream.use { it.readBytes() }
                        Log.d(LogTag, "HTTP GET $uri -> $code (${data.size} bytes)")
                        return data
                    }
                    401 -> {
                        Log.w(LogTag, "HTTP GET $uri -> 401 Unauthorized (attempt ${attempt + 1})")
                        if (attempt == 0) return@repeat else throw LibrarySyncPullException(LibrarySyncFailure.Transport("Desktop rejected sync credentials"))
                    }
                    404 -> {
                        Log.w(LogTag, "HTTP GET $uri -> 404 Superseded")
                        throw LibrarySyncPullException(LibrarySyncFailure.Superseded)
                    }
                    else -> {
                        Log.e(LogTag, "HTTP GET $uri failed with status $code")
                        throw LibrarySyncPullException(LibrarySyncFailure.Transport("Manifest download failed ($code)"))
                    }
                }
            } finally { connection.disconnect() }
        }
        error("unreachable")
    }

    private suspend fun getToFile(uri: URI, expectedMobileId: String, destination: File): DownloadedFile {
        repeat(2) { attempt ->
            Log.d(LogTag, "HTTP GET file to $destination from $uri (attempt ${attempt + 1})")
            val connection = open(uri, expectedMobileId)
            try {
                when (val code = connection.responseCode) {
                    in 200..299 -> return connection.inputStream.use { input ->
                        destination.outputStream().use { output ->
                            val digest = MessageDigest.getInstance("SHA-256")
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var size = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                size += read
                            }
                            output.fd.sync()
                            val downloadedHash = hex(digest.digest())
                            val headerHash = connection.getHeaderField("X-Airmedy-SHA256") ?: ""
                            Log.d(LogTag, "HTTP GET file $uri -> $code ($size bytes, sha256=$downloadedHash)")
                            DownloadedFile(downloadedHash, headerHash, size)
                        }
                    }
                    401 -> {
                        Log.w(LogTag, "HTTP GET file $uri -> 401 Unauthorized (attempt ${attempt + 1})")
                        if (attempt == 0) return@repeat else throw LibrarySyncPullException(LibrarySyncFailure.Transport("Desktop rejected sync credentials"))
                    }
                    404 -> {
                        Log.w(LogTag, "HTTP GET file $uri -> 404 Superseded")
                        throw LibrarySyncPullException(LibrarySyncFailure.Superseded)
                    }
                    else -> {
                        Log.e(LogTag, "HTTP GET file $uri failed with status $code")
                        throw LibrarySyncPullException(LibrarySyncFailure.Transport("Asset download failed ($code)"))
                    }
                }
            } finally { connection.disconnect() }
        }
        error("unreachable")
    }

    private suspend fun open(uri: URI, expectedMobileId: String): HttpURLConnection {
        val mobile = identity.identity()
        if (mobile.id != expectedMobileId) throw LibrarySyncPullException(LibrarySyncFailure.Transport("Pairing identity changed"))
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val nonceBytes = identity.randomBytes(32)
        val nonce = nonceBytes.base64Url()
        val emptyBodyHash = ByteArray(0).sha256()
        val input = "GET\n${uri.rawPath}\n$emptyBodyHash\n$timestamp\n$nonce".encodeToByteArray()
        val signature = identity.sign(input).base64Url()
        return (uri.toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("X-Airmedy-Mobile-ID", mobile.id)
            setRequestProperty("X-Airmedy-Timestamp", timestamp)
            setRequestProperty("X-Airmedy-Nonce", nonce)
            setRequestProperty("X-Airmedy-Signature", signature)
        }
    }

    private fun String.toUriOrThrow(): URI = runCatching { URI(this) }.getOrElse {
        throw LibrarySyncPullException(LibrarySyncFailure.Transport("Invalid desktop sync URL"))
    }.takeIf { it.scheme == "http" && it.host != null } ?: throw LibrarySyncPullException(LibrarySyncFailure.Transport("Invalid desktop sync URL"))

    private fun ByteArray.sha256(): String = hex(MessageDigest.getInstance("SHA-256").digest(this))
    private fun ByteArray.base64Url(): String = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private data class DownloadedFile(val sha256: String, val headerHash: String, val size: Long)

    private companion object {
        private const val LogTag = "AirmedySyncPuller"
    }
}
