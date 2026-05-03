import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { PlaybackState, RepeatMode } from '../../bindings/airmedy/internal/domain/models'

// Mock Wails runtime — must be before any import that uses it
vi.mock('@wailsio/runtime', () => ({
  Events: {
    On: vi.fn(),
    Off: vi.fn(),
  },
  Create: {
    Nullable: (fn: any) => (v: any) => (v == null ? null : fn(v)),
    Array: (fn: any) => (arr: any[]) => (arr ?? []).map(fn),
    Struct: (ctor: any) => (v: any) => (v == null ? null : new ctor(v)),
    Map: (k: any, v: any) => (val: any) => val,
  },
  Call: {
    ByID: vi.fn().mockResolvedValue(null),
  },
}))

// Mock PlayerService bindings
const mockGetStatus = vi.fn()
const mockGetQueue = vi.fn()
vi.mock('../../bindings/airmedy/internal/infra/wails/playerservice', () => ({
  GetStatus: () => mockGetStatus(),
  GetQueue: () => mockGetQueue(),
  Play: vi.fn(),
  Pause: vi.fn(),
  Stop: vi.fn(),
  Next: vi.fn(),
  Previous: vi.fn(),
  Seek: vi.fn(),
  SetVolume: vi.fn(),
  SetMuted: vi.fn(),
  SetShuffle: vi.fn(),
  SetRepeatMode: vi.fn(),
  PlayTracks: vi.fn(),
}))

import { usePlayerStore } from './player'

describe('usePlayerStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts with no status', () => {
    const store = usePlayerStore()
    expect(store.status).toBeNull()
    expect(store.isPlaying).toBe(false)
    expect(store.isStopped).toBe(true)
  })

  it('computes isPlaying from status', () => {
    const store = usePlayerStore()
    store.status = {
      track_id: 't1',
      playback_state: PlaybackState.PlaybackStatePlaying,
      position: 30,
      duration: 180,
      volume: 0.8,
      muted: false,
      repeat_mode: RepeatMode.RepeatModeOff,
      shuffle: false,
    } as any
    expect(store.isPlaying).toBe(true)
    expect(store.isStopped).toBe(false)
  })

  it('computes progressPercent correctly', () => {
    const store = usePlayerStore()
    store.status = {
      track_id: 't1',
      playback_state: PlaybackState.PlaybackStatePlaying,
      position: 45,
      duration: 180,
      volume: 1,
      muted: false,
      repeat_mode: RepeatMode.RepeatModeOff,
      shuffle: false,
    } as any
    expect(store.progressPercent).toBeCloseTo(25)
  })

  it('returns 0 progressPercent when duration is 0', () => {
    const store = usePlayerStore()
    expect(store.progressPercent).toBe(0)
  })

  it('computes artworkUrl from currentTrack', () => {
    const store = usePlayerStore()
    store.currentTrack = { artwork_key: 'abc123.jpg' } as any
    expect(store.artworkUrl).toBe('/artwork/abc123.jpg')
  })

  it('returns null artworkUrl when no currentTrack', () => {
    const store = usePlayerStore()
    expect(store.artworkUrl).toBeNull()
  })

  it('toggleQueue flips isQueueOpen and closes other drawers', () => {
    const store = usePlayerStore()
    store.isLyricsOpen = true
    store.isTrackInfoOpen = true

    store.toggleQueue()
    expect(store.isQueueOpen).toBe(true)
    expect(store.isLyricsOpen).toBe(false)
    expect(store.isTrackInfoOpen).toBe(false)

    store.toggleQueue()
    expect(store.isQueueOpen).toBe(false)
  })

  it('toggleLyrics flips isLyricsOpen and closes other drawers', () => {
    const store = usePlayerStore()
    store.isQueueOpen = true
    store.isTrackInfoOpen = true

    store.toggleLyrics()
    expect(store.isLyricsOpen).toBe(true)
    expect(store.isQueueOpen).toBe(false)
    expect(store.isTrackInfoOpen).toBe(false)

    store.toggleLyrics()
    expect(store.isLyricsOpen).toBe(false)
  })

  it('openTrackInfo opens track info and closes other drawers', () => {
    const store = usePlayerStore()
    store.isQueueOpen = true
    store.isLyricsOpen = true

    store.openTrackInfo({ id: 't1' } as any)
    expect(store.isTrackInfoOpen).toBe(true)
    expect(store.isQueueOpen).toBe(false)
    expect(store.isLyricsOpen).toBe(false)
    expect(store.trackInfoTrack?.id).toBe('t1')
  })

  it('closeAllDrawers closes all drawers', () => {
    const store = usePlayerStore()
    store.isQueueOpen = true
    store.isLyricsOpen = true
    store.isTrackInfoOpen = true

    store.closeAllDrawers()
    expect(store.isQueueOpen).toBe(false)
    expect(store.isLyricsOpen).toBe(false)
    expect(store.isTrackInfoOpen).toBe(false)
  })

  it('init fetches status, theme and queue from backend', async () => {
    const fakeTheme = { vibrant: '#ff0000', muted: '#00ff00', dominant: '#0000ff' }
    const fakeStatus = {
      track_id: '',
      playback_state: PlaybackState.PlaybackStateStopped,
      position: 0,
      duration: 0,
      volume: 1,
      muted: false,
      repeat_mode: RepeatMode.RepeatModeOff,
      shuffle: false,
      theme: fakeTheme,
    }
    mockGetStatus.mockResolvedValue(fakeStatus)
    mockGetQueue.mockResolvedValue([])

    const store = usePlayerStore()
    await store.init()

    expect(store.status).toEqual(fakeStatus)
    expect(store.theme).toEqual(fakeTheme)
    expect(store.queue).toEqual([])
  })
})
