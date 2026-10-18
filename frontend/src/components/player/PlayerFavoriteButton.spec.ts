import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PlayerFavoriteButton from './PlayerFavoriteButton.vue'

const mocks = vi.hoisted(() => ({ track: { id: 't1', is_favorite: false } }))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/stores/player', () => ({
  usePlayerStore: () => ({ currentTrack: mocks.track }),
}))

vi.mock('@/stores/favorites', () => ({
  useFavoritesStore: () => ({
    isFavorite: (track: { is_favorite: boolean }) => track.is_favorite,
    toggle: vi.fn(),
  }),
}))

describe('PlayerFavoriteButton', () => {
  it.each([false, true])('uses the requested button and icon size (large: %s)', (large) => {
    const wrapper = mount(PlayerFavoriteButton, { props: { large } })
    expect(wrapper.classes()).toEqual(expect.arrayContaining(large ? ['h-9', 'w-9'] : ['h-7', 'w-7']))
    expect(wrapper.get('svg').classes()).toEqual(expect.arrayContaining(large ? ['h-5', 'w-5'] : ['h-4', 'w-4']))
    wrapper.unmount()
  })

  it.each([false, true])('has no mini-player hover state when favorite is %s', (favorite) => {
    mocks.track.is_favorite = favorite
    const wrapper = mount(PlayerFavoriteButton, { props: { mini: true } })
    expect(wrapper.classes().some(value => value === 'border' || value.startsWith('border-'))).toBe(false)
    expect(wrapper.classes().some(value => value.startsWith('hover:'))).toBe(false)
    expect(wrapper.attributes('aria-pressed')).toBe(String(favorite))
    expect(wrapper.get('svg').classes('fill-current')).toBe(favorite)
    expect(wrapper.classes()).toContain('transition-none')
    wrapper.unmount()
  })

  it('keeps the desktop button background transparent', () => {
    const wrapper = mount(PlayerFavoriteButton)
    expect(wrapper.classes()).not.toContain('bg-mini-player-pill-background/45')
    expect(wrapper.classes()).toContain('hover:bg-foreground/[0.04]')
    wrapper.unmount()
  })
})
