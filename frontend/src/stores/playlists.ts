import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import type { Playlist } from '../../bindings/airmedy/internal/domain/models'

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

  return { playlists, loading, loadAll, create, rename, deletePlaylist }
})
