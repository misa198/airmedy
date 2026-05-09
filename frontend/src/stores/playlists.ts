import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import type { Playlist } from '../../bindings/airmedy/internal/domain/models'
import { Events } from '@wailsio/runtime'

export const usePlaylistsStore = defineStore('playlists', () => {
  const playlists = ref<Playlist[]>([])
  const loading = ref(false)

  async function loadAll() {
    loading.value = true
    try {
      const result = await PlaylistService.GetAllPlaylists()
      playlists.value = result.filter(Boolean) as Playlist[]
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

  function dispose() {
    _offDeleted()
    _offRenamed()
  }

  async function create(name: string, description = '') {
    const p = await PlaylistService.CreatePlaylist(name, description)
    if (p) playlists.value.push(p)
    return p
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

  return { playlists, loading, loadAll, create, rename, deletePlaylist, dispose }
})

