import { usePlayerStore } from './stores/player'

export type InboundMessage =
  | { type: 'auth_required' }
  | { type: 'auth_ok'; token: string; state: { track_metadata: PlayerTrackMetadata; player_state: RemotePlayerState; queue: TrackDTO[] } }
  | { type: 'auth_failed'; reason: string }
  | { type: 'track_metadata'; data: PlayerTrackMetadata }
  | { type: 'player_state'; data: RemotePlayerState }
  | { type: 'queue'; data: TrackDTO[] }
  | { type: 'lyrics'; data: Lyric | null }
  | { type: 'error'; message: string }

export interface PlayerTrackMetadata {
  track_id: string
  duration: number
  theme: ThemeColors | null
}

export interface RemotePlayerState {
  playback_state: 'playing' | 'paused' | 'stopped'
  position: number
  volume: number
  muted: boolean
  repeat_mode: 'off' | 'one' | 'all'
  shuffle: boolean
}

// Unified shape used by Vue components via the store's status computed.
export interface PlayerStatus {
  track_id: string
  playback_state: 'playing' | 'paused' | 'stopped'
  position: number
  duration: number
  volume: number
  muted: boolean
  repeat_mode: 'off' | 'one' | 'all'
  shuffle: boolean
  theme: ThemeColors | null
}

export interface ThemeColors {
  vibrant: string
  muted: string
  dominant: string
}

export interface TrackDTO {
  id: string
  path: string
  title: string
  duration: number
  artwork_key: string
  artists?: Artist[]
  album?: Album
  album_artists?: Artist[]
}

export interface Artist {
  id: string
  name: string
}

export interface Album {
  id: string
  title: string
  artwork_key: string
}

export interface Lyric {
  track_id: string
  content: string
  source: string
}

const WS_TOKEN_KEY = 'airmedy_remote_token'
const MAX_BACKOFF_MS = 30_000

let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let backoffMs = 1000
let shouldReconnect = true

export function getStoredToken(): string {
  return localStorage.getItem(WS_TOKEN_KEY) ?? ''
}

export function clearToken(): void {
  localStorage.removeItem(WS_TOKEN_KEY)
}

function wsUrl(): string {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${location.host}/ws`
}

export function connect(): void {
  shouldReconnect = true
  openConnection()
}

export function disconnect(): void {
  shouldReconnect = false
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  ws?.close()
}

export function send(msg: object): void {
  if (ws?.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(msg))
  }
}

function openConnection(): void {
  if (ws && ws.readyState !== WebSocket.CLOSED) return

  const store = usePlayerStore()
  store.setConnecting(true)

  const url = wsUrl()
  console.debug(`[WS] Connecting to ${url}`)
  ws = new WebSocket(url)

  ws.onopen = () => {
    console.debug('[WS] Connection opened')
    store.setConnected(true)
    backoffMs = 1000
  }

  ws.onmessage = (event) => {
    try {
      const msg: InboundMessage = JSON.parse(event.data)
      console.debug('[WS] Received:', msg.type)
      handleMessage(msg)
    } catch {
      console.error('[WS] Failed to parse message:', event.data)
    }
  }

  ws.onerror = (err) => {
    console.error('[WS] Error event:', err)
  }

  ws.onclose = (event) => {
    console.debug(`[WS] Connection closed: code=${event.code}, reason=${event.reason}, wasClean=${event.wasClean}`)
    store.setConnected(false)
    ws = null
    if (shouldReconnect) {
      scheduleReconnect(store.authState === 'required')
    }
  }
}

function scheduleReconnect(silent = false): void {
  const store = usePlayerStore()
  if (!silent) store.setReconnecting(true)
  console.debug(`[WS] Scheduling reconnect in ${backoffMs}ms (silent=${silent})`)
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    openConnection()
    backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS)
  }, backoffMs)
}

function handleMessage(msg: InboundMessage): void {
  const store = usePlayerStore()

  switch (msg.type) {
    case 'auth_required':
      console.debug('[WS] Auth required')
      store.setAuthState('required')
      store.setConnecting(false)
      store.setReconnecting(false)
      {
        const token = getStoredToken()
        if (token) {
          console.debug('[WS] Sending stored token')
          store.setConnecting(true)
          send({ type: 'auth', token })
        }
      }
      break

    case 'auth_ok':
      console.debug('[WS] Auth OK')
      localStorage.setItem(WS_TOKEN_KEY, msg.token)
      store.setAuthState('authenticated')
      store.setConnecting(false)
      store.setReconnecting(false)
      store.applyTrackMetadata(msg.state.track_metadata)
      store.applyRemoteState(msg.state.player_state)
      store.applyQueue(msg.state.queue)
      break

    case 'auth_failed':
      console.error('[WS] Auth failed:', msg.reason)
      clearToken()
      store.setAuthState('failed')
      store.setConnecting(false)
      store.setReconnecting(false)
      break

    case 'track_metadata':
      store.applyTrackMetadata(msg.data)
      break

    case 'player_state':
      store.applyRemoteState(msg.data)
      break

    case 'queue':
      store.applyQueue(msg.data)
      break

    case 'lyrics':
      store.applyLyrics(msg.data)
      break
  }
}
