import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { createTestingPinia } from '@pinia/testing'
import { createPinia, setActivePinia } from 'pinia'
import { Events } from '@wailsio/runtime'
import { useAppStore } from '../../stores/app'
import GeneralSettings from './GeneralSettings.vue'

vi.mock('../../../bindings/airmedy/internal/infra/wails/windowservice', () => ({ SetTitleBarTheme: vi.fn().mockResolvedValue(undefined) }))

vi.mock('@/composables/useCustomTitleBar', () => ({ useCustomTitleBar: () => ({ canConfigureTitleBar: titleBarAvailable }) }))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string, values?: Record<string, string>) => values?.color ? `${key}:${values.color}` : key }),
}))

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => vi.fn()) },
  Create: {
    Nullable: (create: (value: unknown) => unknown) => (value: unknown) => value == null ? null : create(value),
    Array: (create: (value: unknown) => unknown) => (values: unknown[]) => (values ?? []).map(create),
    Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value),
    Map: () => (value: unknown) => value,
    Any: (value: unknown) => value,
  },
  Call: { ByID: vi.fn() },
}))

const titleBarAvailable = ref(true)

describe('GeneralSettings primary color', () => {
  it.each([true, false])('follows title bar configuration availability (%s)', async (available) => {
    titleBarAvailable.value = available
    const pinia = createTestingPinia({ createSpy: vi.fn })
    const wrapper = mount(GeneralSettings, { global: { plugins: [pinia], stubs: { TitleBarSettings: { template: '<div data-test="title-bar-settings" />' } } } })
    expect(wrapper.find('[data-test="title-bar-settings"]').exists()).toBe(available)
    titleBarAvailable.value = !available
    await nextTick()
    expect(wrapper.find('[data-test="title-bar-settings"]').exists()).toBe(!available)
    wrapper.unmount()
    titleBarAvailable.value = true
  })

  it('shows the tray style only when macOS tray is enabled', async () => {
    const pinia = createTestingPinia({ createSpy: vi.fn, initialState: { app: { showTrayIcon: true, trayMode: 'menu' }, device: { isMac: true } } })
    const wrapper = mount(GeneralSettings, { global: { plugins: [pinia] } })
    expect(wrapper.text().indexOf('settings.behavior.tray_section')).toBeLessThan(wrapper.text().indexOf('settings.sidebar.title'))
    expect(wrapper.text()).toContain('settings.behavior.tray_mode')
    expect(wrapper.text()).toContain('settings.behavior.tray_theme')
    expect(wrapper.text()).toContain('settings.behavior.tray_panel_theme')
    useAppStore().showTrayIcon = false
    await nextTick()
    expect(wrapper.text()).not.toContain('settings.behavior.tray_mode')
    expect(wrapper.text()).not.toContain('settings.behavior.tray_theme')
    expect(wrapper.text()).not.toContain('settings.behavior.tray_panel_theme')
  })

  it('syncs tray window theme independently and removes the listener', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    setActivePinia(createPinia())
    const store = useAppStore()
    const index = vi.mocked(Events.On).mock.calls.findLastIndex(([name]) => name === 'settings:tray-panel-theme')
    const handler = vi.mocked(Events.On).mock.calls[index][1]
    const off = vi.mocked(Events.On).mock.results[index].value
    handler({ data: 'black' } as Events.WailsEvent)
    expect(store.trayPanelTheme).toBe('black')
    expect(store.trayTheme).toBe('system')
    handler({ data: 'light' } as Events.WailsEvent)
    expect(store.trayPanelTheme).toBe('light')
    handler({ data: 'system' } as Events.WailsEvent)
    expect(store.trayPanelTheme).toBe('system')
    store.dispose()
    expect(off).toHaveBeenCalledOnce()
    vi.unstubAllGlobals()
  })
  it('hides the unused tray background setting for the empty native panel', () => {
    const pinia = createTestingPinia({ createSpy: vi.fn, initialState: { app: { showTrayIcon: true }, device: { isMac: true } } })
    const wrapper = mount(GeneralSettings, { global: { plugins: [pinia] } })
    expect(wrapper.find('[data-test="tray-background"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('syncs the menu bar pill theme without changing the app theme', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    setActivePinia(createPinia())
    const store = useAppStore()
    const index = vi.mocked(Events.On).mock.calls.findLastIndex(([name]) => name === 'settings:tray-theme')
    const handler = vi.mocked(Events.On).mock.calls[index][1]
    const off = vi.mocked(Events.On).mock.results[index].value
    document.documentElement.classList.add('dark')
    handler({ data: 'light' } as Events.WailsEvent)
    expect(store.trayTheme).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    document.documentElement.classList.remove('dark')
    handler({ data: 'dark' } as Events.WailsEvent)
    expect(store.trayTheme).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    handler({ data: 'black' } as Events.WailsEvent)
    expect(store.trayTheme).toBe('black')
    handler({ data: 'system' } as Events.WailsEvent)
    expect(store.trayTheme).toBe('system')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    store.dispose()
    expect(off).toHaveBeenCalledOnce()
    document.documentElement.classList.remove('dark')
    vi.unstubAllGlobals()
  })

  it('receives language changes from another desktop window and removes its listener', () => {
    setActivePinia(createPinia())
    const store = useAppStore()
    const index = vi.mocked(Events.On).mock.calls.findLastIndex(([name]) => name === 'language:changed')
    const handler = vi.mocked(Events.On).mock.calls[index][1]
    const off = vi.mocked(Events.On).mock.results[index].value
    handler({ data: 'ja' } as Events.WailsEvent)
    expect(store.language).toBe('ja')
    store.dispose()
    expect(off).toHaveBeenCalledOnce()
  })

  it('shows the language setting before the theme and primary color settings', () => {
    const wrapper = mount(GeneralSettings, {
      global: { plugins: [createTestingPinia({ createSpy: vi.fn, initialState: { app: { primaryColor: '#E11D48' } } })] },
    })

    const settings = wrapper.findAll('[data-testid]')
    expect(settings.map(setting => setting.attributes('data-testid'))).toEqual([
      'language-setting',
      'theme-setting',
      'primary-color-setting',
      'primary-color-presets',
    ])
  })

  it('renders seven preset circles and a custom-picker trigger', () => {
    const wrapper = mount(GeneralSettings, {
      global: { plugins: [createTestingPinia({ createSpy: vi.fn, initialState: { app: { primaryColor: '#E11D48' } } })] },
    })

    expect(wrapper.findAll('button[aria-pressed]').length).toBe(7)
    expect(wrapper.get('button[aria-label="settings.appearance.custom_primary_color"]')).toBeTruthy()
    expect(wrapper.get('button[aria-pressed="true"]').attributes('aria-label')).toContain('#E11D48')
  })

  it('allows the preset controls to wrap within a narrow settings section', () => {
    const wrapper = mount(GeneralSettings, {
      global: { plugins: [createTestingPinia({ createSpy: vi.fn, initialState: { app: { primaryColor: '#E11D48' } } })] },
    })

    const presets = wrapper.get('[data-testid="primary-color-presets"]')
    expect(presets.classes()).toEqual(expect.arrayContaining(['w-1/2', 'max-w-[18rem]', 'flex-wrap']))
    expect(wrapper.get('[data-testid="primary-color-setting"] p').classes()).toContain('truncate')
  })
})
