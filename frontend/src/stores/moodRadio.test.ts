import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

const mockGenerateMoodRadio = vi.fn()
const mockPlayTrackIDs = vi.fn()
const mockAppendTracks = vi.fn()

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => () => {}) },
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
}))

vi.mock('../../bindings/airmedy/internal/infra/wails/playerservice', () => ({
  PlayTrackIDs: (...args: unknown[]) => mockPlayTrackIDs(...args),
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
    mockAppendTracks.mockResolvedValue(undefined)
  })

  it('starts with generated candidates and the seed track first', async () => {
    mockGenerateMoodRadio.mockResolvedValue([{ id: 'similar' }])

    await useMoodRadioStore().start({ id: 'seed' } as any)

    expect(mockGenerateMoodRadio).toHaveBeenCalledWith('seed', [], 15)
    expect(mockPlayTrackIDs).toHaveBeenCalledWith(['seed', 'similar'], 0)
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
})
