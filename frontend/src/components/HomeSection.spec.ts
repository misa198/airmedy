import { mount } from '@vue/test-utils'
import { Music } from '@lucide/vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import HomeSection from './HomeSection.vue'

class ResizeObserverMock {
  observe = vi.fn()
  disconnect = vi.fn()
}

describe('HomeSection', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverMock)
  })

  it('fills each grid column top-to-bottom', () => {
    const wrapper = mount(HomeSection, {
      props: { title: 'Tracks', icon: Music, id: 'tracks', items: Array.from({ length: 10 }, (_, id) => ({ id })) },
      global: { mocks: { $t: (key: string) => key } },
    })

    const grid = wrapper.find('.grid')
    expect(grid.attributes('style')).toContain('grid-auto-flow: column')
    expect(grid.attributes('style')).toContain('grid-template-rows: repeat(2, minmax(0, auto))')
  })
})
