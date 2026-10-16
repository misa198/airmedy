import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createPinia, setActivePinia } from 'pinia'
import { Events } from '@wailsio/runtime'
import { useAppStore } from '../../stores/app'
import GeneralSettings from './GeneralSettings.vue'

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

describe('GeneralSettings primary color', () => {
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
