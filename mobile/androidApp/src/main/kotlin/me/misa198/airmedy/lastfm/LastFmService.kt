package me.misa198.airmedy.lastfm

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.misa198.airmedy.BuildConfig
import me.misa198.airmedy.lastfm.LastFmPlaybackEvent.NowPlaying
import me.misa198.airmedy.lastfm.LastFmPlaybackEvent.Scrobble
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.sync.metadataObject

private const val ApiBase = "https://ws.audioscrobbler.com/2.0/"
private const val AuthCallback = "airmedy://lastfm/auth"
private const val LogTag = "AirmedyLastFm"
private const val MaxAvatarBytes = 2 * 1024 * 1024
private val Context.lastFmDataStore by preferencesDataStore(name = "lastfm")
private val SessionKey = stringPreferencesKey("encrypted_session_key")
private val UsernameKey = stringPreferencesKey("username")
private val AvatarPathKey = stringPreferencesKey("avatar_path")

data class LastFmStatus(
    val connected: Boolean = false,
    val username: String = "",
    val working: Boolean = false,
    val configured: Boolean = true,
    val failed: Boolean = false,
    val avatarPath: String? = null,
)

data class LastFmTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumArtist: String = "",
    val trackNumber: Int = 0,
)

internal object AndroidLastFmRuntime {
    private var service: LastFmService? = null

    fun initialize(context: Context, library: AndroidLibrarySyncStore): LastFmService =
        service ?: synchronized(this) {
            service ?: LastFmService(
                context.applicationContext,
                BuildConfig.LASTFM_API_KEY,
                BuildConfig.LASTFM_API_SECRET,
            ) { trackId -> library.lastFmTrack(trackId) }.also { service = it }
        }

}

