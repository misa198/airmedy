import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import QueueTrackList from './QueueTrackList.vue'
import { usePlayerStore } from '../stores/player'

const mocks = vi.hoisted(() => ({ scrollToIndex: vi.fn() }))

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(), Off: vi.fn() },
  Create: {
    Nullable: (fn: any) => (value: any) => value == null ? null : fn(value),
    Array: (fn: any) => (value: any[]) => (value ?? []).map(fn),
    Struct: (ctor: any) => (value: any) => value == null ? null : new ctor(value),
    Map: () => (value: any) => value,
  },
  Call: { ByID: vi.fn().mockResolvedValue(null) },
}))

describe('QueueTrackList', () => {
  it('plays rows and forwards drag reorder to the player store', async () => {
    const queue = [
      { id: 'one', title: 'One', duration: 60, artists: [] },
      { id: 'two', title: 'Two', duration: 120, artists: [] },
    ]
    const wrapper = mount(QueueTrackList, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn, initialState: { player: { queue } } })],
        stubs: {
          VirtualList: {
            props: ['modelValue'],
            methods: { scrollToIndex: mocks.scrollToIndex },
            template: '<div><button data-test="reorder" @click="$emit(\'update:modelValue\', [...modelValue].reverse())" /><slot name="item" v-for="(item, index) in modelValue" :record="item" :index="index" /></div>',
          },
          LazyImg: true,
          TrackContextMenu: true,
        },
      },
    })
    const store = usePlayerStore()

    await wrapper.get('.group').trigger('click')
    expect(store.playQueueIndex).toHaveBeenCalledWith(0)

    await wrapper.get('[data-test="reorder"]').trigger('click')
    expect(store.reorderQueue).toHaveBeenCalledWith([queue[1], queue[0]])
  })

  it('scrolls to the current track when opened', async () => {
    vi.useFakeTimers()
    const queue = [{ id: 'one', title: 'One', duration: 60, artists: [] }, { id: 'two', title: 'Two', duration: 120, artists: [] }]
    const wrapper = mount(QueueTrackList, {
      props: { scrollToCurrentOnMount: true },
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn, initialState: { player: { queue, currentTrack: queue[1] } } })],
        stubs: {
          VirtualList: { methods: { scrollToIndex: mocks.scrollToIndex }, template: '<div />' },
          TrackContextMenu: true,
        },
      },
    })

    expect(wrapper.find('.opacity-0').exists()).toBe(true)
    await vi.advanceTimersByTimeAsync(16)
    expect(mocks.scrollToIndex).toHaveBeenCalledWith(1)
    expect(wrapper.find('.opacity-0').exists()).toBe(false)
    vi.useRealTimers()
  })
})
