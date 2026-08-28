package me.misa198.airmedy.player

import android.media.AudioManager

internal const val PlaybackLogTag = "AirmedyPlayback"

internal fun clampSeekPosition(positionMs: Long, durationMs: Long): Long =
    positionMs.coerceAtLeast(0L).let { position -> if (durationMs > 0L) position.coerceAtMost(durationMs) else position }

/** Queue item IDs are their active-order indexes, as required by Android's MediaSession. */
internal fun activeQueueItemId(snapshot: PlaybackQueueSnapshot): Long =
    snapshot.currentIndex.takeIf { it in snapshot.activeTrackIds.indices }?.toLong() ?: -1L

/** Drops queue entries no longer represented by the current synced library. */
internal fun queueForAvailableTracks(
    snapshot: PlaybackQueueSnapshot,
    availableTrackIds: Set<String>,
): PlaybackQueueSnapshot = snapshot.copy(
    originalTrackIds = snapshot.originalTrackIds.filter(availableTrackIds::contains),
    activeTrackIds = snapshot.activeTrackIds.filter(availableTrackIds::contains),
)

/** Android sends this action when the current music output is about to become audible. */
internal fun audioBecomingNoisyRequiresPause(action: String?): Boolean =
    action == AudioManager.ACTION_AUDIO_BECOMING_NOISY

internal enum class AudioFocusChangeAction { Pause, PauseAndResumeOnGain, Duck, Restore, Ignore }

internal fun audioFocusChangeAction(change: Int): AudioFocusChangeAction = when (change) {
    AudioManager.AUDIOFOCUS_LOSS -> AudioFocusChangeAction.Pause
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> AudioFocusChangeAction.PauseAndResumeOnGain
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AudioFocusChangeAction.Duck
    AudioManager.AUDIOFOCUS_GAIN -> AudioFocusChangeAction.Restore
    else -> AudioFocusChangeAction.Ignore
}

/** AAudio streams cannot be restarted after Android disconnects their output route. */
internal fun audioOutputDisconnectRequiresRecovery(isOutputDisconnected: Boolean): Boolean = isOutputDisconnected

/** A natural repeat-off completion releases the decoder; Play then begins the active queue again. */
internal fun shouldRestartQueueOnResume(
    pausedPositionMs: Long,
    durationMs: Long,
    hasDecoder: Boolean,
): Boolean = !hasDecoder && durationMs > 0L && pausedPositionMs >= durationMs

/** Pure policy shared by the service ticker and host tests. */
internal fun shouldStartCrossfade(
    crossfadeSeconds: Int,
    positionMs: Long,
    durationMs: Long,
    hasPreloadedNext: Boolean,
): Boolean {
    if (!hasPreloadedNext || crossfadeSeconds <= 0 || durationMs < 2_000L) return false
    val remainingMs = durationMs - positionMs
    val effectiveFadeMs = minOf(crossfadeSeconds * 1_000L, durationMs / 2)
    return remainingMs in 401..effectiveFadeMs
}

/** Keep the outgoing gain ramp within the audible remainder of its source. */
internal fun crossfadeDurationMs(crossfadeSeconds: Int, positionMs: Long, durationMs: Long): Long =
    minOf(
        crossfadeSeconds.coerceAtLeast(0) * 1_000L,
        durationMs.coerceAtLeast(0L) / 2,
        (durationMs - positionMs).coerceAtLeast(0L),
    )

/**
 * The native engine has exactly two source slots. During a fade they belong to
 * the incoming and outgoing tracks, so the queue's following item cannot be
 * loaded until the outgoing slot has been retired.
 */
internal fun canPreloadNext(isCrossfading: Boolean): Boolean = !isCrossfading

/** An item the Android-native player can resolve from the synced library. */
data class PlaybackItem(
    val trackId: String,
    val title: String,
    val artist: String,
    val audioPath: String,
    val artworkPath: String? = null,
    val albumId: String = "",
    val analysis: TrackAnalysis? = null,
    val album: String = "",
    val albumArtist: String = "",
    val trackNumber: Int = 0,
)

/** Visual lifecycle for an automatic native audio crossfade; manual changes never create one. */
internal data class ArtworkCrossfadeTransition(
    val id: Long,
    val fromArtworkPath: String?,
    val toArtworkPath: String?,
    val durationMs: Long,
)

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Preparing(val item: PlaybackItem) : PlaybackState
    data class Playing(val item: PlaybackItem, val positionMs: Long, val durationMs: Long) : PlaybackState
    data class Paused(val item: PlaybackItem, val positionMs: Long, val durationMs: Long) : PlaybackState
    data class Failed(val trackId: String?, val reason: String) : PlaybackState
}

/** A newly selected queue takes precedence over restoring the prior session. */
internal fun playbackActionReplacesRestoredQueue(action: String?): Boolean = when (action) {
    PlaybackService.ActionPlay, PlaybackService.ActionShuffle -> true
    else -> false
}

internal fun interface PlaybackItemResolver {
    suspend fun resolve(trackId: String): PlaybackItem?
}
