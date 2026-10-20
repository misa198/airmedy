import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useFavoritesStore } from './favorites'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

const mocks = vi.hoisted(() => ({
  handlers: new Map<string, (event: { data: unknown }) => void>(),
  toggle: vi.fn(),
  off: vi.fn(),
}))
vi.mock('../../bindings/airmedy/internal/infra/wails/libraryservice', () => ({
  ToggleFavorite: (...args: unknown[]) => mocks.toggle(...args),
}))
vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn((name: string, handler: (event: { data: unknown }) => void) => {
    mocks.handlers.set(name, handler)
    return mocks.off
  }) },
}))
const track = (favorite: boolean) => ({ id: 'a', is_favorite: favorite } as TrackDTO)
const emit = (favorite: boolean) => mocks.handlers.get('library:favorite-changed')!({ data: track(favorite) })

beforeEach(() => {
  setActivePinia(createPinia())
  mocks.handlers.clear()
  vi.clearAllMocks()
})

describe('favorites store', () => {
  it.each([false, true])('deduplicates event and response (event first: %s)', async (eventFirst) => {
    const store = useFavoritesStore()
    let resolve!: (state: boolean) => void
    mocks.toggle.mockImplementation(() => new Promise<boolean>(done => { resolve = done }))
    const pending = store.toggle('a')
    if (eventFirst) emit(true)
    resolve(true)
    await pending
    if (!eventFirst) emit(true)
    expect(store.version).toBe(1)
    expect(store.isFavorite(track(false))).toBe(true)
  })

  it('does not overwrite a newer external change with a late response', async () => {
    const store = useFavoritesStore()
    let resolve!: (state: boolean) => void
    mocks.toggle.mockImplementation(() => new Promise<boolean>(done => { resolve = done }))
    const pending = store.toggle('a')
    emit(true)
    emit(false)
    resolve(true)
    await pending
    expect(store.isFavorite(track(true))).toBe(false)
    expect(store.version).toBe(2)
  })

  it('keeps state on failure and accepts a retry', async () => {
    const store = useFavoritesStore()
    mocks.toggle.mockRejectedValueOnce(new Error('failed')).mockResolvedValueOnce(true)
    await expect(store.toggle('a')).rejects.toThrow('failed')
    expect(store.version).toBe(0)
    expect(store.isFavorite(track(false))).toBe(false)
    await store.toggle('a')
    expect(store.version).toBe(1)
  })

  it('seeds metadata updates without a second playlist invalidation and disposes listeners', () => {
    const store = useFavoritesStore()
    mocks.handlers.get('library:track-updated')!({ data: track(true) })
    expect(store.isFavorite(track(false))).toBe(true)
    expect(store.version).toBe(0)
    store.dispose()
    expect(mocks.off).toHaveBeenCalledTimes(2)
  })
})
