import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { PlayerTrackMetadata, RemotePlayerState, PlayerStatus, TrackDTO, Lyric } from '../ws'

export type AuthState = 'idle' | 'required' | 'authenticated' | 'failed'

export const usePlayerStore = defineStore('player', () => {
  const authState = ref<AuthState>('idle')
  const connected = ref(false)
  const connecting = ref(false)
  const reconnecting = ref(false)

  const trackMetadata = ref<PlayerTrackMetadata | null>(null)
  const remoteState = ref<RemotePlayerState | null>(null)
  const queue = ref<TrackDTO[]>([])
  const lyrics = ref<Lyric | null>(null)

  // Local progress interpolation — avoids 500ms server tick for position updates
  const localPosition = ref(0)
  let _positionBaseline = 0
  let _positionTimestamp = 0
  let _progressTimer: ReturnType<typeof setInterval> | null = null

  function _startProgressTimer() {
    _stopProgressTimer()
    _progressTimer = setInterval(() => {
      const elapsed = (Date.now() - _positionTimestamp) / 1000
      localPosition.value = _positionBaseline + elapsed
    }, 250)
  }

  function _stopProgressTimer() {
    if (_progressTimer) {
      clearInterval(_progressTimer)
      _progressTimer = null
    }
  }

  // Unified status computed — backward compat with all Vue components
  const status = computed((): PlayerStatus | null => {
    if (!trackMetadata.value && !remoteState.value) return null
    return {
      track_id: trackMetadata.value?.track_id ?? '',
      playback_state: remoteState.value?.playback_state ?? 'stopped',
      position: localPosition.value,
      duration: trackMetadata.value?.duration ?? 0,
      volume: remoteState.value?.volume ?? 1,
      muted: remoteState.value?.muted ?? false,
      repeat_mode: remoteState.value?.repeat_mode ?? 'off',
      shuffle: remoteState.value?.shuffle ?? false,
      theme: trackMetadata.value?.theme ?? null,
    }
  })

  const currentTrack = computed(() => {
    if (!trackMetadata.value?.track_id) return null
    return queue.value.find(t => t.id === trackMetadata.value!.track_id) ?? null
  })

  const artworkUrl = (key: string, size = 'md') =>
    key ? `/artwork/${key}?size=${size}` : ''

  function setAuthState(s: AuthState) { authState.value = s }
  function setConnected(v: boolean) { connected.value = v }
  function setConnecting(v: boolean) { connecting.value = v }
  function setReconnecting(v: boolean) { reconnecting.value = v }

  function applyTrackMetadata(meta: PlayerTrackMetadata) {
    trackMetadata.value = meta
  }

  function applyRemoteState(state: RemotePlayerState) {
    remoteState.value = state
    _positionBaseline = state.position
    _positionTimestamp = Date.now()
    localPosition.value = state.position
    if (state.playback_state === 'playing') {
      _startProgressTimer()
    } else {
      _stopProgressTimer()
    }
  }

  function applyQueue(q: TrackDTO[]) { queue.value = q }
  function applyLyrics(l: Lyric | null) { lyrics.value = l }

  function dispose() {
    _stopProgressTimer()
  }

  return {
    authState, connected, connecting, reconnecting,
    status, queue, lyrics, currentTrack, artworkUrl,
    setAuthState, setConnected, setConnecting, setReconnecting,
    applyTrackMetadata, applyRemoteState, applyQueue, applyLyrics,
    dispose,
  }
})
