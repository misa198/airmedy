import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PlayerLyrics from './PlayerLyrics.vue'
import { useAppStore } from '../stores/app'
import { useRomanizationStore } from '../stores/romanization'

const api = vi.hoisted(() => ({ InspectRomanization: vi.fn(), RomanizeLyrics: vi.fn() }))
vi.mock('../../bindings/airmedy/internal/infra/wails', () => ({ LyricsService: api }))
vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => vi.fn()) },
  Create: {
    Nullable: (fn: (value: unknown) => unknown) => (value: unknown) => value == null ? null : fn(value),
    Array: (fn: (value: unknown) => unknown) => (value: unknown[]) => (value ?? []).map(fn),
    Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value),
    Map: () => (value: unknown) => value,
  },
}))

function request<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = Object.assign(new Promise<T>((yes, no) => { resolve = yes; reject = no }), {
    cancel: vi.fn().mockResolvedValue(undefined),
  })
  return { promise, resolve, reject }
}

describe('fullscreen romanization', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    api.InspectRomanization.mockImplementation(() => Object.assign(Promise.resolve({ supported: true, mandarinDefault: true }), { cancel: vi.fn().mockResolvedValue(undefined) }))
    vi.stubGlobal('requestAnimationFrame', vi.fn(() => 1))
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
  })
  afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals() })
  const create = (lyrics = '你好 ^ Translation\nHello', teleport = false) => mount(PlayerLyrics, {
    props: { lyrics, romanization: true },
    global: { mocks: { $t: (key: string) => key }, stubs: { Teleport: !teleport } },
  })

  it.each(['light', 'dark', 'black'])('replaces bilingual without copying Latin in %s theme', async (theme) => {
    const task = request<{ text: string; status: string }[]>()
    api.RomanizeLyrics.mockReturnValue(task.promise)
    const wrapper = create()
    wrapper.element.classList.add(theme)
    await flushPromises()
    expect(api.InspectRomanization).toHaveBeenCalledWith(['你好', 'Hello'])
    const button = wrapper.get('button')
    expect(button.attributes('aria-pressed')).toBe('false')
    expect(button.attributes('title')).toContain('player.romanization_mandarin')
    expect(button.attributes('aria-label')).toBe('player.romanization')
    expect(button.text()).toBe('')
    expect(button.classes()).toContain('h-10')
    await button.trigger('click')
    expect(wrapper.get('button').attributes('aria-busy')).toBe('true')
    expect(wrapper.text()).toContain('Translation')
    task.resolve([{ text: 'nǐ hǎo', status: 'converted' }, { text: '', status: 'unsupported' }])
    await flushPromises()
    expect(wrapper.text()).toContain('nǐ hǎo')
    expect(wrapper.text()).not.toContain('Translation')
    expect(wrapper.text().match(/Hello/g)).toHaveLength(1)
    expect(wrapper.get('button').classes()).not.toContain('bg-foreground')
    expect(wrapper.get('[data-test="romanization-fill"]').classes()).toEqual(expect.arrayContaining(['inset-0', 'bg-foreground', 'opacity-100']))
    expect(wrapper.get('button').classes()).toContain('text-background')
    expect(wrapper.get('button').classes().join(' ')).not.toMatch(/text-white|text-black|bg-white|bg-black/)
    await wrapper.get('button').trigger('click')
    expect(wrapper.text()).toContain('Translation')
    wrapper.unmount()
  })

  it('renders its single icon button in the fullscreen toolbar and removes it on close', async () => {
    const toolbar = document.createElement('div')
    toolbar.id = 'fullscreen-lyrics-actions'
    document.body.append(toolbar)
    const wrapper = create(undefined, true)
    try {
      await flushPromises()
      expect(toolbar.querySelectorAll('button')).toHaveLength(1)
      expect(toolbar.querySelector('button')?.getAttribute('aria-label')).toBe('player.romanization')
      expect(wrapper.find('button').exists()).toBe(false)
      wrapper.unmount()
      expect(toolbar.querySelector('button')).toBeNull()
    } finally {
      toolbar.remove()
    }
  })

  it('preserves synced browsing and seeks original timestamps', async () => {
    const task = request<{ text: string; status: string }[]>()
    api.RomanizeLyrics.mockReturnValue(task.promise)
    const wrapper = create('[00:01.00]你好 ^ Translation\n[00:02.00]世界')
    await wrapper.setProps({ immersive: true, currentPosition: 2 })
    await flushPromises()
    await wrapper.get('[data-test="lyric-line"]').trigger('wheel')
    await wrapper.get('button').trigger('click')
    task.resolve([{ text: 'nǐ hǎo', status: 'converted' }, { text: '', status: 'failed' }])
    await flushPromises()
    expect(wrapper.get('[data-test="lyric-line"]').attributes('style')).toContain('opacity: 1')
    await wrapper.get('[data-test="lyric-line"]').trigger('click')
    expect(wrapper.emitted('seek')).toEqual([[1]])
    wrapper.unmount()
  })

  it('rejects stale results, cancels on close, and preserves session preference', async () => {
    const stale = request<{ text: string; status: string }[]>()
    const current = request<{ text: string; status: string }[]>()
    api.RomanizeLyrics.mockReturnValueOnce(stale.promise).mockReturnValue(current.promise)
    const wrapper = create()
    await flushPromises()
    await wrapper.get('button').trigger('click')
    await wrapper.setProps({ lyrics: '世界 ^ World' })
    await flushPromises()
    expect(stale.promise.cancel).toHaveBeenCalled()
    stale.resolve([{ text: 'STALE', status: 'converted' }])
    await flushPromises()
    expect(wrapper.text()).not.toContain('STALE')
    expect(wrapper.text()).toContain('World')
    wrapper.unmount()
    expect(current.promise.cancel).toHaveBeenCalled()
    expect(useRomanizationStore().enabled).toBe(true)
  })

  it('keeps bilingual on failure and retries', async () => {
    const failure = request<never>()
    api.RomanizeLyrics.mockReturnValue(failure.promise)
    const wrapper = create()
    await flushPromises()
    await wrapper.get('button').trigger('click')
    failure.reject(new Error('failed'))
    await flushPromises()
    expect(wrapper.text()).toContain('Translation')
    expect(wrapper.get('button').attributes('aria-label')).toBe('player.romanization_retry')
    const retry = request<{ text: string; status: string }[]>()
    api.RomanizeLyrics.mockReturnValue(retry.promise)
    await wrapper.get('[data-test="romanization-toggle"]').trigger('click')
    expect(api.RomanizeLyrics).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('hides the control when only bilingual is supported or outside fullscreen', async () => {
    api.InspectRomanization.mockImplementation(() => Object.assign(Promise.resolve({ supported: false, mandarinDefault: false }), { cancel: vi.fn().mockResolvedValue(undefined) }))
    const wrapper = create('Hello ^ 你好')
    await flushPromises()
    expect(api.InspectRomanization).toHaveBeenCalledWith(['Hello'])
    expect(wrapper.find('button').exists()).toBe(false)
    await wrapper.setProps({ romanization: false })
    expect(wrapper.find('button').exists()).toBe(false)
    wrapper.unmount()
  })

  it('hides active romanization when the feature is disabled', async () => {
    api.RomanizeLyrics.mockReturnValue(Object.assign(
      Promise.resolve([{ text: 'nǐ hǎo', status: 'converted' }, { text: '', status: 'unsupported' }]),
      { cancel: vi.fn().mockResolvedValue(undefined) },
    ))
    const wrapper = create()
    await flushPromises()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('nǐ hǎo')

    useAppStore().romanizationEnabled = false
    await flushPromises()
    expect(wrapper.find('[data-test="romanization-toggle"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Translation')
    wrapper.unmount()
  })
})
