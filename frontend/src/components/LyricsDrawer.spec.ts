import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { reactive } from 'vue'
import LyricsDrawer from './LyricsDrawer.vue'
import { usePlayerStore } from '../stores/player'

const api = vi.hoisted(() => ({ InspectRomanization: vi.fn(), RomanizeLyrics: vi.fn() }))
const romanization = vi.hoisted(() => ({ enabled: true }))
const player = vi.hoisted(() => ({
  lyrics: { content: '[00:00.00]你好 ^ Translation' },
  lyricsLoading: false,
  position: 0,
  isLyricsOpen: true,
  seek: vi.fn(),
  toggleLyrics: vi.fn(),
}))

vi.mock('../../bindings/airmedy/internal/infra/wails', () => ({ LyricsService: api }))
vi.mock('../stores/player', () => ({ usePlayerStore: () => reactive(player) }))
vi.mock('../stores/romanization', () => ({
  useRomanizationStore: () => Object.assign(reactive(romanization), { setEnabled: vi.fn(), getCached: () => undefined, cache: vi.fn() }),
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => vi.fn()) },
  Create: {
    Nullable: (fn: (value: unknown) => unknown) => (value: unknown) => value == null ? null : fn(value),
    Array: (fn: (value: unknown) => unknown) => (value: unknown[]) => (value ?? []).map(fn),
    Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value),
    Map: () => (value: unknown) => value,
  },
}))

describe('LyricsDrawer romanization', () => {
  let store: typeof player

  beforeEach(() => {
    setActivePinia(createPinia())
    store = usePlayerStore() as unknown as typeof player
    store.lyrics = { content: '[00:00.00]你好 ^ Translation' }
    store.lyricsLoading = false
    store.position = 0
    store.isLyricsOpen = true
    reactive(romanization).enabled = true
    api.InspectRomanization.mockImplementation(() => Object.assign(Promise.resolve({ supported: true, mandarinDefault: true }), { cancel: vi.fn().mockResolvedValue(undefined) }))
    api.RomanizeLyrics.mockReturnValue(Object.assign(Promise.resolve([{ text: 'nǐ hǎo', status: 'converted' }]), { cancel: vi.fn().mockResolvedValue(undefined) }))
  })

  afterEach(() => { vi.clearAllMocks(); vi.restoreAllMocks(); vi.useRealTimers(); vi.unstubAllGlobals() })

  it('sweeps enhanced lyrics only while open and following playback', async () => {
    store.lyrics = { content: '[00:01]Người <00:02>hỏi<00:03>\n[00:04]Next' }
    store.position = 1.5
    const wrapper = mount(LyricsDrawer, { global: { mocks: { $t: (key: string) => key } } })
    await flushPromises()
    expect(wrapper.get('.karaoke-word').attributes('style')).toContain('50%')
    expect(wrapper.get('.cursor-pointer').attributes('style')).toContain('transition: none')
    store.isLyricsOpen = false
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.karaoke-word').exists()).toBe(false)
    expect(wrapper.get('.cursor-pointer').attributes('style')).toBeUndefined()
    store.isLyricsOpen = true
    await wrapper.vm.$nextTick()
    expect(wrapper.get('.karaoke-word').attributes('style')).toContain('50%')
    await wrapper.get('.scrollbar-hide').trigger('wheel')
    expect(wrapper.find('.karaoke-word').exists()).toBe(false)
    wrapper.unmount()
  })

  it('replaces bilingual text through its sticky CJK control', async () => {
    const wrapper = mount(LyricsDrawer, { global: { mocks: { $t: (key: string) => key } } })
    await flushPromises()

    const button = wrapper.get('[data-test="drawer-romanization-toggle"]')
    await button.trigger('click')
    await flushPromises()
    expect(button.classes()).toContain('text-primary')
    expect(wrapper.text()).toContain('nǐ hǎo')
    expect(wrapper.text()).not.toContain('Translation')
    wrapper.unmount()
  })

  it('keeps the drawer control binary during conversion and after failure', async () => {
    let reject!: (error: Error) => void
    api.RomanizeLyrics.mockReturnValue(Object.assign(
      new Promise((_, no) => { reject = no }),
      { cancel: vi.fn().mockResolvedValue(undefined) },
    ))
    const wrapper = mount(LyricsDrawer, { global: { mocks: { $t: (key: string) => key } } })
    await flushPromises()

    const button = wrapper.get('[data-test="drawer-romanization-toggle"]')
    expect(button.attributes('aria-pressed')).toBe('true')
    expect(button.attributes('aria-busy')).toBeUndefined()
    expect(button.attributes('aria-label')).toBe('player.romanization')
    expect(button.find('.lucide-languages').exists()).toBe(true)

    reject(new Error('failed'))
    await flushPromises()
    expect(button.attributes('aria-label')).toBe('player.romanization')
    expect(button.find('.lucide-languages').exists()).toBe(true)
    wrapper.unmount()
  })

  it('remeasures after secondary text changes without interrupting browsing', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => window.setTimeout(() => callback(Date.now()), 16))
    vi.stubGlobal('cancelAnimationFrame', (id: number) => window.clearTimeout(id))
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(200)
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(300)
    store.lyrics = { content: '[00:00]你好\n[00:10]你好' }
    store.position = 10
    api.RomanizeLyrics.mockReturnValue(Object.assign(Promise.resolve([
      { text: 'nǐ hǎo', status: 'converted' }, { text: 'nǐ hǎo', status: 'converted' },
    ]), { cancel: vi.fn().mockResolvedValue(undefined) }))
    const wrapper = mount(LyricsDrawer, { global: { mocks: { $t: (key: string) => key } } })
    const container = wrapper.get('.scrollbar-hide').element
    const active = wrapper.findAll<HTMLElement>('.cursor-pointer')[1].element
    vi.spyOn(active, 'offsetTop', 'get').mockImplementation(() => active.textContent?.includes('nǐ hǎo') ? 600 : 300)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    expect(container.scrollTop).toBe(600)

    reactive(romanization).enabled = false
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    expect(container.scrollTop).toBe(300)

    reactive(romanization).enabled = true
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    expect(container.scrollTop).toBe(600)

    await wrapper.get('.scrollbar-hide').trigger('wheel')
    container.scrollTop = 100
    reactive(romanization).enabled = false
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    expect(container.scrollTop).toBe(100)
    wrapper.unmount()
  })

  it('restores auto-scroll when the drawer reopens', async () => {
    store.lyrics = { content: '[00:00.00]First\n[00:05.00]Second' }
    const wrapper = mount(LyricsDrawer, { global: { mocks: { $t: (key: string) => key } } })
    await flushPromises()

    await wrapper.get('.scrollbar-hide').trigger('wheel')
    expect(wrapper.html()).not.toContain('text-foreground/40')

    store.isLyricsOpen = false
    await wrapper.vm.$nextTick()
    store.isLyricsOpen = true
    await wrapper.vm.$nextTick()

    expect(wrapper.html()).toContain('text-foreground/40')
    wrapper.unmount()
  })
})