class LastFmService internal constructor(
    private val context: Context,
    private val apiKey: String,
    private val apiSecret: String,
    private val resolveTrack: suspend (String) -> LastFmTrack?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tracker = LastFmScrobbleTracker()
    private val configured = apiKey.isNotBlank() && apiSecret.isNotBlank()
    private val loaded = CompletableDeferred<Unit>()
    private val _status = MutableStateFlow(LastFmStatus(working = true, configured = configured))
    val status: StateFlow<LastFmStatus> = _status
    @Volatile
    private var sessionKey = ""
    private var playbackStartedAtSeconds = 0L

    init {
        scope.launch {
            try {
                loadSession()
            } finally {
                if (!_status.value.connected) _status.value = LastFmStatus(configured = configured)
                loaded.complete(Unit)
            }
        }
    }

    fun authorizationUrl(): String? {
        if (!_status.value.configured) return null
        _status.value = _status.value.copy(failed = false)
        return "https://www.last.fm/api/auth/?api_key=${encode(apiKey)}&cb=${encode(AuthCallback)}"
    }

    suspend fun completeAuthorization(uri: Uri) {
        loaded.await()
        val token = uri.getQueryParameter("token").orEmpty()
        if (!configured || !isLastFmAuthCallback(uri.scheme, uri.host, uri.path, token)) {
            _status.value = _status.value.copy(working = false, failed = true)
            return
        }
        _status.value = _status.value.copy(working = true, failed = false)
        runCatching {
            val response = call(mapOf("method" to "auth.getSession", "api_key" to apiKey, "token" to token))
            val session = response["session"]?.jsonObject ?: error("Missing Last.fm session")
            val key = session["key"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val username = session["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            require(key.isNotBlank() && username.isNotBlank()) { "Invalid Last.fm session" }
            saveSession(key, username)
            sessionKey = key
            _status.value = LastFmStatus(connected = true, username = username, configured = true)
            refreshAvatar(username)
        }.onFailure { error ->
            Log.w(LogTag, "Last.fm authorization failed", error)
            _status.value = _status.value.copy(working = false, failed = true)
        }
    }

    suspend fun disconnect() {
        loaded.await()
        context.lastFmDataStore.edit { values ->
            values.remove(SessionKey)
            values.remove(UsernameKey)
            values.remove(AvatarPathKey)
        }
        _status.value.avatarPath?.let { File(context.filesDir, it).delete() }
        sessionKey = ""
        _status.value = LastFmStatus(configured = apiKey.isNotBlank() && apiSecret.isNotBlank())
    }

    fun startPlayback(trackId: String, positionMs: Long = 0L) {
        tracker.start(trackId)
        playbackStartedAtSeconds = System.currentTimeMillis() / 1_000 - positionMs / 1_000
    }

    fun seek(positionMs: Long) {
        playbackStartedAtSeconds = System.currentTimeMillis() / 1_000 - positionMs / 1_000
    }

    fun reportPlayback(track: LastFmTrack, positionMs: Long, durationMs: Long) {
        if (sessionKey.isBlank()) return
        val startedAt = playbackStartedAtSeconds
        tracker.update(track.id, positionMs, durationMs, playing = true).forEach { event ->
            scope.launch {
                when (event) {
                    NowPlaying -> submit("track.updateNowPlaying", track, durationMs)
                    Scrobble -> submit("track.scrobble", track, durationMs, startedAt)
                }
            }
        }
    }

    suspend fun setLoved(trackId: String, loved: Boolean) {
        loaded.await()
        val track = resolveTrack(trackId) ?: return
        submit(if (loved) "track.love" else "track.unlove", track)
    }

    private suspend fun loadSession() {
        val values = context.lastFmDataStore.data.first()
        val encrypted = values[SessionKey]
        val username = values[UsernameKey].orEmpty()
        val avatarPath = values[AvatarPathKey]?.takeIf { File(context.filesDir, it).isFile }
        if (encrypted != null && username.isNotBlank()) {
            runCatching { decrypt(decode(encrypted)) }.onSuccess { key ->
                sessionKey = key
                _status.value = LastFmStatus(true, username, configured = configured, avatarPath = avatarPath)
                refreshAvatar(username)
            }.onFailure { error -> Log.w(LogTag, "Unable to restore Last.fm session", error) }
        }
    }

    private suspend fun saveSession(key: String, username: String) {
        context.lastFmDataStore.edit { values ->
            values[SessionKey] = encode(encrypt(key))
            values[UsernameKey] = username
        }
    }

    private suspend fun refreshAvatar(username: String) {
        runCatching {
            val avatarPath = lastFmAvatarUrl(
                call(mapOf("method" to "user.getInfo", "api_key" to apiKey, "user" to username)),
            )
                ?.let { downloadAvatar(it) }
            val previous = _status.value.avatarPath
            context.lastFmDataStore.edit { values ->
                if (avatarPath == null) values.remove(AvatarPathKey) else values[AvatarPathKey] = avatarPath
            }
            if (previous != null && previous != avatarPath) File(context.filesDir, previous).delete()
            _status.value = _status.value.copy(avatarPath = avatarPath)
        }.onFailure { error -> Log.w(LogTag, "Unable to refresh Last.fm avatar", error) }
    }

    private suspend fun downloadAvatar(value: String): String = withContext(Dispatchers.IO) {
        val url = URL(value)
        require(url.protocol == "https") { "Last.fm avatar must use HTTPS" }
        val name = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val relativePath = "lastfm/avatar/$name"
        val target = File(context.filesDir, relativePath)
        if (target.isFile) return@withContext relativePath

        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Airmedy-Android/${BuildConfig.VERSION_NAME}")
            require(connection.responseCode in 200..299 && connection.url.protocol == "https") {
                "Unable to download Last.fm avatar"
            }
            require(connection.contentLengthLong <= MaxAvatarBytes) { "Last.fm avatar is too large" }
            val bytes = connection.inputStream.use { it.readNBytes(MaxAvatarBytes + 1) }
            require(bytes.size <= MaxAvatarBytes) { "Last.fm avatar is too large" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Last.fm avatar is not an image" }
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "$name.tmp")
            temporary.writeBytes(bytes)
            check(temporary.renameTo(target)) { "Unable to save Last.fm avatar" }
            relativePath
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun submit(method: String, track: LastFmTrack, durationMs: Long = 0L, timestamp: Long? = null) {
        val session = sessionKey
        if (session.isBlank() || track.artist.isBlank() || track.title.isBlank()) return
        val params = mutableMapOf(
            "method" to method,
            "api_key" to apiKey,
            "sk" to session,
            "artist" to track.artist,
            "track" to track.title,
        )
        track.album.takeIf { it.isNotBlank() && it != "Unknown Album" }?.let { params["album"] = it }
        track.albumArtist.takeIf(String::isNotBlank)?.let { params["albumArtist"] = it }
        track.trackNumber.takeIf { it > 0 }?.let { params["trackNumber"] = it.toString() }
        durationMs.takeIf { it > 0 }?.let { params["duration"] = (it / 1_000).toString() }
        timestamp?.let { params["timestamp"] = it.toString() }
        runCatching { call(params) }.onFailure { error -> Log.w(LogTag, "Last.fm $method failed", error) }
    }

    private suspend fun call(unsigned: Map<String, String>): JsonObject = withContext(Dispatchers.IO) {
        val params = unsigned + ("api_sig" to lastFmSignature(unsigned, apiSecret)) + ("format" to "json")
        val body = params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val connection = URL(ApiBase).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("User-Agent", "Airmedy-Android/${BuildConfig.VERSION_NAME}")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val text = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val response = Json.parseToJsonElement(text).jsonObject
            response["error"]?.jsonPrimitive?.intOrNull?.let { code ->
                error("Last.fm error $code: ${response["message"]?.jsonPrimitive?.contentOrNull.orEmpty()}")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun encrypt(value: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    }

    private fun decrypt(bytes: ByteArray): String {
        require(bytes.size > 12) { "Invalid Last.fm session" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }

    private fun keystoreKey(): javax.crypto.SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "airmedy.mobile.lastfm.session.v1"
        (store.getKey(alias, null) as? javax.crypto.SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }
}

internal fun lastFmSignature(params: Map<String, String>, secret: String): String {
    val input = params.filterKeys { it != "format" && it != "callback" && it != "api_sig" }
        .toSortedMap()
        .entries
        .joinToString("") { (key, value) -> key + value } + secret
    return MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

internal fun isLastFmAuthCallback(scheme: String?, host: String?, path: String?, token: String?): Boolean =
    scheme == "airmedy" && host == "lastfm" && path == "/auth" && !token.isNullOrBlank()

internal fun lastFmAvatarUrl(response: JsonObject): String? =
    (response["user"]?.jsonObject?.get("image") as? JsonArray)
        ?.mapNotNull { (it as? JsonObject)?.get("#text")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) }
        ?.lastOrNull()

private suspend fun AndroidLibrarySyncStore.lastFmTrack(trackId: String): LastFmTrack? {
    val track = tracks.first().firstOrNull { it.id == trackId } ?: return null
    val metadata = track.metadataObject()
    fun firstName(key: String): String = ((metadata?.get(key) as? JsonArray)?.firstOrNull() as? JsonObject)
        ?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
    return LastFmTrack(
        id = track.id,
        title = track.title,
        artist = firstName("artists").ifBlank { track.artists.substringBefore(",").trim() },
        album = track.album,
        albumArtist = firstName("album_artists"),
        trackNumber = track.trackNumber,
    )
}

private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
