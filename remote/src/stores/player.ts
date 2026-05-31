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
  let _rafId: number | null = null

  function updateInterpolatedPosition() {
    if (remoteState.value?.playback_state === 'playing') {
      const elapsed = (performance.now() - _positionTimestamp) / 1000
      localPosition.value = Math.min(_positionBaseline + elapsed, trackMetadata.value?.duration ?? 0)
    } else {
      localPosition.value = _positionBaseline
    }
    _rafId = requestAnimationFrame(updateInterpolatedPosition)
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
    _positionTimestamp = performance.now()
    localPosition.value = state.position
    
    if (_rafId === null) {
      _rafId = requestAnimationFrame(updateInterpolatedPosition)
    }
  }

  function applyQueue(q: TrackDTO[]) { queue.value = q }
  function applyLyrics(l: Lyric | null) { lyrics.value = l }

  function dispose() {
    if (_rafId !== null) {
      cancelAnimationFrame(_rafId)
      _rafId = null
    }
  }

  return {
    authState, connected, connecting, reconnecting,
    status, queue, lyrics, currentTrack, artworkUrl,
    setAuthState, setConnected, setConnecting, setReconnecting,
    applyTrackMetadata, applyRemoteState, applyQueue, applyLyrics,
    dispose,
  }
})
