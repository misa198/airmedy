package me.misa198.airmedy.player

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Android-only command boundary for the future UI. It deliberately does not
 * expose a platform decoder or a MediaCodec fallback.
 */
internal class PlaybackController(
    private val context: Context,
    private val resolver: PlaybackItemResolver,
) {
    val state: StateFlow<PlaybackState> get() = PlaybackService.state
    val queue: StateFlow<PlaybackQueueSnapshot> get() = PlaybackService.queueState
    val artworkCrossfade: StateFlow<ArtworkCrossfadeTransition?> get() = PlaybackService.artworkCrossfade
    val crossfadeSeconds: Flow<Int> get() = PlaybackService.crossfadeSeconds

    fun play(request: PlaybackRequest) {
        Log.d(PlaybackLogTag, "Queue play requested size=${request.trackIds.size} startIndex=${request.startIndex} startId=${request.trackIds[request.startIndex]}")
        context.startForegroundService(
            PlaybackService.intent(context, PlaybackService.ActionPlay)
            .putExtra(PlaybackService.TrackIdsExtra, request.trackIds.toTypedArray())
            .putExtra(PlaybackService.StartIndexExtra, request.startIndex),
        )
    }

    fun pause() = command(PlaybackService.ActionPause)
    fun resume() = command(PlaybackService.ActionResume)
    fun stop() = command(PlaybackService.ActionStop)
    fun clearQueue() = command(PlaybackService.ActionClearQueue)
    fun next() = command(PlaybackService.ActionNext)
    fun previous() = command(PlaybackService.ActionPrevious)
    fun shuffle(request: PlaybackRequest) = context.startForegroundService(
        PlaybackService.intent(context, PlaybackService.ActionShuffle)
            .putExtra(PlaybackService.TrackIdsExtra, request.trackIds.toTypedArray())
            .putExtra(PlaybackService.StartIndexExtra, request.startIndex),
    )
    fun setShuffle(enabled: Boolean) = context.startForegroundService(
        PlaybackService.intent(context, PlaybackService.ActionSetShuffle).putExtra(PlaybackService.EnabledExtra, enabled),
    )
    fun setRepeatMode(mode: RepeatMode) = context.startForegroundService(
        PlaybackService.intent(context, PlaybackService.ActionSetRepeat).putExtra(PlaybackService.RepeatModeExtra, mode.name),
    )
    fun playNext(trackId: String) = playNext(listOf(trackId))
    fun playNext(trackIds: List<String>) = tracksCommand(PlaybackService.ActionPlayNext, trackIds)
    fun append(trackIds: List<String>) = tracksCommand(PlaybackService.ActionAppend, trackIds)
    fun selectQueueTrack(trackId: String) = context.startForegroundService(
        PlaybackService.intent(context, PlaybackService.ActionSelect).putExtra(PlaybackService.TrackIdExtra, trackId),
    )
    fun removeFromQueue(trackId: String) = context.startForegroundService(
        PlaybackService.intent(context, PlaybackService.ActionRemove).putExtra(PlaybackService.TrackIdExtra, trackId),
    )
    fun reorderQueue(trackIds: List<String>) = tracksCommand(PlaybackService.ActionReorder, trackIds)
    fun seekTo(positionMs: Long) = context.startForegroundService(
        PlaybackService.intent(context, PlaybackService.ActionSeek).putExtra(PlaybackService.PositionMsExtra, positionMs),
    )
    /**
     * A preference edit must not start the media foreground service: when no
     * track is playing it has no notification to promote within Android's
     * foreground-service deadline. A running service observes this DataStore
     * value and resyncs its preload itself.
     */
    fun setCrossfadeSeconds(seconds: Int) {
        val clamped = clampCrossfadeSeconds(seconds)
        CoroutineScope(Dispatchers.IO).launch {
            PlaybackPreferences(context).setCrossfadeSeconds(clamped)
        }
    }

    fun setBlendArtworkDuringCrossfade(enabled: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            PlaybackPreferences(context).setBlendArtworkDuringCrossfade(enabled)
        }
    }

    internal suspend fun resolve(trackId: String): PlaybackItem? = resolver.resolve(trackId)

    private fun command(action: String) = context.startForegroundService(PlaybackService.intent(context, action))
    private fun tracksCommand(action: String, trackIds: List<String>) = context.startForegroundService(
        PlaybackService.intent(context, action).putExtra(PlaybackService.TrackIdsExtra, trackIds.toTypedArray()),
    )
}
