import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

const { getStatus, getTrustedDevices, getSyncStatus, on, revokeDevice, startBroadcast, stopBroadcast } = vi.hoisted(() => ({
  getStatus: vi.fn(),
  getTrustedDevices: vi.fn(),
  getSyncStatus: vi.fn(),
  on: vi.fn((_event: string, _listener: () => void) => vi.fn()),
  revokeDevice: vi.fn(),
  startBroadcast: vi.fn(),
  stopBroadcast: vi.fn(),
}))

vi.mock('../../../bindings/airmedy/internal/infra/wails/mobilepairingservice', () => ({
  GetStatus: getStatus,
  GetTrustedDevices: getTrustedDevices,
  RevokeDevice: revokeDevice,
  StartBroadcast: startBroadcast,
  StopBroadcast: stopBroadcast,
}))
const openURL = vi.fn()
vi.mock('@wailsio/runtime', () => ({ Browser: { OpenURL: openURL }, Events: { On: on } }))
vi.mock('../../../bindings/airmedy/internal/infra/wails/mobilelibrarysyncservice', () => ({ GetStatus: getSyncStatus }))

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
        common: {
          delete: 'Delete',
          mobile_pairing_broadcast: { title: 'Broadcast to pair', description: 'Make this desktop available for 30 seconds.', broadcasting_desc: 'Broadcasting for {seconds} seconds.', broadcasting: 'Broadcasting', start: 'Broadcast', stop: 'Stop' },
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
    openURL.mockReset()
    getStatus.mockResolvedValue({ running: false, addresses: [], broadcasting: false, broadcasting_until: '' })
    getTrustedDevices.mockReset()
    getSyncStatus.mockResolvedValue(null)
    revokeDevice.mockReset()
    startBroadcast.mockReset()
    stopBroadcast.mockReset()
    on.mockClear()
  })

  it('starts and stops the short-lived mDNS broadcast from its status button', async () => {
    getStatus.mockResolvedValue({ running: true, addresses: [], broadcasting: false, broadcasting_until: '' })
    const wrapper = mountSettings()
    await flushPromises()

    expect(wrapper.find('[data-testid="broadcast-button"]').text()).toContain('Broadcast')
    await wrapper.find('[data-testid="broadcast-button"]').trigger('click')
    expect(startBroadcast).toHaveBeenCalledOnce()

    getStatus.mockResolvedValue({ running: true, addresses: [], broadcasting: true, broadcasting_until: new Date(Date.now() + 30_000).toISOString() })
    await wrapper.vm.$nextTick()
    const broadcastChanged = on.mock.calls.find(([event]) => event === 'pairing:broadcast-changed')
    expect(broadcastChanged).toBeDefined()
    await broadcastChanged![1]()
    await flushPromises()
    expect(wrapper.find('[data-testid="broadcast-status"]').text()).toContain('Broadcasting')

    getStatus.mockResolvedValue({ running: true, addresses: [], broadcasting: false, broadcasting_until: '' })
    await wrapper.find('[data-testid="broadcast-button"]').trigger('click')
    expect(stopBroadcast).toHaveBeenCalledOnce()
    await flushPromises()
    wrapper.unmount()
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
    expect(badges[0].attributes('style')).toContain('--badge-color: var(--status-online)')
    expect(badges[1].attributes('style')).toContain('--badge-color: var(--text-muted)')
    expect(on).toHaveBeenCalledWith('pairing:trusted-devices-changed', expect.any(Function))
  })

  it('opens the delete action from a clickable device row or its actions button', async () => {
    getTrustedDevices.mockResolvedValue([{ ...device, online: true }])

    const wrapper = mountSettings()
    await flushPromises()

    await wrapper.find('.trusted-device-row').trigger('contextmenu')
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

  it('disables deleting a device with an active library sync', async () => {
    getTrustedDevices.mockResolvedValue([{ ...device, online: true }])
    getSyncStatus.mockResolvedValue({ device_id: device.device_id, status: 'active' })

    const wrapper = mountSettings()
    await flushPromises()

    expect(wrapper.find('[data-testid="device-actions-button"]').attributes('disabled')).toBeDefined()
    await wrapper.find('.trusted-device-row').trigger('contextmenu')
    const menu = wrapper.findComponent({ name: 'ContextMenu' })
    expect(menu.props('items')).toEqual(expect.arrayContaining([
      expect.objectContaining({ label: 'Delete', disabled: true }),
    ]))
  })

  it('opens the mobile sync FAQ from the pairing guidance', async () => {
    getStatus.mockResolvedValue({ running: true, addresses: [], broadcasting: false, broadcasting_until: '' })
    const wrapper = mountSettings()
    await flushPromises()

    await wrapper.get('[data-testid="mobile-sync-help"]').trigger('click')

    expect(openURL).toHaveBeenCalledWith('https://airmedy.pages.dev/faq/mobile-sync')
  })
})
