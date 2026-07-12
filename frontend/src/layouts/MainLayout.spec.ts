import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import MainLayout from './MainLayout.vue'
import { usePlayerStore } from '@/stores/player'

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

    expect(usePlayerStore().sidebarWidth).toBe(260)
  })

  it('persists the final clamped width when resizing ends', async () => {
    const wrapper = mountLayout()
    const resizer = wrapper.find('.cursor-col-resize')

    await resizer.trigger('mousedown', { clientX: 240 })
    document.dispatchEvent(new MouseEvent('mousemove', { clientX: 1000 }))
    document.dispatchEvent(new MouseEvent('mouseup'))

    expect(usePlayerStore().sidebarWidth).toBe(250)
    expect(localStorage.getItem('airmedy:sidebar-width')).toBe('250')
  })
})
