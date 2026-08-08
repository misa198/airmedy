import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

const { getStatus, getTrustedDevices, on, revokeDevice } = vi.hoisted(() => ({
  getStatus: vi.fn(),
  getTrustedDevices: vi.fn(),
  on: vi.fn(() => vi.fn()),
  revokeDevice: vi.fn(),
}))

vi.mock('../../../bindings/airmedy/internal/infra/wails/mobilepairingservice', () => ({
  GetStatus: getStatus,
  GetTrustedDevices: getTrustedDevices,
  RevokeDevice: revokeDevice,
}))
vi.mock('@wailsio/runtime', () => ({ Events: { On: on } }))

import MobileDevicesSettings from './MobileDevicesSettings.vue'

const device = {
  device_id: 'device-1',
  display_name: 'My phone',
  platform: 'android',
  fingerprint: 'ABCD-1234',
  paired_at: '',
  last_seen_at: '',
}

function mountSettings() {
  const i18n = createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        settings: {
          mobile_pairing: {
            trusted_title: 'Trusted devices',
            no_devices: 'No mobile devices have been paired yet.',
            online: 'Online',
            offline: 'Offline',
          },
        },
      },
    },
  })
  return mount(MobileDevicesSettings, {
    global: {
      plugins: [i18n],
      stubs: {
        SettingSection: { template: '<section><slot /></section>' },
        NetworkAddressList: true,
      },
    },
  })
}

describe('MobileDevicesSettings', () => {
  beforeEach(() => {
    getStatus.mockResolvedValue({ running: false, addresses: [] })
    getTrustedDevices.mockReset()
    revokeDevice.mockReset()
    on.mockClear()
  })

  it('shows the MQTT online state for every trusted device row', async () => {
    getTrustedDevices.mockResolvedValue([{ ...device, online: true }, { ...device, device_id: 'device-2', display_name: 'Old phone', online: false }])

    const wrapper = mountSettings()
    await flushPromises()

    expect(wrapper.text()).toContain('My phone')
    expect(wrapper.text()).toContain('Online')
    expect(wrapper.text()).toContain('Old phone')
    expect(wrapper.text()).toContain('Offline')
    const badges = wrapper.findAll('[data-testid="device-status-badge"]')
    expect(badges[0].attributes('style')).toContain('--badge-color: var(--primary)')
    expect(badges[1].attributes('style')).toContain('--badge-color: var(--text-muted)')
    expect(on).toHaveBeenCalledWith('pairing:trusted-devices-changed', expect.any(Function))
  })

  it('opens the delete action from a clickable device row or its actions button', async () => {
    getTrustedDevices.mockResolvedValue([{ ...device, online: true }])

    const wrapper = mountSettings()
    await flushPromises()

    await wrapper.find('.trusted-device-row').trigger('click')
    let menu = wrapper.findComponent({ name: 'ContextMenu' })
    expect(menu.props('visible')).toBe(true)
    expect(menu.props('items')).toEqual(expect.arrayContaining([
      expect.objectContaining({ label: 'Delete', danger: true }),
    ]))

    await wrapper.find('[data-testid="device-actions-button"]').trigger('click')
    menu = wrapper.findComponent({ name: 'ContextMenu' })
    expect(menu.props('visible')).toBe(true)

    await wrapper.find('.trusted-device-row').trigger('contextmenu')
    expect(menu.props('visible')).toBe(true)
  })
})
