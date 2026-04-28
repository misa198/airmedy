import { onMounted, onUnmounted } from 'vue'
import { Events } from '@wailsio/runtime'

export function useLibrarySync(reloadFn: () => void) {
  let offSyncFinished: (() => void) | null = null
  let offLibraryUpdated: (() => void) | null = null

  onMounted(() => {
    offSyncFinished = Events.On('library:sync-finished', reloadFn)
    offLibraryUpdated = Events.On('library:updated', reloadFn)
  })

  onUnmounted(() => {
    offSyncFinished?.()
    offLibraryUpdated?.()
  })
}
