import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import type { Playlist, SmartPlaylistConfig } from '../../bindings/airmedy/internal/domain/models'
import { Events } from '@wailsio/runtime'

const FAVORITES_PINNED_KEY = 'airmedy:favorites-pinned'

export const usePlaylistsStore = defineStore('playlists', () => {
  const playlists = ref<Playlist[]>([])
  const loading = ref(false)
  const favoritesPinned = ref(localStorage.getItem(FAVORITES_PINNED_KEY) !== 'false')

  async function loadAll() {
    loading.value = true
    try {
      const result = await PlaylistService.GetAllPlaylists()
      // Favorites has a real DB row (for artwork), but every other consumer
      // of this list (add-to-playlist menus, pinned list, playlists grid)
      // expects normal user playlists — keep it out and let call sites that
      // need it (PlaylistDetailView, PlaylistsView artwork) fetch it directly.
      playlists.value = (result.filter(Boolean) as Playlist[]).filter((p) => p.id !== 'favorites')
    } catch (e) {
      console.error('Failed to load playlists', e)
    } finally {
      loading.value = false
    }
  }

  // Handle external events
  const _offDeleted = Events.On('playlist:deleted', (ev: Events.WailsEvent) => {
    const id = ev.data as string
    playlists.value = playlists.value.filter((p) => p.id !== id)
  })

  const _offRenamed = Events.On('playlist:renamed', async (ev: Events.WailsEvent) => {
    const id = ev.data as string
    const p = playlists.value.find((x) => x.id === id)
    if (p) {
      try {
        const updated = await PlaylistService.GetPlaylistByID(id)
        if (updated) {
          p.name = updated.name
          p.description = updated.description
        }
      } catch (e) {
        console.error('Failed to update renamed playlist in store', e)
      }
    }
  })

  const _offPinned = Events.On('playlist:pinned-changed', async (ev: Events.WailsEvent) => {
    const id = ev.data as string
    const p = playlists.value.find((x) => x.id === id)
    if (p) {
      try {
        const updated = await PlaylistService.GetPlaylistByID(id)
        if (updated) p.pinned_at = updated.pinned_at
      } catch (e) {
        console.error('Failed to update pinned playlist in store', e)
      }
    }
  })

  const _offArtworkChanged = Events.On('playlist:artwork-changed', (ev: Events.WailsEvent) => {
    const payload = ev.data as { playlist_id: string; artwork_key: string | null }
    const p = playlists.value.find((x) => x.id === payload.playlist_id)
    if (p) p.artwork_key = payload.artwork_key
  })

  const _offRulesChanged = Events.On('playlist:rules-changed', async (ev: Events.WailsEvent) => {
    const payload = ev.data as { playlist_id: string; sender_id: string }
    const p = playlists.value.find((x) => x.id === payload.playlist_id)
    if (p) {
      try {
        const updated = await PlaylistService.GetPlaylistByID(payload.playlist_id)
        if (updated) {
          p.rules = updated.rules
        }
      } catch (e) {
        console.error('Failed to update smart playlist rules in store', e)
      }
    }
  })

  function dispose() {
    _offDeleted()
    _offRenamed()
    _offPinned()
    _offArtworkChanged()
    _offRulesChanged()
  }

  async function create(name: string, description = '') {
    const p = await PlaylistService.CreatePlaylist(name, description)
    if (p) playlists.value.push(p)
    return p
  }

  async function createSmart(name: string, description: string, config: SmartPlaylistConfig) {
    const p = await PlaylistService.CreateSmartPlaylist(name, description, config)
    if (p) playlists.value.push(p)
    return p
  }

  async function updateSmartRules(id: string, config: SmartPlaylistConfig, senderID: string) {
    await PlaylistService.UpdateSmartPlaylistRules(id, config, senderID)
    const p = playlists.value.find((x) => x.id === id)
    if (p) {
      p.rules = JSON.stringify(config)
    }
  }

  async function rename(id: string, name: string) {
    const p = playlists.value.find((x) => x.id === id)
    const description = p?.description ?? ''
    await PlaylistService.UpdatePlaylist(id, name, description)
    if (p) p.name = name
  }

  async function deletePlaylist(id: string) {
    await PlaylistService.DeletePlaylist(id)
    playlists.value = playlists.value.filter((p) => p.id !== id)
  }

  function isPinned(playlist: Playlist): boolean {
    if (playlist.id === 'favorites') return favoritesPinned.value
    return !!playlist.pinned_at
  }

  async function togglePinned(id: string) {
    if (id === 'favorites') {
      favoritesPinned.value = !favoritesPinned.value
      localStorage.setItem(FAVORITES_PINNED_KEY, String(favoritesPinned.value))
      return favoritesPinned.value
    }
    const newState = await PlaylistService.TogglePlaylistPinned(id)
    const p = playlists.value.find((x) => x.id === id)
    if (p) p.pinned_at = newState ? new Date().toISOString() : null
    return newState
  }

  const pinnedPlaylists = computed(() =>
    playlists.value
      .filter((p) => p.pinned_at)
      .sort((a, b) => (a.pinned_at! < b.pinned_at! ? -1 : 1))
  )

  // Smart playlists derive their tracks from rules and reject manual
  // add/remove server-side — keep them out of "add to playlist" menus.
  const manualPlaylists = computed(() => playlists.value.filter((p) => !p.is_smart))

  return {
    playlists,
    loading,
    favoritesPinned,
    loadAll,
    create,
    createSmart,
    updateSmartRules,
    rename,
    deletePlaylist,
    isPinned,
    togglePinned,
    pinnedPlaylists,
    manualPlaylists,
    dispose,
  }
})

