package me.misa198.airmedy.player

import android.media.AudioManager

internal const val PlaybackLogTag = "AirmedyPlayback"

internal fun clampSeekPosition(positionMs: Long, durationMs: Long): Long =
    positionMs.coerceAtLeast(0L).let { position -> if (durationMs > 0L) position.coerceAtMost(durationMs) else position }

/** Android sends this action when the current music output is about to become audible. */
internal fun audioBecomingNoisyRequiresPause(action: String?): Boolean =
    action == AudioManager.ACTION_AUDIO_BECOMING_NOISY

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
