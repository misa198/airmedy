import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const handlers = vi.hoisted(() => new Map<string, (event: { data?: unknown }) => void>())
const mockGetTrackCount = vi.hoisted(() => vi.fn())
const mockGetRecentlyPlayedTracks = vi.hoisted(() => vi.fn())
const mockGetMostListenedTracks = vi.hoisted(() => vi.fn())
const mockGetLeastListenedTracks = vi.hoisted(() => vi.fn())

vi.mock('@wailsio/runtime', () => ({
  Events: {
    On: vi.fn((name: string, handler: (event: { data?: unknown }) => void) => {
      handlers.set(name, handler)
      return () => handlers.delete(name)
    }),
  },
  Create: {
    Nullable: (fn: (value: unknown) => unknown) => (value: unknown) => value == null ? null : fn(value),
    Array: (fn: (value: unknown) => unknown) => (value: unknown[]) => (value ?? []).map(fn),
    Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value),
    Map: () => (value: unknown) => value,
  },
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('../../../bindings/airmedy/internal/infra/wails/libraryservice', () => ({
  GetTrackCount: mockGetTrackCount,
  GetRecentlyPlayedTracks: mockGetRecentlyPlayedTracks,
  GetMostListenedTracks: mockGetMostListenedTracks,
  GetLeastListenedTracks: mockGetLeastListenedTracks,
}))

import HomeOverview from './HomeOverview.vue'

describe('HomeOverview', () => {
  let pinia = createPinia()

  beforeEach(() => {
    vi.useFakeTimers()
    handlers.clear()
    pinia = createPinia()
    setActivePinia(pinia)
    mockGetTrackCount.mockResolvedValue(1)
    mockGetRecentlyPlayedTracks.mockResolvedValue([])
    mockGetMostListenedTracks.mockResolvedValue([])
    mockGetLeastListenedTracks.mockResolvedValue([])
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('refreshes carousel tracks after a metadata update', async () => {
    mount(HomeOverview, {
      global: {
        plugins: [pinia],
        stubs: { HomeSection: true, TrackCard: true, TrackContextMenu: true },
      },
    })
    await flushPromises()

    handlers.get('library:track-updated')?.({})
    await vi.advanceTimersByTimeAsync(50)
    await flushPromises()

    expect(mockGetRecentlyPlayedTracks).toHaveBeenCalledTimes(2)
    expect(mockGetMostListenedTracks).toHaveBeenCalledTimes(2)
    expect(mockGetLeastListenedTracks).toHaveBeenCalledTimes(2)
  })
})
