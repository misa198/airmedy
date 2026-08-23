import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import MiniPlayerLyrics from './MiniPlayerLyrics.vue'

const originalScrollTo = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollTo')
const originalClientHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientHeight')
const originalClientWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientWidth')

describe('MiniPlayerLyrics', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    if (originalScrollTo) Object.defineProperty(HTMLElement.prototype, 'scrollTo', originalScrollTo)
    else delete (HTMLElement.prototype as { scrollTo?: HTMLElement['scrollTo'] }).scrollTo
    if (originalClientHeight) Object.defineProperty(HTMLElement.prototype, 'clientHeight', originalClientHeight)
    else delete (HTMLElement.prototype as { clientHeight?: number }).clientHeight
    if (originalClientWidth) Object.defineProperty(HTMLElement.prototype, 'clientWidth', originalClientWidth)
    else delete (HTMLElement.prototype as { clientWidth?: number }).clientWidth
  })

  it('positions the measured active line one quarter down its own panel and seeks on click', async () => {
    const scrollTo = vi.fn()
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      callback(0)
      return 1
    })
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    Object.defineProperty(HTMLElement.prototype, 'scrollTo', { configurable: true, value: scrollTo })
    Object.defineProperty(HTMLElement.prototype, 'clientHeight', { configurable: true, get: () => 300 })
    Object.defineProperty(HTMLElement.prototype, 'clientWidth', { configurable: true, get: () => 300 })

    const wrapper = mount(MiniPlayerLyrics, {
      props: { lyrics: '[00:00.00]First\n[00:10.00]Active\n[00:20.00]Next\n[00:30.00]Later', currentPosition: 0 },
      global: { mocks: { $t: (key: string) => key } },
    })
    await nextTick()

    scrollTo.mockClear()
    const container = wrapper.get('[data-test="mini-synced-lyrics"]')
    vi.spyOn(container.element, 'getBoundingClientRect').mockReturnValue({ top: 300 } as DOMRect)
    const previous = wrapper.findAll('[data-test="mini-lyric-line"]')[0]
    vi.spyOn(previous.element, 'getBoundingClientRect').mockReturnValue({ top: 700 } as DOMRect)
    const active = wrapper.findAll('[data-test="mini-lyric-line"]')[1]
    vi.spyOn(active.element, 'getBoundingClientRect').mockReturnValue({ top: 480 } as DOMRect)
    Object.defineProperty(active.element, 'clientHeight', { configurable: true, value: 40 })
    await wrapper.setProps({ currentPosition: 10 })
    await nextTick()
    await nextTick()

    expect(scrollTo).toHaveBeenLastCalledWith({ top: 140, behavior: 'smooth' })
    expect(active.classes()).toContain('text-foreground')
    expect(active.classes()).toContain('scale-105')
    expect(active.classes()).toContain('transform-gpu')
    expect(container.classes()).toContain('pr-10')
    expect(container.classes()).toContain('py-24')
    expect(wrapper.findAll('[data-test="mini-lyric-line"]')[0].classes()).toContain('opacity-60')
    expect(wrapper.findAll('[data-test="mini-lyric-line"]')[2].classes()).toContain('opacity-50')
    expect(wrapper.findAll('[data-test="mini-lyric-line"]')[2].classes()).toContain('transform-gpu')
    expect(wrapper.findAll('[data-test="mini-lyric-line"]')[3].classes()).not.toContain('transform-gpu')

    await active.trigger('click')
    expect(wrapper.emitted('seek')).toEqual([[10]])
  })

  it('renders plaintext, loading, and empty lyrics states', () => {
    const global = { mocks: { $t: (key: string) => key } }
    expect(mount(MiniPlayerLyrics, {
      props: { lyrics: 'First line\nSecond line', currentPosition: 0 }, global,
    }).findAll('[data-test="mini-plain-lyric-line"]')).toHaveLength(2)
    expect(mount(MiniPlayerLyrics, {
      props: { lyrics: '[00:00.00]Loading', loading: true, currentPosition: 0 }, global,
    }).find('[data-test="mini-lyrics-loading"]').exists()).toBe(true)
    expect(mount(MiniPlayerLyrics, {
      props: { currentPosition: 0 }, global,
    }).find('[data-test="mini-lyrics-empty"]').exists()).toBe(true)
  })

  it('seeks a lyric tap without entering browse mode first', async () => {
    const wrapper = mount(MiniPlayerLyrics, {
      props: { lyrics: '[00:00.00]First\n[00:10.00]Active', currentPosition: 10 },
      global: { mocks: { $t: (key: string) => key } },
    })
    const firstLine = wrapper.findAll('[data-test="mini-lyric-line"]')[0]

    await firstLine.trigger('pointerdown')
    expect(firstLine.classes()).toContain('opacity-60')
    await firstLine.trigger('click')

    expect(wrapper.emitted('seek')).toEqual([[0]])
  })
})
