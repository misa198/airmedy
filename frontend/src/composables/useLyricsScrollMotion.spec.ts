import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { fullscreenLyricsMotionDuration, useLyricsScrollMotion } from './useLyricsScrollMotion'

describe('lyrics end-of-list scrolling', () => {
  afterEach(() => { vi.unstubAllGlobals() })

  it.each([0.25, 0.32, 0.5].flatMap(anchor => [230, fullscreenLyricsMotionDuration].map(duration => ({ anchor, duration }))))('reaches the last line at $anchor over $duration ms, also after resize', ({ anchor, duration }) => {
    const frames = new Map<number, FrameRequestCallback>()
    let id = 0
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      frames.set(++id, callback)
      return id
    })
    vi.stubGlobal('cancelAnimationFrame', (frame: number) => frames.delete(frame))
    const tick = (time: number) => {
      const callbacks = [...frames.values()]
      frames.clear()
      callbacks.forEach(callback => callback(time))
    }
    let motion!: ReturnType<typeof useLyricsScrollMotion>
    const wrapper = mount(defineComponent({ setup() {
      motion = useLyricsScrollMotion(duration)
      return () => h('div', [h('div')])
    } }))
    const container = wrapper.element as HTMLElement
    const list = container.firstElementChild as HTMLElement
    let height = 600
    let position = 1200
    // Model the browser's scroll clamping, which jsdom does not implement.
    Object.defineProperties(container, {
      clientHeight: { get: () => height },
      scrollHeight: { get: () => 2000 + (parseFloat(list.style.paddingBottom) || 0) },
      scrollTop: {
        get: () => position,
        set: (value: number) => { position = Math.max(0, Math.min(value, container.scrollHeight - height)) },
      },
    })
    for (const panelHeight of [600, 900]) {
      height = panelHeight
      const start = position
      const target = 1980 - height * anchor
      motion.scrollTo(container, target, true)
      tick(0)
      expect(position).toBeCloseTo(start)
      tick(duration / 2)
      expect(position).toBeGreaterThan(Math.min(start, target))
      expect(position).toBeLessThan(Math.max(start, target))
      tick(duration)
      expect(position).toBeCloseTo(target)
      expect(container.scrollHeight - height).toBeGreaterThanOrEqual(target)
    }
    motion.scrollTo(container, 0, true)
    wrapper.unmount()
    expect(frames.size).toBe(0)
  })
})
