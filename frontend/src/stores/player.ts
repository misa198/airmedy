import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { Events } from '@wailsio/runtime'
import * as PlayerService from '../../bindings/airmedy/internal/infra/wails/playerservice'
import { PlaybackState, PlayerStatus, RepeatMode } from '../../bindings/airmedy/internal/domain/models'
import type { Lyric, TrackDTO } from '../../bindings/airmedy/internal/domain/models'

export interface ThemeColors {
  vibrant: string
  muted: string
  dominant: string
}

export type PlayerMode = 'sticky' | 'mini' | 'fullscreen'

export const usePlayerStore = defineStore('player', () => {
  // State
  const status = ref<PlayerStatus | null>(null)
  const queue = ref<TrackDTO[]>([])
  const currentTrack = ref<TrackDTO | null>(null)
  const theme = ref<ThemeColors | null>(null)
  const isQueueOpen = ref(false)
  const isLyricsOpen = ref(false)
  const sidebarWidth = ref(260)
  const playerMode = ref<PlayerMode>('sticky')
  const lyrics = ref<Lyric | null>(null)
  const lyricsLoading = ref(false)

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
  const position = computed(() => status.value?.position ?? 0)
  const duration = computed(() => status.value?.duration ?? 0)
  const volume = computed(() => status.value?.volume ?? 1)
  const muted = computed(() => status.value?.muted ?? false)
  const shuffle = computed(() => status.value?.shuffle ?? false)
  const repeatMode = computed(() => status.value?.repeat_mode ?? RepeatMode.RepeatModeOff)
  const progressPercent = computed(() =>
    duration.value > 0 ? (position.value / duration.value) * 100 : 0,
  )
  const artworkUrl = computed(() => {
    const key = currentTrack.value?.artwork_key
    return key ? `/artwork/${key}` : null
  })

  // Clear lyrics immediately whenever the playing track changes
  watch(currentTrack, (newTrack, oldTrack) => {
    if (newTrack?.id !== oldTrack?.id) {
      lyrics.value = null
      lyricsLoading.value = true
    }
  })

  // Actions
  async function init() {
    console.log('[PlayerStore] Initializing...')
    try {
      const s = await PlayerService.GetStatus()
      status.value = s
      const q = await PlayerService.GetQueue()
      queue.value = (q.filter(Boolean) as TrackDTO[])
      if (s.track_id) {
        currentTrack.value = queue.value.find((t) => t.id === s.track_id) ?? null
      }
    } catch (e) {
      console.error('Failed to init player store', e)
    }

    Events.On('player:status', (ev: Events.WailsEvent) => {
      const s = ev.data as PlayerStatus
      status.value = s
      if (s?.track_id) {
        const found = queue.value.find((t) => t.id === s.track_id)
        if (found) currentTrack.value = found
      } else if (s?.playback_state === PlaybackState.PlaybackStateStopped) {
        // Keep the current track even when stopped so the UI can show it as the last active track
        // unless we explicitly want to clear it. For now, we keep it.
      }
    })

    Events.On('player:theme', (ev: Events.WailsEvent) => {
      theme.value = ev.data as ThemeColors
    })

    Events.On('player:lyrics', (ev: Events.WailsEvent) => {
      const lyric = (ev.data as Lyric) ?? null
      // Discard stale lyrics from a previous track (race condition on fast skipping)
      if (lyric && lyric.track_id !== currentTrack.value?.id) return
      // Discard a stale null if we already have correct lyrics for the current track
      if (!lyric && lyrics.value?.track_id === currentTrack.value?.id) return
      lyrics.value = lyric
      lyricsLoading.value = false
    })

    Events.On('player:queue-updated', (ev: Events.WailsEvent) => {
      const q = ev.data as TrackDTO[]
      if (Array.isArray(q)) queue.value = q.filter(Boolean) as TrackDTO[]
    })

    Events.On('library:track-updated', (ev: Events.WailsEvent) => {
      const updated = ev.data as TrackDTO
      if (!updated?.id) return
      const idx = queue.value.findIndex(t => t.id === updated.id)
      if (idx !== -1) queue.value[idx] = updated
      if (currentTrack.value?.id === updated.id) currentTrack.value = updated
    })

    Events.On('library:track-deleted', (ev: Events.WailsEvent) => {
      const id = ev.data as string
      if (!id) return
      queue.value = queue.value.filter(t => t.id !== id)
      if (currentTrack.value?.id === id) currentTrack.value = null
    })
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

  async function next() {
    await PlayerService.Next()
  }

  async function previous() {
    await PlayerService.Previous()
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

  async function playTracks(tracks: TrackDTO[], startIndex: number) {
    queue.value = tracks
    currentTrack.value = tracks[startIndex] ?? null
    await PlayerService.PlayTracks(tracks, startIndex)
  }


  function toggleQueue() {
    isQueueOpen.value = !isQueueOpen.value
    if (isQueueOpen.value) isLyricsOpen.value = false
  }

  function toggleLyrics() {
    isLyricsOpen.value = !isLyricsOpen.value
    if (isLyricsOpen.value) isQueueOpen.value = false
  }

  return {
    // State
    status,
    queue,
    currentTrack,
    theme,
    isQueueOpen,
    isLyricsOpen,
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
    artworkUrl,
    // Actions
    init,
    play,
    pause,
    togglePlayPause,
    next,
    previous,
    seek,
    setVolume,
    setMuted,
    setShuffle,
    setRepeatMode,
    playTracks,
    toggleQueue,
    toggleLyrics,
    cycleRepeat,
  }
})
