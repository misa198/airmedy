package me.misa198.airmedy.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as AndroidMediaPlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.File
import android.util.LruCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.misa198.airmedy.MainActivity
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.AndroidSyncRuntime

/** Owns Android transport; queue semantics are delegated to sharedLogic. */
class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()
    private val restored = CompletableDeferred<Unit>()
    private lateinit var restoreJob: Job
    private val queue = PlaybackQueue()
    private var decoder: FfmpegDecoder? = null
    private var preloadedItem: PlaybackItem? = null
    private lateinit var sessionStore: PlaybackSessionStore
    private lateinit var playbackPreferences: PlaybackPreferences
    private lateinit var normalizationPreferences: NormalizationPreferences
    private var normalizationSettings = NormalizationSettings()
    private var preferencesJob: Job? = null
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSession
    private lateinit var focusRequest: AudioFocusRequest
    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (audioBecomingNoisyRequiresPause(intent.action)) dispatch(ActionPause)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AndroidPlaybackRuntime.initialize(applicationContext, AndroidSyncRuntime.syncStore())
        sessionStore = PlaybackSessionStore(applicationContext)
        playbackPreferences = PlaybackPreferences(applicationContext)
        normalizationPreferences = NormalizationPreferences(applicationContext)
        preferencesJob = scope.launch {
            playbackPreferences.settings.collectLatest { settings ->
                commandMutex.withLock {
                    crossfadeSeconds.value = settings.seconds
                    blendArtworkDuringCrossfade.value = settings.blendArtworkDuringCrossfade
                    if (!settings.blendArtworkDuringCrossfade) clearArtworkCrossfade()
                    // A preference update must never change a fade already
                    // running, but it does refresh the idle source afterward.
                    if (decoder?.isCrossfading() != true) preloadNext()
                }
            }
        }
        scope.launch {
            normalizationPreferences.settings.collectLatest { settings ->
                commandMutex.withLock {
                    normalizationSettings = settings
                    refreshNormalizationGains()
                }
            }
        }
        audioManager = getSystemService(AudioManager::class.java)
        registerNoisyAudioReceiver()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change -> if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) dispatch(ActionPause) }
            .build()
        mediaSession = MediaSession(this, "AirmedyPlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { dispatch(ActionResume) }
                override fun onPause() { dispatch(ActionPause) }
                override fun onSkipToNext() { dispatch(ActionNext) }
                override fun onSkipToPrevious() { dispatch(ActionPrevious) }
                override fun onSeekTo(pos: Long) { dispatch(ActionSeek, positionMs = pos) }
                override fun onStop() { dispatch(ActionStop) }
            })
            setPlaybackToLocal(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            isActive = false
        }
        AndroidPlaybackSession.publish(mediaSession.sessionToken)
        restoreJob = scope.launch {
            try {
                sessionStore.load()?.let { session ->
                    val saved = session.queue
                    val available = saved.originalTrackIds.filter { AndroidPlaybackRuntime.controller().resolve(it) != null }
                    val availableSet = available.toSet()
                    commandMutex.withLock {
                        if (available.isEmpty()) {
                            clearRestoredSession()
                            return@withLock
                        }
                        queue.restore(queueForAvailableTracks(saved, availableSet))
                        restoreCurrent(session.positionMs)
                    }
                }
            } catch (error: Throwable) {
                if (error !is kotlinx.coroutines.CancellationException) {
                    Log.w(PlaybackLogTag, "Unable to restore playback session; clearing it", error)
                    commandMutex.withLock { clearRestoredSession() }
                }
            } finally {
                restored.complete(Unit)
            }
        }
        scope.launch {
            while (true) {
                delay(200)
                commandMutex.withLock {
                    refreshPlaybackPosition()
                    consumeNativeTransition()
                    if (decoder?.isCrossfading() != true) clearArtworkCrossfade()
                    if (audioOutputDisconnectRequiresRecovery(decoder?.isOutputDisconnected() == true)) {
                        recoverAfterOutputDisconnect()
                    } else if (maybeStartCrossfade()) {
                        consumeNativeTransition()
                    } else if (decoder?.isFinished() == true && state.value is PlaybackState.Playing) {
                        handleTransition(queue.next())
                        publishQueue()
                    }
                    // A crossfade occupies both native source slots. Once its
                    // callback retires the outgoing item, populate that slot
                    // with the queue's new immediate successor.
                    if (canPreloadNext(decoder?.isCrossfading() == true)) preloadNext()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (playbackActionReplacesRestoredQueue(intent?.action)) {
            // A tap is more important than reconstructing the prior session. In
            // particular, do not delay its Preparing state (and mini-player)
            // behind DataStore I/O and validation of every saved queue entry.
            restoreJob.cancel()
            restored.complete(Unit)
        }
        when (intent?.action) {
            ActionPlay, ActionShuffle -> dispatch(
                action = intent.action!!,
                trackIds = intent.getStringArrayExtra(TrackIdsExtra).orEmpty().toList(),
                startIndex = intent.getIntExtra(StartIndexExtra, 0),
            )
            ActionSeek -> dispatch(ActionSeek, positionMs = intent.getLongExtra(PositionMsExtra, 0L))
            ActionSetShuffle -> dispatch(ActionSetShuffle, enabled = intent.getBooleanExtra(EnabledExtra, false))
            ActionSetRepeat -> dispatch(
                ActionSetRepeat,
                repeat = intent.getStringExtra(RepeatModeExtra)?.let { value -> runCatching { RepeatMode.valueOf(value) }.getOrNull() },
            )
            ActionSetCrossfade -> scope.launch {
                playbackPreferences.setCrossfadeSeconds(
                    intent.getIntExtra(CrossfadeSecondsExtra, CrossfadeDisabledSeconds),
                )
            }
            ActionPlayNext, ActionAppend, ActionReorder -> dispatch(
                action = intent.action!!,
                trackIds = intent.getStringArrayExtra(TrackIdsExtra).orEmpty().toList(),
            )
            ActionSelect -> dispatch(ActionSelect, trackIds = listOfNotNull(intent.getStringExtra(TrackIdExtra)))
            ActionRemove -> dispatch(ActionRemove, trackIds = listOfNotNull(intent.getStringExtra(TrackIdExtra)))
            null -> Unit
            else -> dispatch(intent.action!!)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runBlocking { sessionStore.save(currentSession()) }
        decoder?.close()
        preferencesJob?.cancel()
        unregisterReceiver(noisyAudioReceiver)
        AndroidPlaybackSession.clear()
        mediaSession.release()
        audioManager.abandonAudioFocusRequest(focusRequest)
        scope.cancel()
        clearArtworkCrossfade()
        state.value = PlaybackState.Idle
        super.onDestroy()
    }

    private fun dispatch(
        action: String,
        positionMs: Long = 0L,
        trackIds: List<String> = emptyList(),
        startIndex: Int = 0,
        enabled: Boolean = false,
        repeat: RepeatMode? = null,
    ) = scope.launch {
        restored.await()
        commandMutex.withLock {
            consumeNativeTransition()
            Log.d(PlaybackLogTag, "Handling action=$action queueSize=${queue.snapshot().activeTrackIds.size}")
            when (action) {
                ActionPlay -> handleTransition(runCatching { queue.play(PlaybackRequest(trackIds, startIndex)) }
                    .getOrElse { QueueTransition.Stop })
                ActionShuffle -> handleTransition(runCatching { queue.playShuffled(PlaybackRequest(trackIds, startIndex)) }
                    .getOrElse { QueueTransition.Stop })
                ActionPause -> pauseCurrent()
                ActionResume -> resumeCurrent()
                ActionStop -> stopPlayback()
                ActionClearQueue -> handleTransition(queue.clear())
                ActionNext -> handleTransition(queue.next(), preservePlaybackState = true)
                ActionPrevious -> {
                    if ((decoder?.positionMs() ?: 0L) > PreviousRestartThresholdMs) decoder?.seekTo(0)
                    else handleTransition(queue.previous(), preservePlaybackState = true)
                }
                ActionSeek -> seekCurrent(positionMs)
                ActionSetShuffle -> handleTransition(queue.setShuffle(enabled))
                ActionSetRepeat -> repeat?.let(queue::setRepeatMode)
                ActionPlayNext -> queue.playNext(trackIds)
                ActionAppend -> queue.append(trackIds)
                ActionSelect -> trackIds.firstOrNull()?.let { handleTransition(queue.select(it)) }
                ActionRemove -> trackIds.firstOrNull()?.let { handleTransition(queue.removeFromQueue(it)) }
                ActionReorder -> queue.reorderQueue(trackIds)
            }
            if (action in PreloadResyncActions) {
                // The old source must not remain part of a fade whose queued
                // successor has just changed.
                if (decoder?.isCrossfading() == true) {
                    clearArtworkCrossfade()
                    decoder?.snapCrossfade()
                }
                preloadNext()
            }
            publishQueue()
        }
    }

    private suspend fun handleTransition(
        transition: QueueTransition,
        preservePlaybackState: Boolean = false,
    ) {
        when (transition) {
            is QueueTransition.Play -> playCurrent(startPaused = preservePlaybackState && state.value is PlaybackState.Paused)
            QueueTransition.StopAtCurrent -> stopAtCurrentTrack()
            QueueTransition.Stop -> stopPlayback()
            QueueTransition.Unchanged -> Unit
        }
    }

    private suspend fun playCurrent(startPositionMs: Long = 0L, startPaused: Boolean = false) {
        clearArtworkCrossfade()
        val trackId = queue.snapshot().currentTrackId ?: return stopPlayback()
        Log.d(PlaybackLogTag, "Preparing current queue track id=$trackId")
        val item = AndroidPlaybackRuntime.controller().resolve(trackId) ?: return fail(trackId, "Audio asset is not available")
        state.value = PlaybackState.Preparing(item)
        publishNowPlaying(item, AndroidMediaPlaybackState.STATE_BUFFERING, positionMs = 0L, durationMs = 0L)
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return fail(trackId, "Audio focus was not granted")
        try {
            decoder?.close()
            decoder = FfmpegDecoder().also {
                it.prepare(File(item.audioPath), normalizationGain(item, queue.peekNext()))
                if (startPositionMs > 0L) it.seekTo(clampSeekPosition(startPositionMs, it.durationMs()))
                if (startPaused) {
                    it.pause()
                    state.value = PlaybackState.Paused(item, it.positionMs(), it.durationMs())
                    publishNowPlaying(item, AndroidMediaPlaybackState.STATE_PAUSED, it.positionMs(), it.durationMs())
                } else {
                    it.play()
                    state.value = PlaybackState.Playing(item, it.positionMs(), it.durationMs())
                    publishNowPlaying(item, AndroidMediaPlaybackState.STATE_PLAYING, it.positionMs(), it.durationMs())
                }
            }
            preloadNext()
            showForeground(item)
            Log.d(PlaybackLogTag, "Playback started id=$trackId durationMs=${decoder?.durationMs()}")
        } catch (error: Throwable) {
            fail(trackId, error.message ?: "Unable to decode audio")
        }
    }

    /** Restores the selected item paused, so reopening the app never starts audio by itself. */
    private suspend fun restoreCurrent(savedPositionMs: Long) {
        val trackId = queue.snapshot().currentTrackId ?: return clearRestoredSession()
        val item = AndroidPlaybackRuntime.controller().resolve(trackId)
            ?: return clearRestoredSession()
        try {
            decoder?.close()
            decoder = FfmpegDecoder().also {
                it.prepare(File(item.audioPath), normalizationGain(item, queue.peekNext()))
                val positionMs = clampSeekPosition(savedPositionMs, it.durationMs())
                if (positionMs > 0L) it.seekTo(positionMs)
                it.pause()
                state.value = PlaybackState.Paused(item, positionMs, it.durationMs())
                publishNowPlaying(item, AndroidMediaPlaybackState.STATE_PAUSED, positionMs, it.durationMs())
            }
            preloadNext()
            showForeground(item)
            publishQueue()
            Log.d(PlaybackLogTag, "Restored paused playback id=$trackId positionMs=${decoder?.positionMs()}")
        } catch (error: Throwable) {
            Log.w(PlaybackLogTag, "Unable to restore playback id=$trackId; clearing session", error)
            clearRestoredSession()
        }
    }

    private fun pauseCurrent() {
        clearArtworkCrossfade()
        decoder?.pause()
        (state.value as? PlaybackState.Playing)?.let { current ->
            val positionMs = decoder?.positionMs() ?: current.positionMs
            state.value = PlaybackState.Paused(current.item, positionMs, current.durationMs)
            publishNowPlaying(current.item, AndroidMediaPlaybackState.STATE_PAUSED, positionMs, current.durationMs)
        }
        updateNotification()
    }

    private suspend fun resumeCurrent() {
        val paused = state.value as? PlaybackState.Paused
        val currentDecoder = decoder
        if (paused == null || currentDecoder == null || currentDecoder.isOutputDisconnected()) {
            if (currentDecoder?.isOutputDisconnected() == true) {
                currentDecoder.close()
                decoder = null
            }
            playCurrent(paused?.positionMs ?: 0L)
            return
        }
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            fail(paused.item.trackId, "Audio focus was not granted")
            return
        }
        currentDecoder.play()
        val positionMs = currentDecoder.positionMs()
        state.value = PlaybackState.Playing(paused.item, positionMs, paused.durationMs)
        publishNowPlaying(paused.item, AndroidMediaPlaybackState.STATE_PLAYING, positionMs, paused.durationMs)
        updateNotification()
        Log.d(PlaybackLogTag, "Playback resumed id=${paused.item.trackId} positionMs=$positionMs")
    }

    /**
     * A manual route change invalidates the old AAudio stream without being a
     * user pause. Recreate it on the new route and retain its rendered position.
     * A real device removal sends ACTION_AUDIO_BECOMING_NOISY, whose queued
     * pause action wins and leaves playback paused instead.
     */
    private suspend fun recoverAfterOutputDisconnect() {
        val current = state.value as? PlaybackState.Playing ?: return
        val positionMs = decoder?.positionMs() ?: current.positionMs
        decoder?.close()
        decoder = null
        Log.w(PlaybackLogTag, "Audio output changed; recreating stream id=${current.item.trackId} positionMs=$positionMs")
        playCurrent(positionMs)
    }

    private fun seekCurrent(requestedPositionMs: Long) {
        val current = state.value
        val item: PlaybackItem
        val durationMs: Long
        val playing: Boolean
        when (current) {
            is PlaybackState.Playing -> {
                item = current.item
                durationMs = current.durationMs
                playing = true
            }
            is PlaybackState.Paused -> {
                item = current.item
                durationMs = current.durationMs
                playing = false
            }
            else -> return
        }
        val targetPositionMs = clampSeekPosition(requestedPositionMs, durationMs)
        clearArtworkCrossfade()
        decoder?.seekTo(targetPositionMs) ?: return
        if (playing) {
            state.value = PlaybackState.Playing(item, targetPositionMs, durationMs)
            publishNowPlaying(item, AndroidMediaPlaybackState.STATE_PLAYING, targetPositionMs, durationMs)
        } else {
            state.value = PlaybackState.Paused(item, targetPositionMs, durationMs)
            publishNowPlaying(item, AndroidMediaPlaybackState.STATE_PAUSED, targetPositionMs, durationMs)
        }
        Log.d(PlaybackLogTag, "Seek requested id=${item.trackId} targetMs=$targetPositionMs playing=$playing")
    }

    private fun stopPlayback() {
        clearArtworkCrossfade()
        decoder?.snapCrossfade()
        decoder?.close(); decoder = null; preloadedItem = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        state.value = PlaybackState.Idle
        mediaSession.setPlaybackState(androidPlaybackState(AndroidMediaPlaybackState.STATE_STOPPED, 0L))
        mediaSession.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * Natural repeat-off exhaustion is distinct from clearing or stopping the queue:
     * retain the final item so its player controls remain available for replay.
     */
    private fun stopAtCurrentTrack() {
        val current = state.value as? PlaybackState.Playing ?: return stopPlayback()
        clearArtworkCrossfade()
        decoder?.snapCrossfade()
        decoder?.close(); decoder = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        state.value = PlaybackState.Paused(current.item, current.durationMs, current.durationMs)
        publishNowPlaying(current.item, AndroidMediaPlaybackState.STATE_PAUSED, current.durationMs, current.durationMs)
        updateNotification()
        Log.d(PlaybackLogTag, "Playback reached final queue track id=${current.item.trackId}")
    }

    private fun fail(trackId: String?, reason: String) {
        Log.e(PlaybackLogTag, "Playback failed id=$trackId reason=$reason")
        clearArtworkCrossfade()
        decoder?.close(); decoder = null; preloadedItem = null
        state.value = PlaybackState.Failed(trackId, reason)
        mediaSession.setPlaybackState(androidPlaybackState(AndroidMediaPlaybackState.STATE_ERROR, 0L))
        mediaSession.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun publishQueue() {
        val snapshot = queue.snapshot()
        queueState.value = snapshot
        scope.launch { sessionStore.save(currentSession(snapshot)) }
    }

    private fun currentSession(snapshot: PlaybackQueueSnapshot = queue.snapshot()): PlaybackSession {
        val positionMs = when (val current = state.value) {
            is PlaybackState.Playing -> decoder?.positionMs() ?: current.positionMs
            is PlaybackState.Paused -> decoder?.positionMs() ?: current.positionMs
            else -> 0L
        }
        return PlaybackSession(snapshot, positionMs.coerceAtLeast(0L))
    }

    private suspend fun clearRestoredSession() {
        decoder?.close(); decoder = null; preloadedItem = null
        queue.clear()
        queueState.value = queue.snapshot()
        state.value = PlaybackState.Idle
        mediaSession.setPlaybackState(androidPlaybackState(AndroidMediaPlaybackState.STATE_NONE, 0L))
        mediaSession.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        sessionStore.clear()
    }

    /** Keep native idle slot aligned with the queue's immediate next item. */
    private suspend fun preloadNext() {
        val nextId = queue.peekNext()
        val currentDecoder = decoder ?: return
        if (nextId == preloadedItem?.trackId && currentDecoder.hasPreloaded()) return
        currentDecoder.clearPreloaded()
        preloadedItem = nextId?.let { id -> AndroidPlaybackRuntime.controller().resolve(id) }
        preloadedItem?.let { item ->
            runCatching { currentDecoder.preload(File(item.audioPath), normalizationGain(item, queue.peekNext())) }
                .onSuccess { loaded ->
                    if (!loaded) preloadedItem = null
                }
                .onFailure { error ->
                    Log.w(PlaybackLogTag, "Unable to preload next id=${item.trackId}", error)
                    preloadedItem = null
                }
        }
    }

    /** Native promotes audio first; this service transaction promotes queue/UI metadata. */
    private suspend fun consumeNativeTransition() {
        val transition = decoder?.consumeTransition() ?: return
        val incoming = preloadedItem ?: return
        val queueTransition = queue.next()
        if (queueTransition !is QueueTransition.Play || queueTransition.trackId != incoming.trackId) {
            fail(incoming.trackId, "Native transition no longer matches the playback queue")
            return
        }
        preloadedItem = null
        val currentDecoder = decoder ?: return
        val positionMs = currentDecoder.positionMs()
        val durationMs = currentDecoder.durationMs()
        state.value = PlaybackState.Playing(incoming, positionMs, durationMs)
        publishNowPlaying(incoming, AndroidMediaPlaybackState.STATE_PLAYING, positionMs, durationMs)
        updateNotification()
        // During a crossfade both native slots are live (incoming + outgoing).
        // Loading i+2 here would reuse the outgoing slot and cut i off instead
        // of letting it fade out. The ticker reloads after the fade completes.
        if (canPreloadNext(currentDecoder.isCrossfading())) preloadNext()
        publishQueue()
        Log.d(PlaybackLogTag, "Consumed native transition=$transition id=${incoming.trackId}")
    }

    private suspend fun normalizationGain(item: PlaybackItem, nextId: String?): Float {
        val analyses = AndroidSyncRuntime.syncStore().activeAnalyses()
        if (analyses.isEmpty()) {
            if (normalizationSettings.enabled) normalizationPreferences.disable()
            return 0f
        }
        val next = nextId?.let { AndroidPlaybackRuntime.controller().resolve(it) }
        val continuousAlbum = normalizationSettings.mode == NormalizationMode.Album &&
            item.albumId.isNotEmpty() && item.albumId == next?.albumId
        val albumAnalyses = if (continuousAlbum) {
            AndroidSyncRuntime.syncStore().tracks.first().filter { it.albumId == item.albumId }.mapNotNull { analyses[it.id] }
        } else emptyList()
        return normalizationGainDb(normalizationSettings, item.analysis, albumAnalyses, continuousAlbum)
    }

    private suspend fun refreshNormalizationGains() {
        val current = when (val value = state.value) {
            is PlaybackState.Playing -> value.item
            is PlaybackState.Paused -> value.item
            is PlaybackState.Preparing -> value.item
            else -> null
        } ?: return
        val nextId = queue.peekNext()
        decoder?.setNormalizationGains(normalizationGain(current, nextId), preloadedItem?.let { normalizationGain(it, queue.peekNext()) } ?: 0f)
    }

    private fun maybeStartCrossfade(): Boolean {
        val current = state.value as? PlaybackState.Playing ?: return false
        val currentDecoder = decoder ?: return false
        val incoming = preloadedItem ?: return false
        if (currentDecoder.isCrossfading()) return false
        if (!shouldStartCrossfade(
                crossfadeSeconds = crossfadeSeconds.value,
                positionMs = currentDecoder.positionMs(),
                durationMs = current.durationMs,
                hasPreloadedNext = preloadedItem != null && currentDecoder.hasPreloaded(),
            )
        ) return false
        val effectiveDurationMs = crossfadeDurationMs(
            crossfadeSeconds = crossfadeSeconds.value,
            positionMs = currentDecoder.positionMs(),
            durationMs = current.durationMs,
        )
        currentDecoder.beginCrossfade(effectiveDurationMs)
        if (currentDecoder.isCrossfading()) {
            nextArtworkCrossfadeId += 1
            artworkCrossfade.value = ArtworkCrossfadeTransition(
                id = nextArtworkCrossfadeId,
                fromArtworkPath = current.item.artworkPath,
                toArtworkPath = incoming.artworkPath,
                durationMs = effectiveDurationMs.coerceAtLeast(1L),
            )
        }
        return true
    }

    private fun clearArtworkCrossfade() {
        artworkCrossfade.value = null
    }

    private fun showForeground(item: PlaybackItem) {
        createChannel()
        startForeground(NotificationId, notification(item), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun updateNotification() {
        val item = when (val current = state.value) {
            is PlaybackState.Playing -> current.item
            is PlaybackState.Paused -> current.item
            else -> return
        }
        getSystemService(NotificationManager::class.java).notify(NotificationId, notification(item))
    }

    private fun registerNoisyAudioReceiver() {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyAudioReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(noisyAudioReceiver, filter)
        }
    }

    /** Publishes metadata and transport state to Android System Now Playing surfaces. */
    private fun publishNowPlaying(item: PlaybackItem, state: Int, positionMs: Long, durationMs: Long) {
        mediaSession.isActive = true
        val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, item.trackId)
                .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, item.artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
        loadNowPlayingArtwork(item.artworkPath)?.let { artwork ->
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)
            metadata.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artwork)
        }
        mediaSession.setMetadata(metadata.build())
        mediaSession.setPlaybackState(androidPlaybackState(state, positionMs))
        Log.d(PlaybackLogTag, "Published Android Now Playing id=${item.trackId} state=$state")
    }

    private fun updateNowPlayingTransportState() {
        when (val current = state.value) {
            is PlaybackState.Playing -> mediaSession.setPlaybackState(
                androidPlaybackState(AndroidMediaPlaybackState.STATE_PLAYING, decoder?.positionMs() ?: current.positionMs),
            )
            is PlaybackState.Paused -> mediaSession.setPlaybackState(
                androidPlaybackState(AndroidMediaPlaybackState.STATE_PAUSED, decoder?.positionMs() ?: current.positionMs),
            )
            else -> Unit
        }
    }

    private fun refreshPlaybackPosition() {
        val current = state.value as? PlaybackState.Playing ?: return
        val positionMs = decoder?.positionMs()?.let { clampSeekPosition(it, current.durationMs) } ?: return
        if (positionMs == current.positionMs) return
        state.value = current.copy(positionMs = positionMs)
        mediaSession.setPlaybackState(androidPlaybackState(AndroidMediaPlaybackState.STATE_PLAYING, positionMs))
    }

    private fun androidPlaybackState(state: Int, positionMs: Long): AndroidMediaPlaybackState =
        AndroidMediaPlaybackState.Builder()
            .setActions(
                AndroidMediaPlaybackState.ACTION_PLAY or
                    AndroidMediaPlaybackState.ACTION_PAUSE or
                    AndroidMediaPlaybackState.ACTION_PLAY_PAUSE or
                    AndroidMediaPlaybackState.ACTION_SKIP_TO_NEXT or
                    AndroidMediaPlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    AndroidMediaPlaybackState.ACTION_SEEK_TO or
                    AndroidMediaPlaybackState.ACTION_STOP,
            )
            .setState(state, positionMs, if (state == AndroidMediaPlaybackState.STATE_PLAYING) 1f else 0f)
            .build()

    private fun loadNowPlayingArtwork(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        nowPlayingArtworkCache.get(path)?.let { return it }
        val artwork = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= NowPlayingArtworkSizePx &&
                bounds.outHeight / (sampleSize * 2) >= NowPlayingArtworkSizePx
            ) sampleSize *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }.getOrNull()
        if (artwork != null) nowPlayingArtworkCache.put(path, artwork)
        else Log.w(PlaybackLogTag, "Unable to decode Now Playing artwork path=$path")
        return artwork
    }

    private fun notification(item: PlaybackItem): Notification = Notification.Builder(this, ChannelId)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(item.title)
        .setContentText(item.artist)
        .setContentIntent(nowPlayingContentIntent())
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken))
        .build()

    private fun nowPlayingContentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ChannelId, getString(R.string.playback_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        internal const val ActionPlay = "me.misa198.airmedy.player.PLAY"
        internal const val ActionShuffle = "me.misa198.airmedy.player.SHUFFLE"
        internal const val ActionPause = "me.misa198.airmedy.player.PAUSE"
        internal const val ActionResume = "me.misa198.airmedy.player.RESUME"
        internal const val ActionStop = "me.misa198.airmedy.player.STOP"
        internal const val ActionClearQueue = "me.misa198.airmedy.player.CLEAR_QUEUE"
        internal const val ActionNext = "me.misa198.airmedy.player.NEXT"
        internal const val ActionPrevious = "me.misa198.airmedy.player.PREVIOUS"
        internal const val ActionSeek = "me.misa198.airmedy.player.SEEK"
        internal const val ActionSetShuffle = "me.misa198.airmedy.player.SET_SHUFFLE"
        internal const val ActionSetRepeat = "me.misa198.airmedy.player.SET_REPEAT"
        internal const val ActionPlayNext = "me.misa198.airmedy.player.PLAY_NEXT"
        internal const val ActionAppend = "me.misa198.airmedy.player.APPEND"
        internal const val ActionSelect = "me.misa198.airmedy.player.SELECT"
        internal const val ActionRemove = "me.misa198.airmedy.player.REMOVE"
        internal const val ActionReorder = "me.misa198.airmedy.player.REORDER"
        internal const val TrackIdsExtra = "track_ids"
        internal const val TrackIdExtra = "track_id"
        internal const val StartIndexExtra = "start_index"
        internal const val PositionMsExtra = "position_ms"
        internal const val EnabledExtra = "enabled"
        internal const val RepeatModeExtra = "repeat_mode"
        private const val PreviousRestartThresholdMs = 3_000L
        private const val ChannelId = "playback"
        private const val NotificationId = 2002
        private const val NowPlayingArtworkSizePx = 512
        private val PreloadResyncActions = setOf(
            ActionSetShuffle, ActionSetRepeat, ActionPlayNext, ActionRemove, ActionReorder,
        )
        private val nowPlayingArtworkCache = LruCache<String, Bitmap>(20)
        internal val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        internal val queueState = MutableStateFlow(PlaybackQueueSnapshot())
        internal val crossfadeSeconds = MutableStateFlow(CrossfadeDisabledSeconds)
        internal val blendArtworkDuringCrossfade = MutableStateFlow(true)
        internal val artworkCrossfade = MutableStateFlow<ArtworkCrossfadeTransition?>(null)
        private var nextArtworkCrossfadeId = 0L
        internal const val ActionSetCrossfade = "me.misa198.airmedy.player.SET_CROSSFADE"
        internal const val CrossfadeSecondsExtra = "crossfade_seconds"
        internal fun intent(context: Context, action: String) = Intent(context, PlaybackService::class.java).setAction(action)
    }
}
