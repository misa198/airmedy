import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

const { getStatus, getTrustedDevices, getPairingStatus, sync, on } = vi.hoisted(() => ({
  getStatus: vi.fn(),
  getTrustedDevices: vi.fn(),
  getPairingStatus: vi.fn(),
  sync: vi.fn(),
  on: vi.fn(() => vi.fn()),
}))

vi.mock('@wailsio/runtime', () => ({ Events: { On: on } }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { deviceId: 'device-1' } }),
  useRouter: () => ({ back: vi.fn() }),
}))
vi.mock('../../bindings/airmedy/internal/infra/wails/mobilelibrarysyncservice', () => ({
  GetStatus: getStatus,
  Sync: sync,
}))
vi.mock('../../bindings/airmedy/internal/infra/wails/mobilepairingservice', () => ({
  GetTrustedDevices: getTrustedDevices,
  GetStatus: getPairingStatus,
}))
vi.mock('../../bindings/airmedy/internal/infra/wails/libraryservice', () => ({
  GetAllArtists: vi.fn().mockResolvedValue([]),
  GetAllAlbums: vi.fn().mockResolvedValue([]),
  GetAllGenres: vi.fn().mockResolvedValue([]),
}))
vi.mock('../../bindings/airmedy/internal/infra/wails/playlistservice', () => ({
  GetAllPlaylists: vi.fn().mockResolvedValue([]),
}))
vi.mock('../../bindings/airmedy/internal/domain/models', () => ({
  MobileLibrarySyncScope: class {
    constructor(values: object) { Object.assign(this, values) }
  },
}))

import MobileLibrarySyncView from './MobileLibrarySyncView.vue'

const activePlan = { device_id: 'device-1', status: 'active', completed: 0, total: 4, scope: { kind: 'all', selected_ids: [] } }

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en: {} }, missingWarn: false, fallbackWarn: false })
  return mount(MobileLibrarySyncView, {
    global: {
      plugins: [i18n],
      stubs: {
        Badge: true, Checkbox: true, ConfirmDialog: true, IconButton: true, Input: true,
        Radio: true, RecycleScroller: true, SettingSection: { template: '<section><slot /></section>' }, TabSwitcher: true,
      },
    },
  })
}

describe('MobileLibrarySyncView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    getTrustedDevices.mockResolvedValue([{ device_id: 'device-1', display_name: 'Phone', online: true }])
    getPairingStatus.mockResolvedValue({ addresses: [{ ip: '192.168.1.2', kind: 'wifi' }] })
    getStatus.mockReset()
    sync.mockReset()
    on.mockClear()
  })

  it('refreshes an active plan while receipts update desktop progress', async () => {
    getStatus.mockResolvedValueOnce(activePlan).mockResolvedValueOnce({ ...activePlan, completed: 1 })
    const wrapper = mountView()
    await flushPromises()

    await vi.advanceTimersByTimeAsync(1_000)
    await flushPromises()

    expect(getStatus).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('25%')
    wrapper.unmount()
  })

  it('keeps Sync disabled while its plan is active', async () => {
    getStatus.mockResolvedValue(activePlan)
    const wrapper = mountView()
    await flushPromises()

    const syncButton = wrapper.get('[data-testid="sync-button"]')
    expect(syncButton.attributes('disabled')).toBeDefined()
    await syncButton.trigger('click')
    expect(sync).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('uses the same content width and padding as Settings', async () => {
    getStatus.mockResolvedValue(null)
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('main').classes()).toEqual(expect.arrayContaining(['max-w-3xl', 'p-8']))
    wrapper.unmount()
  })
})
