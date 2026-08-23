import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import QueueTrackList from './QueueTrackList.vue'

const mocks = vi.hoisted(() => ({
  scrollToIndex: vi.fn(),
  resize: undefined as ResizeObserverCallback | undefined,
  frame: undefined as FrameRequestCallback | undefined,
}))

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/stores/player', () => ({
  usePlayerStore: () => ({
    currentTrack: { id: 'current' },
    queue: [{ id: 'before' }, { id: 'current' }],
    reorderQueue: vi.fn(),
    playQueueIndex: vi.fn(),
  }),
}))
vi.mock('vue-virtual-sortable', () => ({
  default: defineComponent({
    setup(_, { expose }) {
      expose({ scrollToIndex: mocks.scrollToIndex })
      return () => h('div')
    },
  }),
}))

class ResizeObserverMock {
  constructor(callback: ResizeObserverCallback) { mocks.resize = callback }
  observe() {}
  disconnect() {}
}

describe('QueueTrackList', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    mocks.scrollToIndex.mockReset()
    mocks.resize = undefined
    mocks.frame = undefined
  })

  it('waits for the expanded panel size before revealing the current track', async () => {
    vi.stubGlobal('ResizeObserver', ResizeObserverMock)
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      mocks.frame = callback
      return 1
    })

    const wrapper = mount(QueueTrackList, {
      props: { scrollToCurrentOnMount: true },
      global: { stubs: { TrackContextMenu: true } },
    })
    await nextTick()

    expect(mocks.scrollToIndex).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="queue-track-list"]').classes()).toContain('opacity-0')
    expect(wrapper.get('[data-test="queue-track-list"]').attributes('handle')).toBeUndefined()

    mocks.resize?.([], {} as ResizeObserver)
    mocks.frame?.(0)
    await nextTick()

    expect(mocks.scrollToIndex).toHaveBeenCalledWith(1)
    expect(wrapper.get('[data-test="queue-track-list"]').classes()).not.toContain('opacity-0')
  })
})
