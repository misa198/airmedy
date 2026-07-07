import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import PlaylistDetailView from './PlaylistDetailView.vue'

vi.mock('../../bindings/airmedy/internal/infra/wails/playlistservice', () => {
  const playlists: Record<string, any> = {
    A: { id: 'A', name: 'Playlist A', description: '', artwork_key: null, is_smart: false, rules: null },
    B: { id: 'B', name: 'Playlist B', description: '', artwork_key: null, is_smart: false, rules: null },
  }
  return {
    GetPlaylistByID: vi.fn((id: string) => Promise.resolve(playlists[id] ?? null)),
    GetPlaylistTracks: vi.fn(() => Promise.resolve([])),
    GetPlaylistColors: vi.fn(() => Promise.resolve(null)),
    GetPlaylistsForTrack: vi.fn(() => Promise.resolve([])),
  }
})

vi.mock('../../bindings/airmedy/internal/infra/wails/libraryservice', () => ({
  GetFavoriteTracks: vi.fn(() => Promise.resolve([])),
  GetAlbumColors: vi.fn(() => Promise.resolve(null)),
}))

vi.mock('@wailsio/runtime', async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>()
  return {
    ...actual,
    Events: { On: vi.fn(() => () => {}) },
  }
})

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/playlists', name: 'playlists', component: { template: '<div>Playlists list</div>' } },
    { path: '/playlists/:id', name: 'playlist-detail', component: PlaylistDetailView },
  ],
})

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0)).then(() => new Promise(resolve => setTimeout(resolve, 0)))
}

describe('PlaylistDetailView navigation between two playlists', () => {
  it('updates playlist.name when navigating back and forth between A and B repeatedly', async () => {
    router.push('/playlists/A')
    await router.isReady()

    const wrapper = mount({ template: '<router-view v-slot="{ Component }"><KeepAlive :max="3"><component :is="Component" /></KeepAlive></router-view>' }, {
      global: {
        plugins: [router, createPinia(), createI18n({ legacy: false, locale: 'en', messages: { en: {} } })],
        stubs: { LazyImg: true, ContextMenu: true, PlaylistArtwork: true, ConfirmDialog: true, CreatePlaylistDialog: true, SmartPlaylistDialog: true },
      },
    })

    await flushPromises()
    expect(wrapper.text()).toContain('Playlist A')

    await router.push('/playlists/B')
    await flushPromises()
    expect(wrapper.text()).toContain('Playlist B')
    expect(wrapper.text()).not.toContain('Playlist A')

    await router.push('/playlists/A')
    await flushPromises()
    expect(wrapper.text()).toContain('Playlist A')
    expect(wrapper.text()).not.toContain('Playlist B')

    await router.push('/playlists/B')
    await flushPromises()
    expect(wrapper.text()).toContain('Playlist B')
    expect(wrapper.text()).not.toContain('Playlist A')
  })

  it('updates when navigating through an unrelated route in between (A -> playlists list -> B)', async () => {
    router.push('/playlists/A')
    await router.isReady()

    const wrapper = mount({ template: '<router-view v-slot="{ Component }"><KeepAlive :max="3"><component :is="Component" /></KeepAlive></router-view>' }, {
      global: {
        plugins: [router, createPinia(), createI18n({ legacy: false, locale: 'en', messages: { en: {} } })],
        stubs: { LazyImg: true, ContextMenu: true, PlaylistArtwork: true, ConfirmDialog: true, CreatePlaylistDialog: true, SmartPlaylistDialog: true },
      },
    })

    await flushPromises()
    expect(wrapper.text()).toContain('Playlist A')

    // Go to an unrelated route (the playlists list), then into a different playlist.
    await router.push('/playlists')
    await flushPromises()
    expect(wrapper.text()).toContain('Playlists list')

    await router.push('/playlists/B')
    await flushPromises()

    expect(wrapper.text()).toContain('Playlist B')
    expect(wrapper.text()).not.toContain('Playlist A')
  })
})
