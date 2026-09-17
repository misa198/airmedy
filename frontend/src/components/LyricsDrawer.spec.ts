import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import LyricsDrawer from './LyricsDrawer.vue'

const api = vi.hoisted(() => ({ InspectRomanization: vi.fn(), RomanizeLyrics: vi.fn() }))
const player = vi.hoisted(() => ({
  lyrics: { content: '[00:00.00]你好 ^ Translation' },
  lyricsLoading: false,
  position: 0,
  seek: vi.fn(),
  toggleLyrics: vi.fn(),
}))

vi.mock('../../bindings/airmedy/internal/infra/wails', () => ({ LyricsService: api }))
vi.mock('../stores/player', () => ({ usePlayerStore: () => player }))
vi.mock('@wailsio/runtime', () => ({ Events: { On: vi.fn(() => vi.fn()) } }))

describe('LyricsDrawer romanization', () => {
  beforeEach(() => {
    player.lyrics = { content: '[00:00.00]你好 ^ Translation' }
    player.lyricsLoading = false
    player.position = 0
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
})
