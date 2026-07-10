import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import PlayerArtwork from './PlayerArtwork.vue'

describe('PlayerArtwork', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('layers both covers with equal-power opacity weights', async () => {
    let frame: FrameRequestCallback | undefined
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      frame = callback
      return 1
    })
    vi.stubGlobal('cancelAnimationFrame', vi.fn())

    const wrapper = mount(PlayerArtwork, {
      props: {
        artworkUrl: '/artwork/to.jpg',
        trackTitle: 'Track',
        isPlaying: true,
        crossfade: {
          transitionId: 7,
          fromUrl: '/artwork/from.jpg',
          toUrl: '/artwork/to.jpg',
          durationMs: 5000,
        },
      },
      global: {
        stubs: { LazyImg: { props: ['src'], template: '<img :src="src" />' } },
      },
    })

    expect(wrapper.findAll('img').map(image => image.attributes('src'))).toEqual([
      '/artwork/from.jpg',
      '/artwork/to.jpg',
    ])
    const startTime = performance.now()
    // First tick: establishes startedAt inside the animation loop.
    frame?.(startTime)
    await nextTick()
    // Second tick: exactly half-duration later → t = 0.5 → equal-power weights.
    frame?.(startTime + 2500)
    await nextTick()
    const images = wrapper.findAll('img')
    expect(images).toHaveLength(2)
    const outgoingStyle = images[0]!.attributes('style') ?? ''
    const incomingStyle = images[1]!.attributes('style') ?? ''
    expect(Number(outgoingStyle.match(/opacity:\s*([\d.]+)/)?.[1])).toBeCloseTo(Math.SQRT1_2)
    expect(Number(incomingStyle.match(/opacity:\s*([\d.]+)/)?.[1])).toBeCloseTo(Math.SQRT1_2)
  })

  it('renders only the current cover outside a crossfade', () => {
    const wrapper = mount(PlayerArtwork, {
      props: { artworkUrl: '/artwork/current.jpg', trackTitle: 'Track', isPlaying: true },
      global: {
        stubs: { LazyImg: { props: ['src'], template: '<img :src="src" />' } },
      },
    })

    expect(wrapper.findAll('img')).toHaveLength(1)
    expect(wrapper.find('.artwork-crossfade-incoming').exists()).toBe(false)
  })
})
