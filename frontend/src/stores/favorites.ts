import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'

export const useFavoritesStore = defineStore('favorites', () => {
  const version = ref(0)

  async function toggle(trackId: string): Promise<boolean> {
    await LibraryService.ToggleFavorite(trackId)
    version.value++
    return true
  }

  return { version, toggle }
})
