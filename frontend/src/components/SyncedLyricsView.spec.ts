import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import SyncedLyricsView from './SyncedLyricsView.vue'

const originalScrollTo = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollTo')
const originalClientHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientHeight')
const originalClientWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientWidth')

describe('SyncedLyricsView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    if (originalScrollTo) {
      Object.defineProperty(HTMLElement.prototype, 'scrollTo', originalScrollTo)
    } else {
      delete (HTMLElement.prototype as { scrollTo?: HTMLElement['scrollTo'] }).scrollTo
    }
    if (originalClientHeight) {
      Object.defineProperty(HTMLElement.prototype, 'clientHeight', originalClientHeight)
    } else {
      delete (HTMLElement.prototype as { clientHeight?: number }).clientHeight
    }
    if (originalClientWidth) {
      Object.defineProperty(HTMLElement.prototype, 'clientWidth', originalClientWidth)
    } else {
      delete (HTMLElement.prototype as { clientWidth?: number }).clientWidth
    }
  })

  it('scrolls to the active line once the initially collapsed panel has layout', async () => {
    const scrollTo = vi.fn()
    let clientWidth = 0
    let resizeCallback: ResizeObserverCallback | undefined
    class ResizeObserverMock {
      constructor(callback: ResizeObserverCallback) {
        resizeCallback = callback
      }
      observe() {}
      disconnect() {}
    }
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      callback(0)
      return 1
    })
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    vi.stubGlobal('ResizeObserver', ResizeObserverMock)
    Object.defineProperty(HTMLElement.prototype, 'scrollTo', { configurable: true, value: scrollTo })
    Object.defineProperty(HTMLElement.prototype, 'clientHeight', { configurable: true, get: () => 400 })
    Object.defineProperty(HTMLElement.prototype, 'clientWidth', { configurable: true, get: () => clientWidth })

    mount(SyncedLyricsView, {
      props: {
        currentPosition: 15,
        lines: [
          { text: 'First', time: 0 },
          { text: 'Active', time: 15 },
          { text: 'Next', time: 20 },
        ],
      },
    })

    await nextTick()
    expect(scrollTo).not.toHaveBeenCalled()

    clientWidth = 400
    resizeCallback?.([], {} as ResizeObserver)
    await nextTick()

    expect(scrollTo).toHaveBeenCalled()
  })

  it('progressively blurs non-active lines in immersive mode', () => {
    const wrapper = mount(SyncedLyricsView, {
      props: {
        immersive: true,
        currentPosition: 15,
        lines: [
          { text: 'Zero', time: 0 },    // distance 3 → blur(2px),   opacity 0.1
          { text: 'First', time: 5 },   // distance 2 → blur(1.25px), opacity 0.15
          { text: 'Second', time: 10 }, // distance 1 → blur(0.35px), opacity 0.25
          { text: 'Active', time: 15 }, // distance 0 → blur(0),      opacity 1
          { text: 'Fourth', time: 20 }, // distance 1 → blur(0.35px), opacity 0.25
          { text: 'Fifth', time: 25 },  // distance 2 → blur(1.25px), opacity 0.15
        ],
      },
    })

    const lines = wrapper.findAll('[data-test="lyric-line"]')
    // blur
    expect(lines[3].attributes('style')).toContain('blur(0)')
    expect(lines[2].attributes('style')).toContain('blur(0.35px)')
    expect(lines[4].attributes('style')).toContain('blur(0.35px)')
    expect(lines[1].attributes('style')).toContain('blur(1.25px)')
    expect(lines[5].attributes('style')).toContain('blur(1.25px)')
    expect(lines[0].attributes('style')).toContain('blur(2px)')
    // opacity
    expect(lines[2].attributes('style')).toContain('opacity: 0.25')
    expect(lines[4].attributes('style')).toContain('opacity: 0.25')
    expect(lines[1].attributes('style')).toContain('opacity: 0.15')
    expect(lines[5].attributes('style')).toContain('opacity: 0.15')
    expect(lines[0].attributes('style')).toContain('opacity: 0.1')
  })
})
