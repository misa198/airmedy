import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import QueueDrawer from './QueueDrawer.vue'
import { usePlayerStore } from '../stores/player'

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(), Off: vi.fn() },
  Create: {
    Nullable: (fn: any) => (v: any) => (v == null ? null : fn(v)),
    Array: (fn: any) => (arr: any[]) => (arr ?? []).map(fn),
    Struct: (ctor: any) => (v: any) => (v == null ? null : new ctor(v)),
  },
  Call: { ByID: vi.fn().mockResolvedValue(null) },
}))

vi.mock('../../bindings/changeme/internal/infra/wails/playerservice', () => ({
  GetStatus: vi.fn().mockResolvedValue(null),
  GetQueue: vi.fn().mockResolvedValue([]),
  Play: vi.fn(),
  Pause: vi.fn(),
  Stop: vi.fn(),
  Next: vi.fn(),
  Previous: vi.fn(),
  Seek: vi.fn(),
  SetVolume: vi.fn(),
  SetMuted: vi.fn(),
  SetShuffle: vi.fn(),
  SetRepeatMode: vi.fn(),
  PlayTracks: vi.fn(),
}))

describe('QueueDrawer', () => {
  beforeEach(() => { vi.clearAllMocks() })

  function mountDrawer(isQueueOpen: boolean, queue: any[] = []) {
    return mount(QueueDrawer, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: { player: { isQueueOpen, queue, currentTrack: null } },
          }),
        ],
        stubs: { RecycleScroller: { template: '<div><slot v-for="(item, index) in items" :item="item" :index="index" /></div>', props: ['items', 'itemSize', 'keyField'] }, Transition: false },
      },
    })
  }

  it('is not rendered when isQueueOpen is false', () => {
    const wrapper = mountDrawer(false)
    expect(wrapper.find('[class*="fixed"]').exists()).toBe(false)
  })

  it('is rendered when isQueueOpen is true', () => {
    const wrapper = mountDrawer(true)
    expect(wrapper.find('[class*="fixed"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Queue')
  })

  it('shows empty state when queue is empty', () => {
    const wrapper = mountDrawer(true, [])
    expect(wrapper.text()).toContain('Queue is empty')
  })

  it('calls toggleQueue when close button clicked', async () => {
    const wrapper = mountDrawer(true)
    const store = usePlayerStore()
    const closeBtn = wrapper.find('button')
    await closeBtn.trigger('click')
    expect(store.toggleQueue).toHaveBeenCalledOnce()
  })
})
