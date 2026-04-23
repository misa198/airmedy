import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { Events } from '@wailsio/runtime'
import * as PlayerService from '../../bindings/changeme/internal/infra/wails/playerservice'
import { PlaybackState, PlayerStatus, RepeatMode } from '../../bindings/changeme/internal/domain/models'
import type { TrackDTO } from '../../bindings/changeme/internal/domain/models'

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
  const playerMode = ref<PlayerMode>('sticky')

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

  // Actions
  async function init() {
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
      }
    })

    Events.On('player:theme', (ev: Events.WailsEvent) => {
      theme.value = ev.data as ThemeColors
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
  }

  return {
    // State
    status,
    queue,
    currentTrack,
    theme,
    isQueueOpen,
    playerMode,
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
    cycleRepeat,
  }
})
