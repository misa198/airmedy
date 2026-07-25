import { onActivated, onDeactivated, onMounted, onUnmounted } from 'vue'
import { Events } from '@wailsio/runtime'

const RELOAD_DEBOUNCE_MS = 50

// Keeps view data fresh without turning KeepAlive into a source of background
// database work. A cached view is marked dirty while inactive, then refreshes
// once when the user returns to it. The visible view refreshes promptly, with
// the paired `library:track-updated` / `library:updated` events coalesced.
export function useLibrarySync(reloadFn: () => void) {
  let offSyncFinished: (() => void) | null = null
  let offLibraryUpdated: (() => void) | null = null
  let offTrackUpdated: (() => void) | null = null
  let reloadTimer: ReturnType<typeof setTimeout> | null = null
  let isActive = false
  let isDirty = false

  const scheduleReload = () => {
    if (reloadTimer !== null) return
    reloadTimer = setTimeout(() => {
      reloadTimer = null
      reloadFn()
    }, RELOAD_DEBOUNCE_MS)
  }

  const handleLibraryChange = () => {
    if (isActive) {
      scheduleReload()
      return
    }
    isDirty = true
  }

  onMounted(() => {
    // onMounted also covers a consumer rendered outside KeepAlive.
    isActive = true
    offSyncFinished = Events.On('library:sync-finished', (ev: Events.WailsEvent) => {
      // Background syncs (startup / periodic) do not trigger a reload — the UI
      // would flash unnecessarily when nothing visible changed. Data-refresh
      // events (library:updated, library:track-updated) still fire for real
      // changes and are handled below.
      if (!(ev.data as { background?: boolean })?.background) handleLibraryChange()
    })
    offLibraryUpdated = Events.On('library:updated', handleLibraryChange)
    offTrackUpdated = Events.On('library:track-updated', handleLibraryChange)
  })

  onActivated(() => {
    isActive = true
    if (!isDirty) return
    isDirty = false
    scheduleReload()
  })

  onDeactivated(() => {
    isActive = false
    if (reloadTimer !== null) {
      clearTimeout(reloadTimer)
      reloadTimer = null
      isDirty = true
    }
  })

  onUnmounted(() => {
    if (reloadTimer !== null) clearTimeout(reloadTimer)
    offSyncFinished?.()
    offLibraryUpdated?.()
    offTrackUpdated?.()
  })
}
