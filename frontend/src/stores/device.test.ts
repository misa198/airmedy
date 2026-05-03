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
        WindowMaximise: 'WindowMaximise',
        WindowUnMaximise: 'WindowUnMaximise',
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
    IsMaximised: vi.fn().mockResolvedValue(false),
  }
}))

// Mock Greetservice bindings
const mockGetPlatform = vi.fn()
vi.mock('../../bindings/airmedy/internal/infra/wails/greetservice', () => ({
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
    expect(store.isWindows).toBe(false)
    expect(store.isLinux).toBe(false)
    expect(store.isWindowFullscreen).toBe(false)
  })

  it('init identifies mac platform', async () => {
    mockGetPlatform.mockResolvedValue('darwin')
    const store = useDeviceStore()
    await store.init()
    expect(store.isMac).toBe(true)
    expect(store.isWindows).toBe(false)
    expect(store.isLinux).toBe(false)
  })

  it('init identifies windows platform', async () => {
    mockGetPlatform.mockResolvedValue('windows')
    const store = useDeviceStore()
    await store.init()
    expect(store.isMac).toBe(false)
    expect(store.isWindows).toBe(true)
    expect(store.isLinux).toBe(false)
  })

  it('init identifies linux platform', async () => {
    mockGetPlatform.mockResolvedValue('linux')
    const store = useDeviceStore()
    await store.init()
    expect(store.isMac).toBe(false)
    expect(store.isWindows).toBe(false)
    expect(store.isLinux).toBe(true)
  })
})
