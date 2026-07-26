import { ref } from 'vue'
import { usePlaylistsStore } from '@/stores/playlists'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'

const isVisible = ref(false)
const pendingTrackIDs = ref<string[]>([])

export function useCreatePlaylistWithTracks() {
  function open(trackIDs: string[]) {
    pendingTrackIDs.value = [...new Set(trackIDs)]
    isVisible.value = true
  }

  function close() {
    isVisible.value = false
    pendingTrackIDs.value = []
  }

  async function create(name: string) {
    const trackIDs = pendingTrackIDs.value
    const playlist = await usePlaylistsStore().create(name)
    if (!playlist) return

    await PlaylistService.AddTracksToPlaylist(playlist.id, trackIDs, '')
    pendingTrackIDs.value = []
  }

  return { isVisible, open, close, create }
}
