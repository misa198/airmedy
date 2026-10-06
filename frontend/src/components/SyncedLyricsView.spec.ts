import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import SyncedLyricsView from './SyncedLyricsView.vue'
import { useGpuLyrics } from '../composables/useGpuLyrics'

vi.mock('../composables/useGpuLyrics', async () => {
  const { ref } = await import('vue')
  return { useGpuLyrics: vi.fn(() => ({ ready: ref(false), draw: vi.fn(), layout: vi.fn() })) }
})

describe('SyncedLyricsView', () => {
  const wrappers: ReturnType<typeof mount>[] = []
  const frames = new Map<number, FrameRequestCallback>()
  const lines = [{ text: 'First', time: 0 }, { text: 'Active', time: 10 }, { text: 'Next', secondary: 'Translation', time: 20 }]
  const create = () => {
    const wrapper = mount(SyncedLyricsView, { props: { immersive: true, currentPosition: 10, lines } })
    wrappers.push(wrapper)
    return wrapper
  }
  const state = () => vi.mocked(useGpuLyrics).mock.calls.at(-1)![3].value
  const gpu = () => vi.mocked(useGpuLyrics).mock.results.at(-1)!.value
  beforeEach(() => {
    let id = 0
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { frames.set(++id, callback); return id })
    vi.stubGlobal('cancelAnimationFrame', (id: number) => frames.delete(id))
  })
  afterEach(() => {
    wrappers.splice(0).forEach(wrapper => wrapper.unmount())
    frames.clear()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('matches the lyric effects before the first GPU frame', async () => {
    const wrapper = create()
    const content = wrapper.findAll('[data-test="lyric-content"]')
    expect(content[0].attributes('style')).toContain('opacity: 0.25')
    expect(content[0].attributes('style')).toContain('filter: blur(0.35px)')
    expect(content[1].attributes('style')).toContain('opacity: 1')
    expect(wrapper.get('[aria-current="true"]').text()).toBe('Active')
    expect(wrapper.get('canvas').attributes('aria-hidden')).toBe('true')
    expect(wrapper.get('[role="button"]').classes()).toContain('focus-visible:outline-none')
    gpu().ready.value = true
    await nextTick()
    expect(wrapper.get('[data-test="lyric-content"]').attributes('style')).toContain('opacity: 0')
    expect(wrapper.get('[role="button"]').attributes('tabindex')).toBe('0')
    expect(wrapper.text()).toContain('Translation')
    gpu().ready.value = false
    await nextTick()
    expect(content[0].attributes('style')).toContain('opacity: 0.25')
  })

  it('preserves browse across position/translation changes and resumes on keyboard seek', async () => {
    const wrapper = create()
    await wrapper.get('[data-test="lyric-line"]').trigger('wheel')
    expect(state().browsing).toBe(true)
    await wrapper.setProps({ currentPosition: 20, secondary: ['Romanized'] })
    expect(state().browsing).toBe(true)
    expect(state().active).toBe(2)
    await wrapper.get('[role="button"]').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('seek')).toEqual([[0]])
    expect(state().browsing).toBe(false)
    await wrapper.get('[role="button"]').trigger('focus')
    expect(state().browsing).toBe(false)
    await wrapper.get('[role="button"]').trigger('keydown', { key: 'Meta' })
    expect(state().browsing).toBe(false)
    await wrapper.setProps({ lines: [{ text: 'New', time: 0 }] })
    expect(state().browsing).toBe(false)
  })

  it('leaves Space to the global play/pause shortcut', () => {
    const wrapper = create()
    const space = new KeyboardEvent('keydown', { key: ' ', code: 'Space', bubbles: true, cancelable: true })

    wrapper.get('[role="button"]').element.dispatchEvent(space)

    expect(wrapper.emitted('seek')).toBeUndefined()
    expect(space.defaultPrevented).toBe(false)
  })

  it('passes exact word timing and seeks through a translation click', async () => {
    const wrapper = create()
    const words = [{ text: 'Hello', start: 1, end: 2 }]
    await wrapper.setProps({ lines: [{ text: 'Hello', time: 1, words, secondary: '你好' }], currentPosition: 1.5 })
    expect(state().lines[0].words).toEqual(words)
    expect(state().position).toBe(1.5)
    await wrapper.get('[data-lyric-secondary]').trigger('click')
    expect(wrapper.emitted('seek')).toEqual([[1]])
  })

  it('passes the glow setting to the GPU without changing the lyric sweep', async () => {
    const wrapper = create()
    expect(state().lyricsGlow).toBe(true)
    await wrapper.setProps({ lyricsGlow: false })
    expect(state().lyricsGlow).toBe(false)
    expect(wrapper.find('[data-test="lyric-line"]').exists()).toBe(true)
  })

  it('waits for layout then places the current lyric at the immersive anchor', async () => {
    let width = 0
    let resize!: ResizeObserverCallback
    vi.stubGlobal('ResizeObserver', class {
      constructor(callback: ResizeObserverCallback) { resize = callback }
      observe() {}
      disconnect() {}
    })
    const wrapper = create()
    const viewport = wrapper.get('.overflow-y-auto').element as HTMLElement
    Object.defineProperties(viewport, { clientHeight: { get: () => 400 }, clientWidth: { get: () => width } })
    const row = wrapper.findAll('[data-test="lyric-line"]')[1].element
    Object.defineProperties(row, { offsetTop: { get: () => 500 }, clientHeight: { get: () => 80 } })
    await nextTick()
    for (const callback of [...frames.values()]) callback(0)
    frames.clear()
    expect(viewport.scrollTop).toBe(0)
    width = 600
    resize([], {} as ResizeObserver)
    await nextTick()
    for (const callback of [...frames.values()]) callback(100)
    expect(viewport.scrollTop).toBe(412)
    expect(gpu().layout).toHaveBeenCalled()
  })
})
