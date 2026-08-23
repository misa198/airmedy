package me.misa198.airmedy.lyrics

import android.util.Base64
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.misa198.airmedy.BuildConfig
import me.misa198.airmedy.lyrics.LyricsCandidate
import me.misa198.airmedy.lyrics.bestLyricsCandidate
import me.misa198.airmedy.lyrics.normalizeLyricsText
import me.misa198.airmedy.lyrics.removeFeaturedLyricsTitle
import me.misa198.airmedy.sync.AndroidLibrarySyncStore

internal data class FetchedLyric(val content: String, val source: String)

internal abstract class LyricsProvider {
    abstract fun enabled(settings: LyricsSettings): Boolean
    abstract suspend fun fetch(track: LyricsTrack): FetchedLyric?
}

internal class AndroidLyricsService(
    private val library: AndroidLibrarySyncStore,
    private val providers: List<LyricsProvider> = listOf(
        LrclibLyricsProvider(),
        KugouLyricsProvider()
    ),
) {
    suspend fun fetch(trackId: String, settings: LyricsSettings) {
        val active = providers.filter { it.enabled(settings) }
        if (active.isEmpty()) return
        val track = library.lyricsTrack(trackId) ?: run {
            Log.w("AirmedyLyrics", "Cannot fetch lyrics: track $trackId is unavailable")
            return
        }
        Log.d("AirmedyLyrics", "Fetching lyrics for ${track.title} from ${active.size} provider(s)")
        val lyric = coroutineScope {
            val results = Channel<Result<FetchedLyric?>>(active.size)
            val jobs =
                active.map { provider -> async { results.send(runCatching { provider.fetch(track) }) } }
            repeat(active.size) {
                val result = results.receive()
                result.exceptionOrNull()?.let { Log.w("AirmedyLyrics", "Lyrics provider failed", it) }
                result.getOrNull()?.let {
                    jobs.forEach { job -> job.cancel() }
                    return@coroutineScope it
                }
            }
            null
        }
        lyric?.let {
            library.saveProviderLyrics(trackId, it.content, it.source)
            Log.d("AirmedyLyrics", "Fetched lyrics from ${it.source}")
        } ?: Log.d("AirmedyLyrics", "No lyrics found for ${track.title}")
    }

}

internal class LrclibLyricsProvider : LyricsProvider() {
    override fun enabled(settings: LyricsSettings) = settings.lrclib
    override suspend fun fetch(track: LyricsTrack): FetchedLyric? {
        val title = normalizeLyricsText(removeFeaturedLyricsTitle(track.title));
        val artist = normalizeLyricsText(track.artist)
        exact(title, artist, track.album, track.duration)?.let { return it }
        if (track.album.isNotBlank()) exact(title, artist, "", track.duration)?.let { return it }
        val candidates = request(
            "https://lrclib.net/api/search",
            mapOf("track_name" to title, "artist_name" to artist)
        ) ?: return null
        val values = ProviderJson.decodeFromString(ListSerializer, candidates)
        val best = bestLyricsCandidate(values.map {
            LyricsCandidate(
                it.trackName,
                it.artistName,
                it.duration
            )
        }, title, artist, track.duration) ?: return null
        return values.firstOrNull { it.trackName == best.title && it.artistName == best.artist && it.duration == best.durationSeconds }
            ?.toLyric()
    }

    private suspend fun exact(
        title: String,
        artist: String,
        album: String,
        duration: Int
    ): FetchedLyric? {
        val params = mutableMapOf(
            "track_name" to title,
            "artist_name" to artist
        ).apply {
            if (album.isNotBlank()) put(
                "album_name",
                album
            ); if (duration > 0) put("duration", duration.toString())
        }
        return request(
            "https://lrclib.net/api/get",
            params
        )?.let {
            ProviderJson.decodeFromString(LrclibCandidate.serializer(), it).toLyric()
        }
    }

    private fun LrclibCandidate.toLyric(): FetchedLyric? = syncedLyrics.takeIf(String::isNotBlank)
        ?.let {
            FetchedLyric(
                decodeLyricsHtml(it),
                "lrclib-synced"
            )
        } ?: plainLyrics.takeIf(String::isNotBlank)?.let {
        FetchedLyric(
            decodeLyricsHtml(it),
            "lrclib-plain"
        )
    }
}

