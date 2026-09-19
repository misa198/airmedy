import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { reactive } from 'vue'
import LyricsDrawer from './LyricsDrawer.vue'
import { usePlayerStore } from '../stores/player'

const api = vi.hoisted(() => ({ InspectRomanization: vi.fn(), RomanizeLyrics: vi.fn() }))
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
vi.mock('../stores/romanization', () => ({ useRomanizationStore: () => ({ enabled: true, setEnabled: vi.fn() }) }))
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
    api.InspectRomanization.mockImplementation(() => Object.assign(Promise.resolve({ supported: true, mandarinDefault: true }), { cancel: vi.fn().mockResolvedValue(undefined) }))
    api.RomanizeLyrics.mockReturnValue(Object.assign(Promise.resolve([{ text: 'nǐ hǎo', status: 'converted' }]), { cancel: vi.fn().mockResolvedValue(undefined) }))
  })

  afterEach(() => { vi.clearAllMocks() })

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
