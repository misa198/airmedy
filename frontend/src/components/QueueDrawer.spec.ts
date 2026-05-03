import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import QueueDrawer from './QueueDrawer.vue'
import { usePlayerStore } from '../stores/player'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => {
      if (key === 'player.queue') return 'Queue'
      if (key === 'player.queue_empty') return 'Queue is empty'
      return key
    },
  }),
}))

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(), Off: vi.fn() },
  Create: {
    Nullable: (fn: any) => (v: any) => (v == null ? null : fn(v)),
    Array: (fn: any) => (arr: any[]) => (arr ?? []).map(fn),
    Struct: (ctor: any) => (v: any) => (v == null ? null : new ctor(v)),
    Map: (k: any, v: any) => (val: any) => val,
  },
  Call: { ByID: vi.fn().mockResolvedValue(null) },
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
        stubs: { 
          RecycleScroller: { 
            template: '<div><div v-for="(item, index) in items" :key="item.id"><slot :item="item" :index="index" /></div></div>', 
            props: ['items', 'itemSize', 'keyField'] 
          }, 
          Transition: false,
          LazyImg: true,
          TrackContextMenu: true
        },
      },
    })
  }

  it('is not rendered when isQueueOpen is false', () => {
    const wrapper = mountDrawer(false)
    expect(wrapper.find('[class*="fixed"]').exists()).toBe(false)
  })

  it('is rendered when isQueueOpen is true', () => {
    const wrapper = mountDrawer(true)
    expect(wrapper.exists()).toBe(true)
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
