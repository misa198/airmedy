import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import TopArtistsCarousel from './TopArtistsCarousel.vue'
import * as LibraryService from '../../../bindings/airmedy/internal/infra/wails/libraryservice'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('../../../bindings/airmedy/internal/infra/wails/libraryservice', () => ({
  GetAllArtists: vi.fn(),
}))

vi.stubGlobal('ResizeObserver', class {
  observe() {}
  disconnect() {}
})

function cancellable<T>(value: T) {
  return Object.assign(Promise.resolve(value), { cancel: vi.fn() })
}

const analyticsArtists = [
  { id: 'artist-a', name: 'Artist A', artwork_key: 'stale-key', listened_seconds: 120 },
  { id: 'artist-b', name: 'Artist B', artwork_key: '', listened_seconds: 60 },
]

describe('TopArtistsCarousel artwork', () => {
  it('loads full artists and passes them to ArtistCard for shared artwork behavior', async () => {
    vi.mocked(LibraryService.GetAllArtists).mockReturnValue(cancellable([
      { id: 'artist-a', name: 'Artist A', artwork_key_manual: null, artwork_key_local: null, artwork_key_online: 'online-key' },
      { id: 'artist-b', name: 'Artist B', artwork_key_manual: null, artwork_key_local: 'local-key', artwork_key_online: null },
      { id: 'unrelated', name: 'Unrelated', artwork_key_manual: null, artwork_key_local: null, artwork_key_online: 'other-key' },
    ]) as unknown as ReturnType<typeof LibraryService.GetAllArtists>)

    const wrapper = mount(TopArtistsCarousel, {
      props: { artists: analyticsArtists },
      global: {
        stubs: {
          ArtistCard: {
            name: 'ArtistCard',
            props: ['artist', 'variant'],
            template: '<div data-testid="artist-card" />',
          },
          LazyImg: true,
        },
      },
    })
    await flushPromises()

    expect(LibraryService.GetAllArtists).toHaveBeenCalledTimes(1)
    const card = wrapper.findComponent({ name: 'ArtistCard' })
    expect(card.props('variant')).toBe('avatar')
    expect(card.props('artist')).toMatchObject({ id: 'artist-a', artwork_key_online: 'online-key' })
    expect(wrapper.text()).toContain('Artist A')
    expect(wrapper.text()).toContain('analytics.top_artists')

    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()
    expect(wrapper.findAllComponents({ name: 'ArtistCard' }).some(component =>
      (component.props('artist') as { id: string }).id === 'artist-b',
    )).toBe(true)
  })

  it('keeps the analytics artwork fallback when artist loading fails', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.mocked(LibraryService.GetAllArtists).mockReturnValue(cancellable(Promise.reject(new Error('offline'))) as unknown as ReturnType<typeof LibraryService.GetAllArtists>)

    const wrapper = mount(TopArtistsCarousel, {
      props: { artists: [analyticsArtists[0]] },
      global: {
        stubs: {
          ArtistCard: true,
          LazyImg: { name: 'LazyImg', template: '<img data-testid="fallback-artwork" />' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="fallback-artwork"]').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'ArtistCard' }).exists()).toBe(false)
    consoleError.mockRestore()
  })
})
