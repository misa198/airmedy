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
    const { hasCustomTitleBar, canConfigureTitleBar } = useCustomTitleBar()
    expect(hasCustomTitleBar.value).toBe(false)
    expect(canConfigureTitleBar.value).toBe(false)

    deviceStore.isMac = true
    expect(hasCustomTitleBar.value).toBe(false)
    expect(canConfigureTitleBar.value).toBe(false)
  })

  it.each(['isWindows', 'isLinux'] as const)('reacts to window and player modes on %s', (platform) => {
    const { hasCustomTitleBar, canConfigureTitleBar } = useCustomTitleBar()
    deviceStore[platform] = true
    expect(hasCustomTitleBar.value).toBe(true)
    expect(canConfigureTitleBar.value).toBe(true)

    deviceStore.isWindowMaximized = true
    expect(hasCustomTitleBar.value).toBe(true)
    expect(canConfigureTitleBar.value).toBe(true)

    deviceStore.isWindowFullscreen = true
    expect(hasCustomTitleBar.value).toBe(false)
    expect(canConfigureTitleBar.value).toBe(false)

    deviceStore.isWindowFullscreen = false
    expect(hasCustomTitleBar.value).toBe(true)
    expect(canConfigureTitleBar.value).toBe(true)

    playerStore.playerMode = 'fullscreen'
    expect(hasCustomTitleBar.value).toBe(true)
    expect(canConfigureTitleBar.value).toBe(true)

    playerStore.playerMode = 'mini'
    expect(hasCustomTitleBar.value).toBe(false)
    expect(canConfigureTitleBar.value).toBe(false)

    playerStore.playerMode = 'sticky'
    expect(hasCustomTitleBar.value).toBe(true)
    expect(canConfigureTitleBar.value).toBe(true)

    deviceStore[platform] = false
    expect(hasCustomTitleBar.value).toBe(false)
    expect(canConfigureTitleBar.value).toBe(false)
  })
})
