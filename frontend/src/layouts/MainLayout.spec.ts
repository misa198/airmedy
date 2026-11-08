import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import MainLayout from './MainLayout.vue'
import { usePlayerStore } from '@/stores/player'
import { useDeviceStore } from '@/stores/device'
import WindowTitleBar from '@/components/WindowTitleBar.vue'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRouter: () => ({ afterEach: () => () => {} }),
}))

vi.mock('@/components/FullScreenPlayer.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/MiniPlayer.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/PlayerFooter.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/LyricsDrawer.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/QueueDrawer.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/TrackInfoDrawer.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/UpdateDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/Sidebar.vue', () => ({ default: { template: '<div />' } }))

function mountLayout() {
  return mount(MainLayout, {
    global: {
      plugins: [createTestingPinia({ createSpy: vi.fn })],
      stubs: { RouterView: true },
    },
  })
}

describe('MainLayout sidebar width persistence', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('restores a valid saved width on mount', () => {
    localStorage.setItem('airmedy:sidebar-width', '240')
    mountLayout()

    expect(usePlayerStore().sidebarWidth).toBe(240)
  })

  it('uses the default width when the saved width is invalid', () => {
    localStorage.setItem('airmedy:sidebar-width', '300')
    mountLayout()

    expect(usePlayerStore().sidebarWidth).toBe(250)
  })

  it('persists the final clamped width when resizing ends', async () => {
    const wrapper = mountLayout()
    const resizer = wrapper.find('.cursor-col-resize')

    await resizer.trigger('mousedown', { clientX: 240 })
    document.dispatchEvent(new MouseEvent('mousemove', { clientX: 1000 }))
    document.dispatchEvent(new MouseEvent('mouseup'))

    expect(usePlayerStore().sidebarWidth).toBe(260)
    expect(localStorage.getItem('airmedy:sidebar-width')).toBe('260')
  })
})

describe('MainLayout title bar', () => {
  it.each(['isWindows', 'isLinux'] as const)('uses the custom bar on %s without maximizing from content clicks', async (platform) => {
    const wrapper = mountLayout()
    const deviceStore = useDeviceStore()
    deviceStore[platform] = true
    usePlayerStore().playerMode = 'sticky'
    await wrapper.vm.$nextTick()

    expect(wrapper.findComponent(WindowTitleBar).exists()).toBe(true)
    expect(wrapper.find('.pointer-events-none').exists()).toBe(false)

    await wrapper.findComponent(WindowTitleBar).trigger('dblclick')
    expect(deviceStore.toggleMaximize).toHaveBeenCalledOnce()

    await wrapper.find('main').trigger('dblclick', { clientY: 35 })
    expect(deviceStore.toggleMaximize).toHaveBeenCalledOnce()

    deviceStore.isWindowFullscreen = true
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent(WindowTitleBar).exists()).toBe(false)
    wrapper.unmount()
  })

  it('keeps the existing macOS drag overlay and double-click behavior', async () => {
    const wrapper = mountLayout()
    const deviceStore = useDeviceStore()
    deviceStore.isMac = true
    usePlayerStore().playerMode = 'sticky'
    await wrapper.vm.$nextTick()

    expect(wrapper.findComponent(WindowTitleBar).exists()).toBe(false)
    expect(wrapper.find('.pointer-events-none').exists()).toBe(true)

    await wrapper.trigger('dblclick', { clientY: 20 })
    expect(deviceStore.toggleMaximize).toHaveBeenCalledOnce()
    await wrapper.trigger('dblclick', { clientY: 50 })
    expect(deviceStore.toggleMaximize).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
})
