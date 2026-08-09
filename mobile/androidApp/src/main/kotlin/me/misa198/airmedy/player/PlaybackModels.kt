package me.misa198.airmedy.player

internal const val PlaybackLogTag = "AirmedyPlayback"

internal fun clampSeekPosition(positionMs: Long, durationMs: Long): Long =
    positionMs.coerceAtLeast(0L).let { position -> if (durationMs > 0L) position.coerceAtMost(durationMs) else position }

/** An item the Android-native player can resolve from the synced library. */
data class PlaybackItem(
    val trackId: String,
    val title: String,
    val artist: String,
    val audioPath: String,
    val artworkPath: String? = null,
)

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Preparing(val item: PlaybackItem) : PlaybackState
    data class Playing(val item: PlaybackItem, val positionMs: Long, val durationMs: Long) : PlaybackState
    data class Paused(val item: PlaybackItem, val positionMs: Long, val durationMs: Long) : PlaybackState
    data class Failed(val trackId: String?, val reason: String) : PlaybackState
}

internal fun interface PlaybackItemResolver {
    suspend fun resolve(trackId: String): PlaybackItem?
}
