import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

const { getStatus, getTrustedDevices, getPairingStatus, getAllPlaylists, sync, cancel, eventHandlers, on } = vi.hoisted(() => {
  const eventHandlers = new Map<string, (event: { data: unknown }) => void>()
  return {
    getStatus: vi.fn(),
    getTrustedDevices: vi.fn(),
    getPairingStatus: vi.fn(),
    getAllPlaylists: vi.fn(),
    sync: vi.fn(),
    cancel: vi.fn(),
    eventHandlers,
    on: vi.fn((event: string, handler: (event: { data: unknown }) => void) => {
      eventHandlers.set(event, handler)
      return vi.fn()
    }),
  }
})

vi.mock('@wailsio/runtime', () => ({ Events: { On: on } }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { deviceId: 'device-1' } }),
  useRouter: () => ({ back: vi.fn() }),
}))
vi.mock('../../bindings/airmedy/internal/infra/wails/mobilelibrarysyncservice', () => ({
  GetStatus: getStatus,
  Sync: sync,
  Cancel: cancel,
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
  GetAllPlaylists: getAllPlaylists,
}))
vi.mock('../../bindings/airmedy/internal/domain/models', () => ({
  MobileLibrarySyncScope: class {
    constructor(values: object) { Object.assign(this, values) }
  },
}))

import MobileLibrarySyncView from './MobileLibrarySyncView.vue'

const activePlan = { id: 'plan-1', device_id: 'device-1', status: 'active', completed: 0, total: 4, scope: { kind: 'all', selected_ids: [] } }

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en: {
    common: { close: 'Close' },
    mobile_sync: { insufficient_storage_title: 'Not enough storage', insufficient_storage_description: 'Needs {required}; {available} available.' },
  } }, missingWarn: false, fallbackWarn: false })
  return mount(MobileLibrarySyncView, {
    global: {
      plugins: [i18n],
      stubs: {
        Badge: true, Checkbox: true, ConfirmDialog: true, IconButton: true, Input: true,
        Modal: { props: ['open', 'title'], template: '<div v-if="open"><h2>{{ title }}</h2><slot /><slot name="footer" /></div>' },
        Radio: true, RecycleScroller: { props: ['items'], template: '<div><slot v-for="(item, index) in items" :item="item" :index="index" /></div>' }, SettingSection: { template: '<section><slot /></section>' }, TabSwitcher: true,
      },
    },
  })
}

describe('MobileLibrarySyncView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    getTrustedDevices.mockResolvedValue([{ device_id: 'device-1', display_name: 'Phone', online: true }])
    getPairingStatus.mockResolvedValue({ addresses: [{ ip: '192.168.1.2', kind: 'wifi' }] })
    getAllPlaylists.mockResolvedValue([])
    getStatus.mockReset()
    sync.mockReset()
    cancel.mockReset()
    eventHandlers.clear()
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

  it('does not let a stale poll overwrite a completed event', async () => {
    let resolvePoll!: (plan: typeof activePlan) => void
    getStatus.mockResolvedValueOnce(activePlan).mockReturnValueOnce(new Promise(resolve => { resolvePoll = resolve }))
    const wrapper = mountView()
    await flushPromises()

    vi.advanceTimersByTime(1_000)
    await flushPromises()
    eventHandlers.get('mobile-library-sync:updated')!({ data: { ...activePlan, status: 'complete', completed: 4 } })
    resolvePoll(activePlan)
    await flushPromises()

    expect(wrapper.get('[data-testid="sync-icon"]').classes()).not.toContain('animate-spin')
    expect(wrapper.find('[data-testid="cancel-sync-button"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('does not let a stale poll move active progress backwards', async () => {
    let resolvePoll!: (plan: typeof activePlan) => void
    getStatus.mockResolvedValueOnce(activePlan).mockReturnValueOnce(new Promise(resolve => { resolvePoll = resolve }))
    const wrapper = mountView()
    await flushPromises()

    vi.advanceTimersByTime(1_000)
    await flushPromises()
    eventHandlers.get('mobile-library-sync:updated')!({ data: { ...activePlan, completed: 3 } })
    resolvePoll({ ...activePlan, completed: 2 })
    await flushPromises()

    expect(wrapper.text()).toContain('75%')
    wrapper.unmount()
  })

  it('keeps Sync disabled while its plan is active', async () => {
    getStatus.mockResolvedValue(activePlan)
    const wrapper = mountView()
    await flushPromises()

    const syncButton = wrapper.get('[data-testid="sync-button"]')
    expect(syncButton.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="sync-icon"]').classes()).toContain('animate-spin')
    expect(wrapper.get('[data-testid="scope-all"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="scope-selected"]').attributes('disabled')).toBeDefined()
    await syncButton.trigger('click')
    expect(sync).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('cancels the active plan from desktop', async () => {
    getStatus.mockResolvedValue(activePlan)
    cancel.mockResolvedValue({ ...activePlan, status: 'superseded' })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="cancel-sync-button"]').trigger('click')
    await flushPromises()

    expect(cancel).toHaveBeenCalledWith('device-1')
    expect(wrapper.find('[data-testid="cancel-sync-button"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('locks the selected-items table while its plan is active', async () => {
    getStatus.mockResolvedValue({ ...activePlan, scope: { kind: 'artists', selected_ids: [] } })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="sync-selection-overlay"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="sync-selection-spinner"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('uses the same content width and padding as Settings', async () => {
    getStatus.mockResolvedValue(null)
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('main').classes()).toEqual(expect.arrayContaining(['max-w-3xl', 'p-8']))
    wrapper.unmount()
  })

  it('opens a storage error from GetStatus, hides progress, and allows Sync after close', async () => {
    getStatus.mockResolvedValue({
      ...activePlan, status: 'superseded', error_code: 'insufficient_storage',
      required_bytes: 2_000_000_000, available_bytes: 1_000_000_000,
    })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="storage-error-message"]').text()).toContain('1,907.3 MB')
    expect(wrapper.get('[data-testid="storage-error-message"]').text()).toContain('953.7 MB')
    expect(wrapper.text()).not.toContain('0%')
    expect(wrapper.get('[data-testid="sync-button"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="storage-error-close"]').trigger('click')
    expect(wrapper.find('[data-testid="storage-error-message"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('opens a storage error from the realtime event and stops polling', async () => {
    getStatus.mockResolvedValue(activePlan)
    const wrapper = mountView()
    await flushPromises()

    eventHandlers.get('mobile-library-sync:updated')!({ data: {
      ...activePlan, status: 'superseded', error_code: 'insufficient_storage', required_bytes: 12, available_bytes: 0,
    } })
    await vi.advanceTimersByTimeAsync(1_000)

    expect(wrapper.find('[data-testid="storage-error-message"]').exists()).toBe(true)
    expect(getStatus).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('only shows regular playlists in the selector', async () => {
    getStatus.mockResolvedValue({ ...activePlan, scope: { kind: 'playlists', selected_ids: ['regular'] } })
    getAllPlaylists.mockResolvedValue([
      { id: 'favorites', name: 'Favorites', is_smart: false },
      { id: 'smart', name: 'Smart', is_smart: true },
      { id: 'regular', name: 'Regular', is_smart: false },
    ])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Regular')
    expect(wrapper.text()).not.toContain('Favorites')
    expect(wrapper.text()).not.toContain('Smart')
    wrapper.unmount()
  })
})
