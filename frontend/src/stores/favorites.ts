import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { Events } from '@wailsio/runtime'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

export const useFavoritesStore = defineStore('favorites', () => {
  const favoritesMap = ref<Record<string, boolean>>({})
  const version = ref(0)

  const eventVersions = new Map<string, number>()

  function applyFavorite(trackId: string, favorite: boolean) {
    if (favoritesMap.value[trackId] === favorite) return
    favoritesMap.value[trackId] = favorite
    version.value++
  }

  const offFavoriteChanged = Events.On('library:favorite-changed', (ev: Events.WailsEvent) => {
    const track = ev.data as TrackDTO
    if (!track?.id) return
    eventVersions.set(track.id, (eventVersions.get(track.id) ?? 0) + 1)
    applyFavorite(track.id, track.is_favorite)
  })
  const offTrackUpdated = Events.On('library:track-updated', (ev: Events.WailsEvent) => {
    const track = ev.data as TrackDTO
    // Metadata refresh already invalidates library views; only seed the icon state.
    if (track?.id) favoritesMap.value[track.id] = track.is_favorite
  })

  function dispose() {
    offFavoriteChanged()
    offTrackUpdated()
  }

  async function toggle(trackId: string): Promise<boolean> {
    const eventVersion = eventVersions.get(trackId) ?? 0
    const newState = await LibraryService.ToggleFavorite(trackId)
    // An event received during the request is authoritative, including a newer
    // toggle from another window. The response is only a fallback.
    if ((eventVersions.get(trackId) ?? 0) === eventVersion) applyFavorite(trackId, newState)
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
