import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createTestingPinia } from '@pinia/testing'
import { Events } from '@wailsio/runtime'
import { Switch } from '@airmedy/ui'
import { useAppStore } from '@/stores/app'
import PlaybackSettings from './PlaybackSettings.vue'

const api = vi.hoisted(() => ({ GetSettings: vi.fn(), SaveSettings: vi.fn(), isMac: false }))

vi.mock('../../../bindings/airmedy/internal/infra/wails/settingsservice', () => api)
vi.mock('@/stores/device', () => ({ useDeviceStore: () => ({ isMac: api.isMac }) }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => vi.fn()) },
  Create: {
    Nullable: (create: (value: unknown) => unknown) => (value: unknown) => value == null ? null : create(value),
    Array: (create: (value: unknown) => unknown) => (values: unknown[]) => (values ?? []).map(create),
    Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value),
    Map: () => (value: unknown) => value,
    Any: (value: unknown) => value,
  },
  Call: { ByID: vi.fn().mockResolvedValue(null) },
}))
vi.mock('../../../bindings/airmedy/internal/infra/wails/windowservice', () => ({
  SetTitleBarTheme: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('../../../bindings/airmedy/internal/infra/wails/analysisservice', () => ({
  GetWorkerCountInfo: vi.fn().mockResolvedValue({ count: 2, max: 4 }),
}))

describe('player favorite visibility setting', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    setActivePinia(createPinia())
    api.isMac = false
    api.SaveSettings.mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.documentElement.classList.remove('dark', 'black')
  })

  it('defaults to hidden and saves both visibility choices', async () => {
    const store = useAppStore()
    expect(store.showPlayerFavoriteButton).toBe(false)
    for (const enabled of [true, false]) {
      await store.updateShowPlayerFavoriteButton(enabled)
      expect(store.showPlayerFavoriteButton).toBe(enabled)
      expect(api.SaveSettings).toHaveBeenLastCalledWith(expect.objectContaining({ show_player_favorite_button: enabled }))
    }
    store.dispose()
  })

  it.each([undefined, false, true])('loads saved visibility %s', async (enabled) => {
    api.GetSettings.mockResolvedValue({ theme: 'dark', show_player_favorite_button: enabled })
    const store = useAppStore()
    await store.loadSettings(true)
    expect(store.showPlayerFavoriteButton).toBe(enabled === true)
    store.dispose()
  })

  it('receives changes from another window and cleans up its subscription', async () => {
    const store = useAppStore()
    const index = vi.mocked(Events.On).mock.calls.findLastIndex(([name]) => name === 'settings:player-favorite-button')
    const handler = vi.mocked(Events.On).mock.calls[index][1]
    const off = vi.mocked(Events.On).mock.results[index].value
    handler({ data: true } as Events.WailsEvent)
    expect(store.showPlayerFavoriteButton).toBe(true)
    await store.updateShowPlayerIndicator(false)
    expect(api.SaveSettings).toHaveBeenLastCalledWith(expect.objectContaining({ show_player_favorite_button: true }))
    handler({ data: false } as Events.WailsEvent)
    expect(store.showPlayerFavoriteButton).toBe(false)
    store.dispose()
    expect(off).toHaveBeenCalledOnce()
  })

  it.each([false, true])('shows independent background controls, macOS: %s', async (isMac) => {
    api.isMac = isMac
    const wrapper = mount(PlaybackSettings, { global: { plugins: [createTestingPinia({ createSpy: vi.fn })], stubs: { EQPanel: true } } })
    const store = useAppStore()
    expect(wrapper.find('[data-test="tray-background"]').exists()).toBe(false)
    for (const [name, action] of [['fullscreen', store.updateLivingArtworkBackground], ['mini', store.updateMiniLivingArtworkBackground]] as const) {
      const select = wrapper.get(`[data-test="${name}-background"]`).findComponent({ name: 'SelectRoot' })
      select.vm.$emit('update:modelValue', 'living')
      expect(action).toHaveBeenCalledWith(true)
    }
    wrapper.unmount()
  })

  it('saves each background independently', async () => {
    const store = useAppStore()
    expect(store.miniLivingArtworkBackground).toBe(false)
    expect(store.trayBackgroundMode).toBe('solid')
    await store.updateMiniLivingArtworkBackground(true)
    await store.updateTrayBackgroundMode('solid')
    expect(api.SaveSettings).toHaveBeenLastCalledWith(expect.objectContaining({ living_artwork_background: true, mini_living_artwork_background: true, tray_background_mode: 'solid' }))
    store.dispose()
  })

  it.each(['living', 'solid', 'monochrome'] as const)('loads and saves tray background %s', async (mode) => {
    api.GetSettings.mockResolvedValue({ theme: 'dark', tray_background_mode: mode })
    const store = useAppStore()
    await store.loadSettings(true)
    expect(store.trayBackgroundMode).toBe(mode)
    await store.updateTrayBackgroundMode(mode)
    expect(api.SaveSettings).toHaveBeenLastCalledWith(expect.objectContaining({ tray_background_mode: mode }))
    store.dispose()
  })

  it('syncs the tray background and app theme and removes both listeners', async () => {
    const store = useAppStore()
    const listeners = ['settings:tray-background', 'settings:theme'].map(name => {
      const index = vi.mocked(Events.On).mock.calls.findLastIndex(([event]) => event === name)
      return { handler: vi.mocked(Events.On).mock.calls[index][1], off: vi.mocked(Events.On).mock.results[index].value }
    })
    listeners[0].handler({ data: 'monochrome' } as Events.WailsEvent)
    expect(store.trayBackgroundMode).toBe('monochrome')
    listeners[0].handler({ data: 'invalid' } as Events.WailsEvent)
    expect(store.trayBackgroundMode).toBe('monochrome')
    for (const value of ['light', 'dark', 'black', 'system']) {
      listeners[1].handler({ data: value } as Events.WailsEvent)
      expect(store.theme).toBe(value)
      expect(document.documentElement.classList.contains('black')).toBe(value === 'black')
      expect(document.documentElement.classList.contains('dark')).toBe(value === 'black' || value === 'dark')
    }
    expect(api.SaveSettings).not.toHaveBeenCalled()
    store.dispose()
    listeners.forEach(({ off }) => expect(off).toHaveBeenCalledOnce())
  })

  it('wires the playback switch to the visibility setting', async () => {
    const wrapper = mount(PlaybackSettings, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn })],
        stubs: { EQPanel: true },
      },
    })
    const row = wrapper.get('[data-test="show-player-favorite-button"]')
    expect(row.text()).toContain('settings.playback.show_player_favorite_button')
    const toggle = row.getComponent(Switch)
    expect(toggle.props('modelValue')).toBe(false)
    toggle.vm.$emit('update:modelValue', true)
    expect(useAppStore().updateShowPlayerFavoriteButton).toHaveBeenCalledWith(true)
    wrapper.unmount()
  })
})
