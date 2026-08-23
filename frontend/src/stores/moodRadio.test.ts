import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

const mockGenerateMoodRadio = vi.fn()
const mockPlayTrackIDs = vi.fn()
const mockReplaceQueueKeepingCurrentTrackIDs = vi.fn()
const mockAppendTracks = vi.fn()
const mockGetMoodRadioActive = vi.fn().mockResolvedValue(false)
const mockSetMoodRadioActive = vi.fn()
let moodRadioStateListener: ((event: { data: boolean }) => void) | undefined

vi.mock('@wailsio/runtime', () => ({
  Events: {
    On: vi.fn((name: string, listener: (event: { data: boolean }) => void) => {
      if (name === 'mood-radio:state') moodRadioStateListener = listener
      return () => { if (name === 'mood-radio:state') moodRadioStateListener = undefined }
    }),
  },
  Create: {
    Nullable: (fn: (value: unknown) => unknown) => (value: unknown) => value == null ? null : fn(value),
    Array: (fn: (value: unknown) => unknown) => (values: unknown[]) => (values ?? []).map(fn),
    Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value),
    Map: () => (value: unknown) => value,
  },
  Call: { ByID: vi.fn() },
}))

vi.mock('../../bindings/airmedy/internal/infra/wails/moodradioservice', () => ({
  GenerateMoodRadio: (...args: unknown[]) => mockGenerateMoodRadio(...args),
  GetMoodRadioActive: () => mockGetMoodRadioActive(),
  SetMoodRadioActive: (active: boolean) => mockSetMoodRadioActive(active),
}))

vi.mock('../../bindings/airmedy/internal/infra/wails/playerservice', () => ({
  PlayTrackIDs: (...args: unknown[]) => mockPlayTrackIDs(...args),
  ReplaceQueueKeepingCurrentTrackIDs: (...args: unknown[]) => mockReplaceQueueKeepingCurrentTrackIDs(...args),
  AppendTracks: (...args: unknown[]) => mockAppendTracks(...args),
}))

import { useAppStore } from './app'
import { useMoodRadioStore } from './moodRadio'
import { usePlayerStore } from './player'

describe('useMoodRadioStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockGenerateMoodRadio.mockResolvedValue([])
    mockPlayTrackIDs.mockResolvedValue(undefined)
    mockReplaceQueueKeepingCurrentTrackIDs.mockResolvedValue(undefined)
    mockAppendTracks.mockResolvedValue(undefined)
    mockGetMoodRadioActive.mockResolvedValue(false)
    moodRadioStateListener = undefined
  })

  it('starts with generated candidates and the seed track first', async () => {
    mockGenerateMoodRadio.mockResolvedValue([{ id: 'similar' }])

    await useMoodRadioStore().start({ id: 'seed' } as any)

    expect(mockGenerateMoodRadio).toHaveBeenCalledWith('seed', [], 15)
    expect(mockPlayTrackIDs).toHaveBeenCalledWith(['seed', 'similar'], 0)
    expect(mockSetMoodRadioActive).toHaveBeenCalledWith(true)
  })

  it('keeps the current seed playing when it starts Mood Radio', async () => {
    const player = usePlayerStore()
    player.currentTrack = { id: 'seed' } as any
    mockGenerateMoodRadio.mockResolvedValue([{ id: 'similar' }])

    await useMoodRadioStore().start({ id: 'seed' } as any)

    expect(mockReplaceQueueKeepingCurrentTrackIDs).toHaveBeenCalledWith(['seed', 'similar'])
    expect(mockPlayTrackIDs).not.toHaveBeenCalled()
    expect(player.queue).toEqual([{ id: 'seed' }, { id: 'similar' }])
  })

  it('syncs the active state broadcast by another window', () => {
    const radio = useMoodRadioStore()
    radio.init(false)

    moodRadioStateListener?.({ data: true })

    expect(radio.active).toBe(true)
    radio.dispose()
  })

  it('excludes the full existing queue when refilling', async () => {
    const app = useAppStore()
    app.libraryAnalysisEnabled = true
    const player = usePlayerStore()
    const radio = useMoodRadioStore()
    radio.active = true
    radio.init()
    player.queue = [{ id: 'seed' }, { id: 'current' }] as any
    player.currentTrack = { id: 'current' } as any

    await nextTick()
    await nextTick()

    expect(mockGenerateMoodRadio).toHaveBeenCalledWith('current', ['seed', 'current'], 15)
    radio.dispose()
  })

  it('refills after unshuffle moves the current radio track near the queue tail', async () => {
    const app = useAppStore()
    app.libraryAnalysisEnabled = true
    const player = usePlayerStore()
    player.queue = [{ id: 'D' }, { id: 'C' }, { id: 'G' }, { id: 'B' }, { id: 'A' }, { id: 'E' }] as any
    player.currentTrack = { id: 'G' } as any
    const radio = useMoodRadioStore()
    radio.active = true
    radio.init()
    mockGenerateMoodRadio.mockResolvedValue([{ id: 'refill' }])

    // Unshuffle keeps G playing but restores the original source order,
    // placing G at the tail. Queue length and current ID are unchanged.
    player.queue = [{ id: 'A' }, { id: 'B' }, { id: 'C' }, { id: 'D' }, { id: 'E' }, { id: 'G' }] as any

    await vi.waitFor(() => {
      expect(mockAppendTracks).toHaveBeenCalledWith([{ id: 'refill' }])
    })

    expect(mockGenerateMoodRadio).toHaveBeenCalledWith('G', ['A', 'B', 'C', 'D', 'E', 'G'], 15)
    radio.dispose()
  })
})
