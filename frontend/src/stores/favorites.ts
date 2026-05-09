import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { Events } from '@wailsio/runtime'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

export const useFavoritesStore = defineStore('favorites', () => {
  const favoritesMap = ref<Record<string, boolean>>({})
  const version = ref(0)

  // Listen for track updates to keep favorites in sync across all components
  const _offTrackUpdated = Events.On('library:track-updated', (ev: Events.WailsEvent) => {
    const track = ev.data as TrackDTO
    if (track && track.id) {
      favoritesMap.value[track.id] = track.is_favorite
      version.value++
    }
  })

  function dispose() {
    _offTrackUpdated()
  }

  async function toggle(trackId: string): Promise<boolean> {
    const newState = await LibraryService.ToggleFavorite(trackId)
    favoritesMap.value[trackId] = newState
    version.value++
    return newState
  }

  function isFavorite(track: TrackDTO): boolean {
    if (favoritesMap.value[track.id] !== undefined) {
      return favoritesMap.value[track.id]
    }
    return track.is_favorite
  }

  return { toggle, isFavorite, version, dispose }
})
