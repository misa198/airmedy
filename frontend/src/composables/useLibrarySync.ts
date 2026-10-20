import { onActivated, onDeactivated, onMounted, onUnmounted, watch } from 'vue'
import { Events } from '@wailsio/runtime'

const RELOAD_DEBOUNCE_MS = 50

// Keeps view data fresh without turning KeepAlive into a source of background
// database work. A cached view is marked dirty while inactive, then refreshes
// once when the user returns to it. The visible view refreshes promptly, with
// the paired `library:track-updated` / `library:updated` events coalesced.
type ReloadReason = 'library' | 'favorite'

export function useLibrarySync(
  reloadFn: (reason: ReloadReason) => void | Promise<void>,
  options: { favorites?: { version: () => number; relevant: () => boolean } } = {},
) {
  let offSyncFinished: (() => void) | null = null
  let offLibraryUpdated: (() => void) | null = null
  let offTrackUpdated: (() => void) | null = null
  let offMobileSyncUpdated: (() => void) | null = null
  let reloadTimer: ReturnType<typeof setTimeout> | null = null
  let isActive = false
  let disposed = false
  let running = false
  let pending: ReloadReason | null = null

  const scheduleReload = () => {
    if (!isActive || disposed || running || reloadTimer !== null || pending === null) return
    reloadTimer = setTimeout(async () => {
      reloadTimer = null
      const reason = pending!
      pending = null
      running = true
      try {
        await reloadFn(reason)
      } catch (error) {
        console.error('Failed to refresh library view', error)
      } finally {
        running = false
        scheduleReload()
      }
    }, RELOAD_DEBOUNCE_MS)
  }

  const invalidate = (reason: ReloadReason) => {
    // Metadata changes require the broader refresh when both arrive together.
    pending = pending === 'library' ? pending : reason
    scheduleReload()
  }
  const handleLibraryChange = () => invalidate('library')

  if (options.favorites) {
    const { version, relevant } = options.favorites
    watch(version, () => {
      if (relevant()) invalidate('favorite')
    })
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
    offMobileSyncUpdated = Events.On('mobile-library-sync:updated', (ev: Events.WailsEvent) => {
      const plan = (Array.isArray(ev.data) ? ev.data[0] : ev.data?.data ?? ev.data) as { status?: string } | undefined
      if (plan?.status === 'complete') handleLibraryChange()
    })
  })

  onActivated(() => {
    isActive = true
    scheduleReload()
  })

  onDeactivated(() => {
    isActive = false
    if (reloadTimer !== null) clearTimeout(reloadTimer)
    reloadTimer = null
  })

  onUnmounted(() => {
    disposed = true
    if (reloadTimer !== null) clearTimeout(reloadTimer)
    offSyncFinished?.()
    offLibraryUpdated?.()
    offTrackUpdated?.()
    offMobileSyncUpdated?.()
  })

  return { invalidate }
}
