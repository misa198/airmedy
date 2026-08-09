package me.misa198.airmedy.player

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.first
import me.misa198.airmedy.sync.AndroidLibrarySyncStore

/** Application-scoped composition root for Android playback. */
internal object AndroidPlaybackRuntime {
    private lateinit var appContext: Context
    private lateinit var resolver: PlaybackItemResolver

    fun initialize(context: Context, syncStore: AndroidLibrarySyncStore) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
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
                    PlaybackItem(track.id, track.title, track.artists, audio.absolutePath, artwork)
                }
            }
        }
    }

    fun controller(): PlaybackController {
        check(::appContext.isInitialized) { "AndroidPlaybackRuntime is not initialized" }
        return PlaybackController(appContext, resolver)
    }
}

internal fun resolveSyncedAudioFile(filesDir: File, storedPath: String?): File? = storedPath?.let { path ->
    File(path).let { candidate -> if (candidate.isAbsolute) candidate else File(filesDir, path) }
}
