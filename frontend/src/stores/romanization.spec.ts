import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRomanizationStore } from './romanization'

const api = vi.hoisted(() => ({ GetRomanizationEnabled: vi.fn(), SetRomanizationEnabled: vi.fn() }))
const events = vi.hoisted(() => ({ On: vi.fn(() => vi.fn()) }))
vi.mock('../../bindings/airmedy/internal/infra/wails/lyricsservice', () => api)
vi.mock('@wailsio/runtime', () => ({ Events: events }))

describe('romanization store', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('hydrates and updates the backend session preference', async () => {
    api.GetRomanizationEnabled.mockResolvedValue(true)
    api.SetRomanizationEnabled.mockResolvedValue(undefined)
    setActivePinia(createPinia())
    const store = useRomanizationStore()
    await Promise.resolve()
    expect(store.enabled).toBe(true)
    await store.setEnabled(false)
    expect(api.SetRomanizationEnabled).toHaveBeenCalledWith(false)
    store.dispose()
  })
})
