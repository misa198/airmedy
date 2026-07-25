import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockSearch = vi.fn()

vi.mock('../../bindings/airmedy/internal/infra/wails/searchservice', () => ({
  Search: (...args: unknown[]) => mockSearch(...args),
}))

import { useSearchStore } from './search'

describe('useSearchStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockSearch.mockReset()
  })

  it('refreshes the active query immediately without clearing visible results', async () => {
    const store = useSearchStore()
    store.query = 'old album'
    store.results = { albums: [{ id: 'old' }] } as any
    mockSearch.mockResolvedValue({ albums: [{ id: 'new' }] })

    await store.refresh()

    expect(mockSearch).toHaveBeenCalledWith('old album')
    expect(store.results).toEqual({ albums: [{ id: 'new' }] })
    expect(store.loading).toBe(false)
  })
})
