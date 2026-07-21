import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import GeneralSettings from './GeneralSettings.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string, values?: Record<string, string>) => values?.color ? `${key}:${values.color}` : key }),
}))

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => () => {}) },
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
  it('renders seven preset circles and a custom-picker trigger', () => {
    const wrapper = mount(GeneralSettings, {
      global: { plugins: [createTestingPinia({ createSpy: vi.fn, initialState: { app: { primaryColor: '#E11D48' } } })] },
    })

    expect(wrapper.findAll('button[aria-pressed]').length).toBe(7)
    expect(wrapper.get('button[aria-label="settings.appearance.custom_primary_color"]')).toBeTruthy()
    expect(wrapper.get('button[aria-pressed="true"]').attributes('aria-label')).toContain('#E11D48')
  })
})
