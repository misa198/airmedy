import { afterEach, describe, it, expect, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import PlaylistDetailView from './PlaylistDetailView.vue'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import type { Playlist, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { CancellablePromise, Events } from '@wailsio/runtime'

enableAutoUnmount(afterEach)

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

describe('PlaylistDetailView background refresh', () => {
  it.each([false, true])('preserves the track list when a favorite changes (smart: %s)', async (isSmart) => {
    vi.mocked(Events.On).mockClear()
    vi.stubGlobal('ResizeObserver', class {
      observe() {}
      disconnect() {}
    })
    const track = { id: 'track-1', title: 'Track 1', duration: 120, is_favorite: false } as TrackDTO
    vi.mocked(PlaylistService.GetPlaylistByID).mockResolvedValue({
      id: 'A', name: 'Playlist A', is_smart: isSmart,
    } as Playlist)
    let finishRefresh!: (tracks: TrackDTO[]) => void
    vi.mocked(PlaylistService.GetPlaylistTracks)
      .mockResolvedValueOnce([track])
      .mockImplementationOnce(() => new CancellablePromise(resolve => { finishRefresh = resolve }))
    await router.push('/playlists/A')

    const wrapper = mount(PlaylistDetailView, {
      global: {
        plugins: [router, createPinia(), createI18n({ legacy: false, locale: 'en', messages: { en: {} } })],
        stubs: {
          ContextMenu: true, PlaylistArtwork: true, ConfirmDialog: true,
          CreatePlaylistDialog: true, SmartPlaylistDialog: true,
          TrackTableHeader: true, TrackTableRow: true, TrackContextMenu: true,
        },
      },
    })
    await flushPromises()

    const list = wrapper.get('.track-table-virtual-list').element as HTMLElement
    list.scrollTop = 560
    const updated = { ...track, is_favorite: true }
    for (const [name, handler] of vi.mocked(Events.On).mock.calls) {
      if (name === 'library:track-updated') handler({ data: updated } as Events.WailsEvent)
    }
    await new Promise(resolve => setTimeout(resolve, 75))
    await flushPromises()

    try {
      expect(finishRefresh).toBeTypeOf('function')
      expect(wrapper.get('.track-table-virtual-list').element).toBe(list)
      expect(list.scrollTop).toBe(560)
      expect(wrapper.text()).not.toContain('--')
    } finally {
      finishRefresh([updated])
      await flushPromises()
      vi.unstubAllGlobals()
    }
    expect(wrapper.get('.track-table-virtual-list').element).toBe(list)
  })
})
