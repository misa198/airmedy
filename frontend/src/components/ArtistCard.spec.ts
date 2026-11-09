import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { describe, expect, it, vi } from 'vitest'
import ArtistCard from './ArtistCard.vue'
import source from './ArtistCard.vue?raw'
import { compileStyle, parse } from 'vue/compiler-sfc'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => vi.fn()) },
  Create: {
    Nullable: (fn: (value: unknown) => unknown) => (value: unknown) => value == null ? null : fn(value),
    Array: (fn: (value: unknown) => unknown) => (value: unknown[]) => (value ?? []).map(fn),
    Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value),
    Map: () => (value: unknown) => value,
  },
}))

vi.mock('../../bindings/airmedy/internal/infra/wails/libraryservice', () => ({
  GetArtistArtwork: vi.fn(),
}))

describe('ArtistCard artwork', () => {
  it('compiles hover scaling onto artwork only, never the containing group', () => {
    const { descriptor } = parse(source)
    const style = document.createElement('style')
    style.textContent = descriptor.styles.map(block => compileStyle({
      source: block.content, filename: 'ArtistCard.vue', id: 'data-v-test', scoped: block.scoped,
    }).code).join('\n')
    document.head.append(style)
    try {
      const rules = Array.from(style.sheet!.cssRules) as CSSStyleRule[]
      const hoverRules = rules.filter(rule => rule.selectorText?.includes(':hover') && rule.style.transform)
      expect(hoverRules.length).toBeGreaterThan(0)
      for (const rule of hoverRules) expect(rule.selectorText.trim().endsWith('.artist-artwork')).toBe(true)
    } finally {
      style.remove()
    }
  })

  it.each(['avatar', 'card'] as const)('keeps the animated %s wrapper while artwork loads or falls back', async (variant) => {
    vi.mocked(LibraryService.GetArtistArtwork).mockResolvedValue('/artwork/cached-key')

    const wrapper = mount(ArtistCard, {
      props: {
        artist: {
          id: 'artist-a',
          name: 'Artist A',
          sort_name: 'Artist A',
          normalization_key: 'artist-a',
          artwork_key_manual: null,
          artwork_key_local: null,
          artwork_key_online: null,
          artwork_key: '',
          created_at: '2026-01-01T00:00:00.000Z',
          updated_at: '2026-01-01T00:00:00.000Z',
        },
        variant,
      },
      global: { plugins: [createTestingPinia({ createSpy: vi.fn })], mocks: { $t: (key: string) => key } },
    })
    const artwork = wrapper.get('.artist-artwork').element
    await flushPromises()

    expect(LibraryService.GetArtistArtwork).toHaveBeenCalledWith('artist-a', 'artist-artwork:artist-a')
    expect(wrapper.get('img').attributes('src')).toBe('/artwork/cached-key')
    expect(wrapper.get('.artist-artwork').element).toBe(artwork)
    expect(wrapper.get('img').classes()).not.toContain('group-hover:scale-110')
    await wrapper.get('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('.artist-artwork').element).toBe(artwork)
    wrapper.unmount()
  })
})
