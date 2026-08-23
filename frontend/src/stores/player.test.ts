import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { nextTick } from 'vue'
import { PlaybackState, RepeatMode } from '../../bindings/airmedy/internal/domain/models'

// Mock Wails runtime — must be before any import that uses it
vi.mock('@wailsio/runtime', () => ({
  Events: {
    On: vi.fn(() => () => {}),
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
const mockGetCurrentLyrics = vi.fn()
const mockRefreshCurrentLyrics = vi.fn()
const mockAppendTracks = vi.fn()
const mockGenerateMoodRadio = vi.fn()
vi.mock('../../bindings/airmedy/internal/infra/wails/playerservice', () => ({
  GetStatus: () => mockGetStatus(),
  GetQueue: () => mockGetQueue(),
  GetCurrentLyrics: () => mockGetCurrentLyrics(),
  RefreshCurrentLyrics: () => mockRefreshCurrentLyrics(),
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
  AppendTracks: (...args: unknown[]) => mockAppendTracks(...args),
}))

vi.mock('../../bindings/airmedy/internal/infra/wails/moodradioservice', () => ({
  GenerateMoodRadio: (...args: unknown[]) => mockGenerateMoodRadio(...args),
  GetMoodRadioActive: vi.fn().mockResolvedValue(false),
  SetMoodRadioActive: vi.fn(),
}))

import { useAppStore } from './app'
import { useMoodRadioStore } from './moodRadio'
import { usePlayerStore } from './player'
import { Events } from '@wailsio/runtime'

describe('usePlayerStore', () => {
  beforeEach(() => {
    vi.useRealTimers()
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

  it('computes progressPercent correctly', async () => {
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
    await nextTick()
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

  it('keeps lyrics that arrive for a newly selected track before the track watcher flushes', async () => {
    const store = usePlayerStore()
    store.currentTrack = { id: 'old-track' } as any
    await nextTick()

    store.currentTrack = { id: 'new-track' } as any
    store.lyrics = { track_id: 'new-track', content: '[00:01.00]Ready' } as any
    await nextTick()

    expect(store.lyrics?.content).toBe('[00:01.00]Ready')
    expect(store.lyricsLoading).toBe(false)
  })

  it('ignores stale lyric events and clears loading after the matching terminal event', async () => {
    vi.stubGlobal('requestAnimationFrame', vi.fn(() => 1))
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    mockGetStatus.mockResolvedValue({
      track_id: 't1',
      lyrics_request_id: 2,
      playback_state: PlaybackState.PlaybackStatePlaying,
      position: 0,
      duration: 180,
      volume: 1,
      muted: false,
      repeat_mode: RepeatMode.RepeatModeOff,
      shuffle: false,
    })
    mockGetQueue.mockResolvedValue([{ id: 't1' }])
    mockGetCurrentLyrics.mockResolvedValue({
      track_id: 't1', request_id: 2, state: 'ready', lyric: { track_id: 't1', content: 'initial' },
    })

    const store = usePlayerStore()
    await store.init()
    const lyricsListener = (Events.On as any).mock.calls.find(([name]: [string]) => name === 'player:lyrics')[1]

    lyricsListener({ data: { track_id: 't1', request_id: 1, state: 'ready', lyric: { track_id: 't1', content: 'stale' } } })
    expect(store.lyrics?.content).toBe('initial')

    lyricsListener({ data: { track_id: 't1', request_id: 2, state: 'loading' } })
    expect(store.lyricsLoading).toBe(true)
    lyricsListener({ data: { track_id: 't1', request_id: 2, state: 'error' } })
    expect(store.lyrics?.content).toBe('initial')
    expect(store.lyricsLoading).toBe(false)
    store.dispose()
  })

  it('keeps a manually selected lyric ready when its event arrives before the status update', async () => {
    vi.stubGlobal('requestAnimationFrame', vi.fn(() => 1))
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    mockGetStatus.mockResolvedValue({
      track_id: 't1',
      lyrics_request_id: 2,
      playback_state: PlaybackState.PlaybackStatePlaying,
      position: 0,
      duration: 180,
      volume: 1,
      muted: false,
      repeat_mode: RepeatMode.RepeatModeOff,
      shuffle: false,
    })
    mockGetQueue.mockResolvedValue([{ id: 't1' }])
    mockGetCurrentLyrics.mockResolvedValue({
      track_id: 't1', request_id: 2, state: 'ready', lyric: { track_id: 't1', content: 'local lyric' },
    })

    const store = usePlayerStore()
    await store.init()
    const lyricsListener = (Events.On as any).mock.calls.find(([name]: [string]) => name === 'player:lyrics')[1]
    const statusListener = (Events.On as any).mock.calls.find(([name]: [string]) => name === 'player:status')[1]

    // This is the event ordering produced when a second manual selection's
    // `ready` event overtakes its `player:status` notification.
    lyricsListener({ data: { track_id: 't1', request_id: 3, state: 'ready', lyric: { track_id: 't1', content: 'selected lyric' } } })
    statusListener({ data: {
      track_id: 't1',
      lyrics_request_id: 3,
      playback_state: PlaybackState.PlaybackStatePlaying,
      position: 0,
      duration: 180,
      volume: 1,
      muted: false,
      repeat_mode: RepeatMode.RepeatModeOff,
      shuffle: false,
    } })

    expect(store.lyrics?.content).toBe('selected lyric')
    expect(store.lyricsLoading).toBe(false)
    store.dispose()
  })

  it('accepts a newer initial lyrics snapshot when the first online fetch races startup', async () => {
    vi.stubGlobal('requestAnimationFrame', vi.fn(() => 1))
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    mockGetStatus.mockResolvedValue({
      track_id: 't1',
      lyrics_request_id: 1,
      playback_state: PlaybackState.PlaybackStatePlaying,
      position: 0,
      duration: 180,
      volume: 1,
      muted: false,
      repeat_mode: RepeatMode.RepeatModeOff,
      shuffle: false,
    })
    mockGetQueue.mockResolvedValue([{ id: 't1' }])
    // The first online request can advance and finish while init is awaiting
    // GetQueue, before player:lyrics listeners have been registered.
    mockGetCurrentLyrics.mockResolvedValue({
      track_id: 't1',
      request_id: 2,
      state: 'ready',
      lyric: { track_id: 't1', content: 'fetched online' },
    })

    const store = usePlayerStore()
    await store.init()

    expect(store.lyrics?.content).toBe('fetched online')
    expect(store.lyricsLoading).toBe(false)
    store.dispose()
  })

  it('reconciles a refresh result when the first online terminal event is missed', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('requestAnimationFrame', vi.fn(() => 1))
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    mockGetStatus.mockResolvedValue({
      track_id: 't1',
      lyrics_request_id: 1,
      playback_state: PlaybackState.PlaybackStatePlaying,
      position: 0,
      duration: 180,
      volume: 1,
      muted: false,
      repeat_mode: RepeatMode.RepeatModeOff,
      shuffle: false,
    })
    mockGetQueue.mockResolvedValue([{ id: 't1' }])
    mockGetCurrentLyrics
      .mockResolvedValueOnce({
        track_id: 't1', request_id: 1, state: 'ready', lyric: null,
      })
      .mockResolvedValueOnce({
        track_id: 't1', request_id: 2, state: 'loading', lyric: null,
      })
      .mockResolvedValueOnce({
        track_id: 't1',
        request_id: 2,
        state: 'ready',
        lyric: { track_id: 't1', content: 'first online result' },
      })
    mockRefreshCurrentLyrics.mockResolvedValue(2)

    const store = usePlayerStore()
    await store.init()
    await store.refreshCurrentLyrics()
    expect(store.lyricsLoading).toBe(true)

    await vi.advanceTimersByTimeAsync(250)
    expect(store.lyricsLoading).toBe(true)
    await vi.advanceTimersByTimeAsync(250)

    expect(store.lyrics?.content).toBe('first online result')
    expect(store.lyricsLoading).toBe(false)
    store.dispose()
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

  it('checks Mood Radio refill after unshuffle reorders the current track to the tail', async () => {
    const app = useAppStore()
    app.libraryAnalysisEnabled = true
    const radio = useMoodRadioStore()
    radio.active = true
    const store = usePlayerStore()
    store.currentTrack = { id: 'G' } as any
    mockGetQueue.mockResolvedValue([{ id: 'A' }, { id: 'B' }, { id: 'C' }, { id: 'D' }, { id: 'E' }, { id: 'G' }])
    mockGenerateMoodRadio.mockResolvedValue([{ id: 'refill' }])

    await store.setShuffle(false)
    await nextTick()
    await nextTick()

    expect(mockGenerateMoodRadio).toHaveBeenCalledWith('G', ['A', 'B', 'C', 'D', 'E', 'G'], 15)
    expect(mockAppendTracks).toHaveBeenCalledWith([{ id: 'refill' }])
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
