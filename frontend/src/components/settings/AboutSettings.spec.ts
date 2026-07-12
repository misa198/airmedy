import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import AboutSettings from './AboutSettings.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@wailsio/runtime', () => ({
  Browser: { OpenURL: vi.fn() },
}))

vi.mock('../../../bindings/airmedy/internal/infra/wails/settingsservice', () => ({
  GetAppInfo: vi.fn().mockResolvedValue({ name: 'Airmedy', version: '1.0.0' }),
  OpenAppDataFolder: vi.fn(),
}))

describe('AboutSettings', () => {
  it('renders the app icon with a dedicated shadow surface instead of a CSS filter', () => {
    const wrapper = mount(AboutSettings, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn })],
      },
    })

    const iconShadow = wrapper.get('.app-icon-shadow')
    expect(iconShadow.classes()).toContain('rounded-[30%]')
    expect(iconShadow.get('img').classes()).not.toContain('drop-shadow-2xl')
  })
})
