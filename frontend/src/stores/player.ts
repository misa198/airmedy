import { defineStore } from 'pinia'
import { ref, shallowRef, computed, watch } from 'vue'
import { Events } from '@wailsio/runtime'
import * as PlayerService from '../../bindings/airmedy/internal/infra/wails/playerservice'
import { PlaybackState, PlayerStatus, RepeatMode, ThemeColors } from '../../bindings/airmedy/internal/domain/models'
import type { Lyric, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { buildArtworkUrl, logger } from '@airmedy/utils'
import { useMoodRadioStore } from './moodRadio'

export type PlayerMode = 'sticky' | 'mini' | 'fullscreen'

export type ArtworkCrossfadeState = {
  transitionId: number
  fromUrl: string | null
  toUrl: string | null
  durationMs: number
}

type ArtworkCrossfadeEvent = {
  transition_id: number
  phase: 'start' | 'end'
  from_artwork_key?: string
  to_artwork_key?: string
  duration_ms?: number
}

type LyricsEvent = {
  track_id: string
  request_id: number
  state: 'loading' | 'ready' | 'error'
  lyric?: Lyric | null
}

export const usePlayerStore = defineStore('player', () => {
  // State
  const status = shallowRef<PlayerStatus | null>(null)
  const queue = shallowRef<TrackDTO[]>([])
  const currentTrack = shallowRef<TrackDTO | null>(null)
  const theme = shallowRef<ThemeColors | null>(null)
  const isQueueOpen = ref(false)
  const isLyricsOpen = ref(false)
  const isTrackInfoOpen = ref(false)
  const trackInfoTrack = ref<TrackDTO | null>(null)
  const sidebarWidth = ref(260)
  const playerMode = ref<PlayerMode>('sticky')
  const lyrics = ref<Lyric | null>(null)
  const lyricsLoading = ref(false)
  const lyricsRequestId = ref(0)
  const artworkCrossfade = shallowRef<ArtworkCrossfadeState | null>(null)

  // Interpolation state
  const localInterpolatedPosition = ref(0)
  let lastSyncTime = performance.now()
  let lastSyncPosition = 0

  function syncInterpolation(pos: number) {
    lastSyncPosition = pos
    lastSyncTime = performance.now()
    localInterpolatedPosition.value = lastSyncPosition
  }

  // Computed
  const isPlaying = computed(
    () => status.value?.playback_state === PlaybackState.PlaybackStatePlaying,
  )
  const isPaused = computed(
    () => status.value?.playback_state === PlaybackState.PlaybackStatePaused,
  )
  const isStopped = computed(
    () =>
      !status.value ||
      status.value.playback_state === PlaybackState.PlaybackStateStopped,
  )
  const position = computed(() => localInterpolatedPosition.value)
  const duration = computed(() => status.value?.duration ?? 0)
  const volume = computed(() => status.value?.volume ?? 1)
  const muted = computed(() => status.value?.muted ?? false)
  const shuffle = computed(() => status.value?.shuffle ?? false)
  const repeatMode = computed(() => status.value?.repeat_mode ?? RepeatMode.RepeatModeOff)
  const progressPercent = computed(() =>
    duration.value > 0 ? (position.value / duration.value) * 100 : 0,
  )
  // Set of track ids in the queue — recomputed only when the queue changes, O(1) membership checks
  const queueIds = computed(() => new Set(queue.value.map(t => t.id)))
  const artworkUrl = computed(() => buildArtworkUrl(currentTrack.value?.artwork_key, 'lg') ?? null)
  const artworkUrlMd = computed(() => buildArtworkUrl(currentTrack.value?.artwork_key, 'md') ?? null)
  const artworkUrlSm = computed(() => buildArtworkUrl(currentTrack.value?.artwork_key, 'sm') ?? null)

  function preloadArtwork(url: string | null) {
    if (!url) return
    const image = new Image()
    image.src = url
  }

  // Clear lyrics immediately whenever the playing track changes
  watch(currentTrack, (newTrack, oldTrack) => {
    if (newTrack?.id !== oldTrack?.id) {
      // Events from the backend can deliver `ready` immediately after the
      // status event that changes currentTrack. This watcher runs on Vue's
      // next flush, so do not erase a lyric that already belongs to the new
      // track (otherwise automatic lyrics appear to load and then vanish).
      const hasNewTrackLyrics = lyrics.value?.track_id === newTrack?.id
      if (!hasNewTrackLyrics) lyrics.value = null
      lyricsLoading.value = !!newTrack && !hasNewTrackLyrics
    }
  })

  // Keep interpolation state in sync with status
  watch(status, (newStatus) => {
    if (newStatus) {
      syncInterpolation(newStatus.position ?? 0)
    }
  })

  // Actions
  async function syncState() {
    try {
      const s = await PlayerService.GetStatus()
      status.value = s
      theme.value = s.theme
      lyricsRequestId.value = s.lyrics_request_id

      const q = await PlayerService.GetQueue()
      queue.value = (q.filter(Boolean) as TrackDTO[])
      if (s.track_id) {
        currentTrack.value = queue.value.find((t) => t.id === s.track_id) ?? null
      }
    } catch (e) {
      console.error('Failed to sync player state', e)
    }
  }

  let _offFns: (() => void)[] = []
  let _initialized = false
  let _rafId: number | null = null
  let _lyricsReconcileTimer: ReturnType<typeof setTimeout> | null = null
  let _lyricsReconcileRequestId = 0
  let _lyricsReconcileDeadline = 0
  let _lyricsReconcileGeneration = 0

  const lyricsReconcileIntervalMs = 250
  const lyricsReconcileTimeoutMs = 36_000

  function stopLyricsReconciliation() {
    if (_lyricsReconcileTimer !== null) {
      clearTimeout(_lyricsReconcileTimer)
      _lyricsReconcileTimer = null
    }
    _lyricsReconcileRequestId = 0
    _lyricsReconcileDeadline = 0
    _lyricsReconcileGeneration++
  }

  function acceptLyricsEvent(event: LyricsEvent): boolean {
    if (!event || event.track_id !== currentTrack.value?.id) return false
    if (event.request_id < lyricsRequestId.value) return false

    lyricsRequestId.value = event.request_id
    if (event.state === 'loading') {
      lyricsLoading.value = true
      scheduleLyricsReconciliation(event.track_id, event.request_id)
      return true
    }

    if (event.state === 'ready') lyrics.value = event.lyric ?? null
    // An error ends loading but deliberately preserves lyrics already shown.
    lyricsLoading.value = false
    stopLyricsReconciliation()
    return true
  }

  function scheduleLyricsReconciliation(trackId: string, requestId: number) {
    if (!trackId || requestId <= 0) return

    if (_lyricsReconcileRequestId !== requestId) {
      _lyricsReconcileRequestId = requestId
      _lyricsReconcileDeadline = Date.now() + lyricsReconcileTimeoutMs
    }
    if (_lyricsReconcileTimer !== null) clearTimeout(_lyricsReconcileTimer)
    const generation = ++_lyricsReconcileGeneration

    _lyricsReconcileTimer = setTimeout(async () => {
      _lyricsReconcileTimer = null
      if (
        generation !== _lyricsReconcileGeneration
        || currentTrack.value?.id !== trackId
        || lyricsRequestId.value > requestId
        || !lyricsLoading.value
      ) {
        return
      }

      try {
        const snapshot = await PlayerService.GetCurrentLyrics()
        if (generation !== _lyricsReconcileGeneration) return
        if (
          snapshot
          && snapshot.track_id === trackId
          && snapshot.request_id >= requestId
          && acceptLyricsEvent(snapshot as LyricsEvent)
        ) {
          return
        }
      } catch (e) {
        logger.error('Failed to reconcile lyrics request', e)
      }

      if (Date.now() >= _lyricsReconcileDeadline) {
        if (currentTrack.value?.id === trackId && lyricsRequestId.value === requestId) {
          lyricsLoading.value = false
        }
        stopLyricsReconciliation()
        return
      }
      scheduleLyricsReconciliation(trackId, requestId)
    }, lyricsReconcileIntervalMs)
  }

  function updateInterpolatedPosition() {
    if (isPlaying.value) {
      const now = performance.now()
      const elapsed = (now - lastSyncTime) / 1000
      localInterpolatedPosition.value = Math.min(lastSyncPosition + elapsed, duration.value)
    } else {
      localInterpolatedPosition.value = lastSyncPosition
    }
    _rafId = requestAnimationFrame(updateInterpolatedPosition)
  }

  async function init() {
    if (_initialized) return
    _initialized = true
    await syncState()
    _rafId = requestAnimationFrame(updateInterpolatedPosition)

    _offFns = [
      Events.On('player:status', (ev: Events.WailsEvent) => {
        const s = ev.data as PlayerStatus
        // SFBAudioEngine enqueueURL is async; currentTime can briefly return the
        // old track's position after a hard load. New track always starts at 0.
        if (s.track_id && s.track_id !== status.value?.track_id) {
          s.position = 0
        }
        status.value = s
        if (s.theme) theme.value = s.theme

        // Wails events are delivered independently, so a terminal lyric event
        // can arrive before the accompanying status update. Request IDs are
        // monotonic: never let a late status for an older request put the UI
        // back into loading after accepting a newer terminal event.
        if (s.track_id && s.lyrics_request_id > lyricsRequestId.value) {
          lyricsRequestId.value = s.lyrics_request_id
          lyrics.value = null
          lyricsLoading.value = true
          scheduleLyricsReconciliation(s.track_id, s.lyrics_request_id)
        }

        if (s?.track_id) {
          const found = queue.value.find((t) => t.id === s.track_id)
          if (found) currentTrack.value = found
        } else if (s?.playback_state === PlaybackState.PlaybackStateStopped) {
          // Keep the current track even when stopped so the UI can show it as the last active track
          // unless we explicitly want to clear it. For now, we keep it.
        }
      }),

      Events.On('player:theme', (ev: Events.WailsEvent) => {
        theme.value = ev.data as ThemeColors
      }),

      Events.On('player:artwork-crossfade', (ev: Events.WailsEvent) => {
        const transition = ev.data as ArtworkCrossfadeEvent
        if (!transition || !Number.isInteger(transition.transition_id)) return

        if (transition.phase === 'end') {
          if (artworkCrossfade.value?.transitionId === transition.transition_id) {
            artworkCrossfade.value = null
          }
          return
        }
        if (transition.phase !== 'start') return

        const fromUrl = buildArtworkUrl(transition.from_artwork_key, 'lg') ?? null
        const toUrl = buildArtworkUrl(transition.to_artwork_key, 'lg') ?? null
        preloadArtwork(toUrl)
        artworkCrossfade.value = {
          transitionId: transition.transition_id,
          fromUrl,
          toUrl,
          durationMs: Math.max(1, transition.duration_ms ?? 0),
        }
      }),

      Events.On('player:lyrics', (ev: Events.WailsEvent) => {
        const event = ev.data as LyricsEvent
        acceptLyricsEvent(event)
      }),

      Events.On('player:queue-reordered', (ev: Events.WailsEvent) => {
        // Pure reorder (shuffle/unshuffle): only ids are sent, remap the
        // already-loaded tracks instead of waiting on a full queue payload.
        const ids = ev.data as string[]
        if (!Array.isArray(ids)) return
        const byId = new Map(queue.value.map(t => [t.id, t]))
        const reordered = ids.map(id => byId.get(id)).filter((t): t is TrackDTO => !!t)
        if (reordered.length === ids.length) {
          queue.value = reordered
        }
      }),

      Events.On('player:queue-updated', (ev: Events.WailsEvent) => {
        const q = ev.data as TrackDTO[]
        if (Array.isArray(q)) {
          queue.value = q.filter(Boolean) as TrackDTO[]
          if (queue.value.length === 0) {
            currentTrack.value = null
          } else if (status.value?.track_id) {
            // Re-sync currentTrack in case player:status arrived before queue-updated
            const found = queue.value.find((t) => t.id === status.value!.track_id)
            if (found) currentTrack.value = found
          }
        }
      }),

      Events.On('library:track-updated', (ev: Events.WailsEvent) => {
        const updated = ev.data as TrackDTO
        if (!updated?.id) return
        const idx = queue.value.findIndex(t => t.id === updated.id)
        if (idx !== -1) queue.value = queue.value.map((t, i) => i === idx ? updated : t)
        if (currentTrack.value?.id === updated.id) currentTrack.value = updated
      }),

      Events.On('library:track-deleted', (ev: Events.WailsEvent) => {
        const id = ev.data as string
        if (!id) return
        queue.value = queue.value.filter(t => t.id !== id)
        if (currentTrack.value?.id === id) currentTrack.value = null
      }),
    ]

    // Pull lyrics for current track in case player:lyrics event fired before listener registration.
    // Use the player's resolver (not raw GetLyrics) so a restored track recovers lyrics that live
    // only in a sibling file or embedded metadata tag, not just cached provider content.
    if (currentTrack.value) {
      const trackId = currentTrack.value.id

      const requestId = lyricsRequestId.value
      try {
        const event = await PlayerService.GetCurrentLyrics()
        if (
          event
          && event.track_id === trackId
          && currentTrack.value?.id === trackId
          && event.request_id >= lyricsRequestId.value
        ) {
          // The first online lookup may advance and finish while init is still
          // awaiting its startup IPC calls, before the event listeners exist.
          // Its pull snapshot is newer than the status we initially observed,
          // so accept it just like a newer player:lyrics event.
          acceptLyricsEvent(event as LyricsEvent)
        }
      } catch (e) {
        logger.error('Failed to pull initial lyrics', e)
        if (currentTrack.value?.id === trackId && lyricsRequestId.value === requestId) {
          lyricsLoading.value = false
        }
      }
    }
  }

  function dispose() {
    _offFns.forEach(off => off())
    _offFns = []
    if (_rafId !== null) {
      cancelAnimationFrame(_rafId)
      _rafId = null
    }
    stopLyricsReconciliation()
    _initialized = false
    artworkCrossfade.value = null
  }

  async function play() {
    await PlayerService.Play()
  }

  async function pause() {
    await PlayerService.Pause()
  }

  async function togglePlayPause() {
    if (isPlaying.value) {
      await pause()
    } else {
      await play()
    }
  }

  async function refreshCurrentLyrics() {
    if (!currentTrack.value) return
    const trackId = currentTrack.value.id
    const requestId = await PlayerService.RefreshCurrentLyrics()
    if (!requestId || currentTrack.value?.id !== trackId) return

    // RefreshCurrentLyrics starts work asynchronously. Do not depend solely on
    // event delivery: the first Wails terminal event after app startup can be
    // missed while the bridge is warming up.
    if (requestId > lyricsRequestId.value) {
      lyricsRequestId.value = requestId
      lyrics.value = null
      lyricsLoading.value = true
    }
    if (requestId === lyricsRequestId.value && lyricsLoading.value) {
      scheduleLyricsReconciliation(trackId, requestId)
    }
  }

  async function next() {
    await PlayerService.Next()
  }

  async function previous() {
    await PlayerService.Previous()
  }

  async function fastForward() {
    await PlayerService.FastForward()
  }

  async function rewind() {
    await PlayerService.Rewind()
  }

  async function increaseVolume() {
    await PlayerService.IncreaseVolume()
  }

  async function decreaseVolume() {
    await PlayerService.DecreaseVolume()
  }

  async function toggleMute() {
    await PlayerService.ToggleMute()
  }

  async function seek(pos: number) {
    await PlayerService.Seek(pos)
  }

  async function setVolume(v: number) {
    await PlayerService.SetVolume(v)
  }

  async function setMuted(m: boolean) {
    await PlayerService.SetMuted(m)
  }

  async function setShuffle(s: boolean) {
    await PlayerService.SetShuffle(s)
    const q = await PlayerService.GetQueue()
    queue.value = (q.filter(Boolean) as TrackDTO[])

    // Do not rely solely on the queue-order watcher: unshuffle can leave the
    // current track and queue length unchanged while moving that track to the
    // tail. Ask Mood Radio to re-check after this store has the authoritative
    // post-toggle order. Its refilling guard coalesces this with the watcher.
    void useMoodRadioStore().refillIfNeeded()
  }

  async function setRepeatMode(m: string) {
    await PlayerService.SetRepeatMode(m)
  }

  async function cycleRepeat() {
    switch (repeatMode.value) {
      case RepeatMode.RepeatModeOff:
        await setRepeatMode(RepeatMode.RepeatModeAll)
        break
      case RepeatMode.RepeatModeAll:
        await setRepeatMode(RepeatMode.RepeatModeOne)
        break
      default:
        await setRepeatMode(RepeatMode.RepeatModeOff)
    }
  }

  // `fromRadio` is set by the Mood Radio store when it seeds its own queue,
  // so we don't tear down the radio it's in the middle of starting. Any other
  // caller (album/track/playlist play) is normal playback and must end an
  // active radio, otherwise the queue-drawer radio icon lingers.
  function stopMoodRadio() {
    const radio = useMoodRadioStore()
    if (radio.active) radio.stop()
  }

  async function playTracks(tracks: TrackDTO[], startIndex: number, fromRadio = false) {
    if (!fromRadio) stopMoodRadio()
    queue.value = tracks
    currentTrack.value = tracks[startIndex] ?? null
    await PlayerService.PlayTrackIDs(tracks.map(t => t.id), startIndex)
  }

  async function playQueueIndex(index: number) {
    currentTrack.value = queue.value[index] ?? null
    await PlayerService.PlayQueueIndex(index)
  }

  async function shuffleTracks(tracks: TrackDTO[]) {
    if (!tracks.length) return
    stopMoodRadio()
    // Send IDs only — Wails caps a single IPC call body at 64MB, and a full
    // 50k-track library serialized as TrackDTOs blows past that.
    await PlayerService.ShuffleTrackIDs(tracks.map(t => t.id))
    // The backend emits player:status and player:queue-updated which will update our local state
  }

  async function reorderQueue(tracks: TrackDTO[]) {
    queue.value = tracks
    const ids = tracks.map(t => t.id)
    await PlayerService.ReorderQueueIDs(ids)
  }


  function toggleQueue() {
    if (isQueueOpen.value) {
      isQueueOpen.value = false
    } else {
      openQueue()
    }
  }

  function openQueue() {
    isQueueOpen.value = true
    isLyricsOpen.value = false
    isTrackInfoOpen.value = false
  }

  function toggleLyrics() {
    if (isLyricsOpen.value) {
      isLyricsOpen.value = false
    } else {
      openLyrics()
    }
  }

  function openLyrics() {
    isLyricsOpen.value = true
    isQueueOpen.value = false
    isTrackInfoOpen.value = false
  }

  function openTrackInfo(track: TrackDTO | null) {
    if (!track) return
    trackInfoTrack.value = track
    isTrackInfoOpen.value = true
    isQueueOpen.value = false
    isLyricsOpen.value = false
  }

  function closeAllDrawers() {
    isQueueOpen.value = false
    isLyricsOpen.value = false
    isTrackInfoOpen.value = false
  }

  function setPlayerMode(mode: PlayerMode) {
    if (playerMode.value === mode) return
    playerMode.value = mode
  }

  return {
    // State
    status,
    queue,
    currentTrack,
    theme,
    isQueueOpen,
    isLyricsOpen,
    isTrackInfoOpen,
    trackInfoTrack,
    sidebarWidth,
    playerMode,
    lyrics,
    lyricsLoading,
    // Computed
    isPlaying,
    isPaused,
    isStopped,
    position,
    duration,
    volume,
    muted,
    shuffle,
    repeatMode,
    progressPercent,
    queueIds,
    artworkUrl,
    artworkUrlMd,
    artworkUrlSm,
    artworkCrossfade,
    // Actions
    init,
    dispose,
    syncState,
    play,
    pause,
    togglePlayPause,
    refreshCurrentLyrics,
    next,
    previous,
    fastForward,
    rewind,
    increaseVolume,
    decreaseVolume,
    toggleMute,
    seek,
    setVolume,
    setMuted,
    setShuffle,
    setRepeatMode,
    playTracks,
    playQueueIndex,
    shuffleTracks,
    reorderQueue,
    toggleQueue,
    openQueue,
    toggleLyrics,
    openLyrics,
    openTrackInfo,
    closeAllDrawers,
    setPlayerMode,
    cycleRepeat,
  }
})
