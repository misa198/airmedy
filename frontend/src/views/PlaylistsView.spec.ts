import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestingPinia } from '@pinia/testing'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import PlaylistsView from './PlaylistsView.vue'
import { useFavoritesStore } from '@/stores/favorites'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'

enableAutoUnmount(afterEach)
vi.mock('../../bindings/airmedy/internal/infra/wails/playlistservice', () => ({
  GetPlaylistByID: vi.fn(),
  GetPlaylistTracksPreview: vi.fn().mockResolvedValue([]),
}))
vi.mock('@wailsio/runtime', async (original) => ({
  ...await original<Record<string, unknown>>(), Events: { On: vi.fn(() => () => {}) },
}))

const rules = (live: boolean, field: string) => JSON.stringify({ live_updating: live, root: { groups: [{ rules: [{ field }] }] } })

beforeEach(() => { vi.clearAllMocks() })

describe('playlist mosaic favorite refresh', () => {
  it.each([false, true])('fetches only affected mosaics (custom Favorites cover: %s)', async (customCover) => {
    vi.mocked(PlaylistService.GetPlaylistByID).mockResolvedValue({ id: 'favorites', artwork_key: customCover ? 'cover' : null } as any)
    const pinia = createTestingPinia({ createSpy: vi.fn, initialState: { playlists: { playlists: [
      { id: 'normal' },
      { id: 'live', is_smart: true, rules: rules(true, 'is_favorite') },
      { id: 'snapshot', is_smart: true, rules: rules(false, 'is_favorite') },
      { id: 'unrelated', is_smart: true, rules: rules(true, 'genre') },
      { id: 'custom', is_smart: true, rules: rules(true, 'is_favorite'), artwork_key: 'cover' },
    ] } } })
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] })
    mount(PlaylistsView, { global: {
      plugins: [pinia, router, createI18n({ legacy: false, locale: 'en', messages: { en: {} } })],
      stubs: { PlaylistGrid: true, ViewHeader: true, CreatePlaylistDialog: true, SmartPlaylistDialog: true, ConfirmDialog: true, FilterDropdown: true },
    } })
    await flushPromises()
    vi.mocked(PlaylistService.GetPlaylistTracksPreview).mockClear()
    const store = useFavoritesStore(pinia)
    store.version++
    await new Promise(resolve => setTimeout(resolve, 75))
    await flushPromises()
    expect(vi.mocked(PlaylistService.GetPlaylistTracksPreview).mock.calls.map(([id]) => id).sort())
      .toEqual(customCover ? ['live'] : ['favorites', 'live'])
    expect(vi.mocked(PlaylistService.GetPlaylistTracksPreview).mock.calls.every(([, count]) => count === 4)).toBe(true)
  })
})