internal class KugouLyricsProvider : LyricsProvider() {
    override fun enabled(settings: LyricsSettings) = settings.kugou
    override suspend fun fetch(track: LyricsTrack): FetchedLyric? {
        var title = normalizeLyricsText(removeFeaturedLyricsTitle(track.title));
        var artist = normalizeLyricsText(track.artist)
        repeat(3) { attempt ->
            val candidates = kugouSearch("$artist - $title", track.duration * 1000)
            val best = bestLyricsCandidate(candidates.map {
                LyricsCandidate(
                    it.song,
                    it.singer,
                    it.duration / 1000.0,
                    it.score
                )
            }, title, artist, track.duration)
            candidates.firstOrNull { best != null && it.song == best.title && it.singer == best.artist && it.duration / 1000.0 == best.durationSeconds }
                ?.let { candidate ->
                    kugouDownload(
                        candidate.id,
                        candidate.accesskey
                    )?.let { content ->
                        return FetchedLyric(
                            content,
                            if (SyncedLrc.containsMatchIn(content)) "kugou-synced" else "kugou-plain"
                        )
                    }
                }
            if (attempt == 0) {
                title = Brackets.replace(title, ""); artist = Brackets.replace(artist, "")
            } else if (attempt == 1) {
                val old = title; title = artist; artist = old
            }
        }
        return null
    }

    private suspend fun kugouSearch(keyword: String, duration: Int): List<KugouCandidate> = request(
        "http://krcs.kugou.com/search",
        mapOf(
            "ver" to "1",
            "man" to "yes",
            "client" to "mobi",
            "keyword" to keyword,
            "duration" to duration.toString(),
            "hash" to "",
            "album_audio_id" to ""
        )
    )
        ?.let { ProviderJson.decodeFromString(KugouSearch.serializer(), it).candidates }
        ?: emptyList()

    private suspend fun kugouDownload(id: String, key: String): String? = request(
        "https://lyrics.kugou.com/download",
        mapOf(
            "ver" to "1",
            "client" to "pc",
            "id" to id,
            "accesskey" to key,
            "fmt" to "lrc",
            "charset" to "utf8"
        )
    )
        ?.let { ProviderJson.decodeFromString(KugouDownload.serializer(), it).content }
        ?.let { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) }

}

private suspend fun request(base: String, params: Map<String, String>): String? =
    withContext(Dispatchers.IO) {
        val query = params.entries.joinToString("&") {
            "${
                URLEncoder.encode(
                    it.key,
                    "UTF-8"
                )
            }=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        (URL("$base?$query").openConnection() as HttpURLConnection).run {
            try {
                connectTimeout = 30_000; readTimeout = 30_000; setRequestProperty(
                    "User-Agent",
                    "Airmedy-Android/${BuildConfig.VERSION_NAME}"
                ); if (responseCode !in 200..299) null else inputStream.bufferedReader()
                    .use { it.readText() }
            } finally {
                disconnect()
            }
        }
    }

private val Brackets = Regex("[(（【〔\\[{｛][^)）】〕\\]}｝]*[)）】〕\\]}｝]")
private val SyncedLrc = Regex("(?m)^\\[\\d{2}:\\d{2}\\.\\d+]")
private val HtmlEntity = Regex("&(#(?:x[0-9a-fA-F]+|\\d+)|amp|apos|lt|gt|quot|nbsp);")
private val ListSerializer =
    kotlinx.serialization.builtins.ListSerializer(LrclibCandidate.serializer())
internal val ProviderJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** Matches desktop html.UnescapeString without treating LRC line breaks as HTML whitespace. */
internal fun decodeLyricsHtml(content: String): String = HtmlEntity.replace(content) { match ->
    when (val entity = match.groupValues[1]) {
        "amp" -> "&"
        "apos" -> "'"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "nbsp" -> " "
        else -> entity.removePrefix("#").let { value ->
            val radix = if (value.startsWith("x", ignoreCase = true)) 16 else 10
            value.removePrefix("x").removePrefix("X").toIntOrNull(radix)?.toChar()?.toString() ?: match.value
        }
    }
}

internal data class LyricsTrack(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int
)

@Serializable
internal data class LrclibCandidate(
    val trackName: String = "",
    val artistName: String = "",
    val duration: Double = 0.0,
    val syncedLyrics: String = "",
    val plainLyrics: String = ""
)

@Serializable
private data class KugouSearch(val candidates: List<KugouCandidate> = emptyList())
@Serializable
private data class KugouCandidate(
    val id: String,
    val accesskey: String,
    val singer: String = "",
    val song: String = "",
    val duration: Int = 0,
    val score: Int = 0
)

@Serializable
private data class KugouDownload(val content: String = "")
