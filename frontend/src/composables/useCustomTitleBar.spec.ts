import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import type { PlayerMode } from '@/stores/player'
import { useCustomTitleBar } from './useCustomTitleBar'

vi.mock('@/stores/device', () => ({ useDeviceStore: () => deviceStore }))
vi.mock('@/stores/player', () => ({ usePlayerStore: () => playerStore }))

const deviceStore = reactive({
  isMac: false,
  isWindows: false,
  isLinux: false,
  isWindowFullscreen: false,
  isWindowMaximized: false,
})
const playerStore = reactive({ playerMode: 'sticky' as PlayerMode })

describe('useCustomTitleBar', () => {
  beforeEach(() => {
    Object.assign(deviceStore, {
      isMac: false,
      isWindows: false,
      isLinux: false,
      isWindowFullscreen: false,
      isWindowMaximized: false,
    })
    playerStore.playerMode = 'sticky'
  })

  it('returns false before the platform is known and on macOS', () => {
    const hasCustomTitleBar = useCustomTitleBar()
    expect(hasCustomTitleBar.value).toBe(false)

    deviceStore.isMac = true
    expect(hasCustomTitleBar.value).toBe(false)
  })

  it.each(['isWindows', 'isLinux'] as const)('reacts to window and player modes on %s', (platform) => {
    const hasCustomTitleBar = useCustomTitleBar()
    deviceStore[platform] = true
    expect(hasCustomTitleBar.value).toBe(true)

    deviceStore.isWindowMaximized = true
    expect(hasCustomTitleBar.value).toBe(true)

    deviceStore.isWindowFullscreen = true
    expect(hasCustomTitleBar.value).toBe(false)

    deviceStore.isWindowFullscreen = false
    expect(hasCustomTitleBar.value).toBe(true)

    for (const mode of ['fullscreen', 'mini'] as const) {
      playerStore.playerMode = mode
      expect(hasCustomTitleBar.value).toBe(false)
      playerStore.playerMode = 'sticky'
      expect(hasCustomTitleBar.value).toBe(true)
    }

    deviceStore[platform] = false
    expect(hasCustomTitleBar.value).toBe(false)
  })
})
