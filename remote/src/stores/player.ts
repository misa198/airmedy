import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { PlayerStatus, TrackDTO, Lyric } from '../ws'

export type AuthState = 'idle' | 'required' | 'authenticated' | 'failed'

export const usePlayerStore = defineStore('player', () => {
  const authState = ref<AuthState>('idle')
  const connected = ref(false)
  const connecting = ref(false)
  const reconnecting = ref(false)

  const status = ref<PlayerStatus | null>(null)
  const queue = ref<TrackDTO[]>([])
  const lyrics = ref<Lyric | null>(null)

  const currentTrack = computed(() => {
    if (!status.value?.track_id) return null
    return queue.value.find(t => t.id === status.value!.track_id) ?? null
  })

  const artworkUrl = (key: string, size = 'md') =>
    key ? `/artwork/${key}?size=${size}` : ''

  function setAuthState(s: AuthState) { authState.value = s }
  function setConnected(v: boolean) { connected.value = v }
  function setConnecting(v: boolean) { connecting.value = v }
  function setReconnecting(v: boolean) { reconnecting.value = v }

  function applyStatus(s: PlayerStatus) { status.value = s }
  function applyQueue(q: TrackDTO[]) { queue.value = q }
  function applyLyrics(l: Lyric | null) { lyrics.value = l }

  return {
    authState, connected, connecting, reconnecting,
    status, queue, lyrics, currentTrack, artworkUrl,
    setAuthState, setConnected, setConnecting, setReconnecting,
    applyStatus, applyQueue, applyLyrics,
  }
})
