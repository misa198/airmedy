import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import MarqueeText from './MarqueeText.vue'

describe('MarqueeText', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', class {
      observe() {}
      disconnect() {}
    })
    vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(100)
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('fades overflowing text only when enabled', async () => {
    vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockReturnValue(200)
    const wrapper = mount(MarqueeText, { props: { text: 'Long track title' } })
    await flushPromises()
    expect(wrapper.find('.marquee-anim').exists()).toBe(true)
    expect(wrapper.classes()).not.toContain('marquee-fade')
    expect(wrapper.get('.marquee-anim').attributes('style')).toContain('--scroll-dist: -232px')
    expect(wrapper.get('.marquee-anim').attributes('style')).toContain('--duration: 14.6s')
    expect(wrapper.get('[aria-hidden="true"]').text()).toBe('Long track title')
    expect(wrapper.get('[aria-hidden="true"]').attributes('inert')).toBeDefined()

    await wrapper.setProps({ fade: true })
    await flushPromises()
    expect(wrapper.classes()).toContain('marquee-fade')
    expect(wrapper.get('.marquee-anim').attributes('style')).toContain('--scroll-dist: -232px')

    await wrapper.setProps({ fade: false })
    await flushPromises()
    expect(wrapper.get('.marquee-anim').attributes('style')).toContain('--scroll-dist: -232px')
    wrapper.unmount()
  })

  it.each([120, 800])('reserves three seconds to rest each loop for a %spx title', async (contentWidth) => {
    vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockReturnValue(contentWidth)
    const wrapper = mount(MarqueeText, { props: { text: 'Track title' } })
    await flushPromises()
    const style = (wrapper.get('.marquee-anim').element as HTMLElement).style
    const totalSeconds = Number.parseFloat(style.getPropertyValue('--duration'))
    const pauseFraction = Number.parseFloat(style.getPropertyValue('--pause-end')) / 100
    expect(totalSeconds * pauseFraction).toBeCloseTo(3)
    expect((contentWidth + 32) / (totalSeconds - 3)).toBeCloseTo(20)
    wrapper.unmount()
  })

  it('removes the fade when a new title fits', async () => {
    const width = vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockReturnValue(200)
    const wrapper = mount(MarqueeText, { props: { text: 'Long track title', fade: true } })
    await flushPromises()
    expect(wrapper.classes()).toContain('marquee-fade')

    width.mockReturnValue(80)
    await wrapper.setProps({ text: 'Short title' })
    await flushPromises()
    expect(wrapper.classes()).not.toContain('marquee-fade')
    expect(wrapper.find('.marquee-anim').exists()).toBe(false)
    expect(wrapper.find('[aria-hidden="true"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
