package me.misa198.airmedy.player

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.metadataObject

/** Application-scoped composition root for Android playback. */
internal object AndroidPlaybackRuntime {
    private lateinit var appContext: Context
    private lateinit var syncStore: AndroidLibrarySyncStore
    private lateinit var resolver: PlaybackItemResolver

    fun initialize(context: Context, syncStore: AndroidLibrarySyncStore) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        this.syncStore = syncStore
        resolver = PlaybackItemResolver { trackId ->
            val track = syncStore.tracks.first().firstOrNull { it.id == trackId }
            if (track == null) {
                Log.w(PlaybackLogTag, "Resolve failed: track is not in synced library id=$trackId")
                null
            } else {
                val storedPath = track.audioPath
                val audio = resolveSyncedAudioFile(appContext.filesDir, storedPath)
                if (audio?.isFile != true) {
                    Log.w(PlaybackLogTag, "Resolve failed: audio asset missing id=$trackId storedPath=$storedPath resolvedPath=${audio?.absolutePath}")
                    null
                } else {
                    val artwork = resolveSyncedAudioFile(appContext.filesDir, track.artworkPath)
                        ?.takeIf(File::isFile)
                        ?.absolutePath
                    Log.d(PlaybackLogTag, "Resolved audio id=$trackId path=${audio.absolutePath} artwork=${artwork != null}")
                    val metadata = track.metadataObject()
                    fun firstName(key: String): String = ((metadata?.get(key) as? JsonArray)?.firstOrNull() as? JsonObject)
                        ?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                    PlaybackItem(
                        trackId = track.id,
                        title = track.title,
                        artist = playbackArtistNames(metadata, track.artists),
                        audioPath = audio.absolutePath,
                        artworkPath = artwork,
                        albumId = track.albumId,
                        analysis = syncStore.analysis(track.id),
                        album = track.album,
                        albumArtist = firstName("album_artists"),
                        trackNumber = track.trackNumber,
                    )
                }
            }
        }
    }

    /** Validates a restored queue with one library snapshot, without constructing playback items. */
    suspend fun availableTrackIds(trackIds: Collection<String>): Set<String> {
        check(::appContext.isInitialized) { "AndroidPlaybackRuntime is not initialized" }
        if (trackIds.isEmpty()) return emptySet()
        return availableSyncedTrackIds(appContext.filesDir, syncStore.tracks.first(), trackIds.toSet())
    }

    fun controller(): PlaybackController {
        check(::appContext.isInitialized) { "AndroidPlaybackRuntime is not initialized" }
        return PlaybackController(appContext, resolver)
    }
}

internal fun playbackArtistNames(metadata: JsonObject?, fallback: String): String =
    ((metadata?.get("artists") as? JsonArray).orEmpty())
        .mapNotNull { artist ->
            ((artist as? JsonObject)?.get("name") as? JsonPrimitive)
                ?.contentOrNull
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }
        .joinToString(", ")
        .ifBlank { fallback }

internal fun resolveSyncedAudioFile(filesDir: File, storedPath: String?): File? = storedPath?.let { path ->
    File(path).let { candidate -> if (candidate.isAbsolute) candidate else File(filesDir, path) }
}

internal fun availableSyncedTrackIds(
    filesDir: File,
    tracks: List<LibraryTrack>,
    requestedTrackIds: Set<String>,
): Set<String> = tracks.asSequence()
    .filter { it.id in requestedTrackIds }
    .filter { resolveSyncedAudioFile(filesDir, it.audioPath)?.isFile == true }
    .mapTo(LinkedHashSet()) { it.id }
