import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock Wails runtime
vi.mock('@wailsio/runtime', () => ({
  Events: {
    On: vi.fn(),
    Off: vi.fn(),
    Types: {
      Common: {
        WindowFullscreen: 'WindowFullscreen',
        WindowUnFullscreen: 'WindowUnFullscreen',
        WindowDidResize: 'WindowDidResize',
      },
      Mac: {
        WindowDidEnterFullScreen: 'WindowDidEnterFullScreen',
        WindowDidExitFullScreen: 'WindowDidExitFullScreen',
        WindowWillEnterFullScreen: 'WindowWillEnterFullScreen',
        WindowWillExitFullScreen: 'WindowWillExitFullScreen',
      }
    }
  },
  Window: {
    IsFullscreen: vi.fn().mockResolvedValue(false),
  }
}))

// Mock Greetservice bindings
const mockGetPlatform = vi.fn()
vi.mock('../../bindings/changeme/internal/infra/wails/greetservice', () => ({
  GetPlatform: () => mockGetPlatform(),
}))

import { useDeviceStore } from './device'

describe('useDeviceStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts with default values', () => {
    const store = useDeviceStore()
    expect(store.isMac).toBe(false)
    expect(store.isWindowFullscreen).toBe(false)
  })

  it('init identifies platform', async () => {
    mockGetPlatform.mockResolvedValue('darwin')
    const store = useDeviceStore()
    await store.init()
    expect(store.isMac).toBe(true)
  })

  it('init identifies non-mac platform', async () => {
    mockGetPlatform.mockResolvedValue('windows')
    const store = useDeviceStore()
    await store.init()
    expect(store.isMac).toBe(false)
  })
})
