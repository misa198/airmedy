import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { describe, expect, it, vi } from 'vitest'
import ArtistCard from './ArtistCard.vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'

vi.mock('@wailsio/runtime', async (importOriginal) => ({
  ...await importOriginal<typeof import('@wailsio/runtime')>(),
  Events: { On: vi.fn(() => vi.fn()) },
}))

vi.mock('../../bindings/airmedy/internal/infra/wails/libraryservice', () => ({
  GetArtistArtwork: vi.fn(),
}))

describe('ArtistCard artwork', () => {
  it('uses the service URL when cached artwork is returned without an update event', async () => {
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
        variant: 'avatar',
      },
      global: { plugins: [createTestingPinia({ createSpy: vi.fn })] },
    })
    await flushPromises()

    expect(LibraryService.GetArtistArtwork).toHaveBeenCalledWith('artist-a', 'artist-artwork:artist-a')
    expect(wrapper.get('img').attributes('src')).toBe('/artwork/cached-key')
  })
})
