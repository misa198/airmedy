import { onMounted, onUnmounted } from 'vue'
import { Events } from '@wailsio/runtime'

export function useLibrarySync(reloadFn: () => void) {
  let offSyncFinished: (() => void) | null = null
  let offLibraryUpdated: (() => void) | null = null
  let offTrackUpdated: (() => void) | null = null

  onMounted(() => {
    offSyncFinished = Events.On('library:sync-finished', (ev: Events.WailsEvent) => {
      // Background syncs (startup / periodic) do not trigger a reload — the UI
      // would flash unnecessarily when nothing visible changed. Data-refresh
      // events (library:updated, library:track-updated) still fire for real
      // changes and are handled below.
      if (!(ev.data as { background?: boolean })?.background) reloadFn()
    })
    offLibraryUpdated = Events.On('library:updated', reloadFn)
    offTrackUpdated = Events.On('library:track-updated', reloadFn)
  })

  onUnmounted(() => {
    offSyncFinished?.()
    offLibraryUpdated?.()
    offTrackUpdated?.()
  })
}
