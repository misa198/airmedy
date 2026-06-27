import { onMounted, onUnmounted, type Ref } from 'vue'
import { Events } from '@wailsio/runtime'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

export interface LibraryUpdatesOptions {
  // When provided, an updated track that no longer satisfies the predicate is
  // removed from the list instead of replaced in place. Lets a scoped view (e.g.
  // an album detail) drop a track whose metadata edit moved it out of the view.
  belongs?: (track: TrackDTO) => boolean
}

export function useLibraryUpdates(tracks: Ref<TrackDTO[]>, opts: LibraryUpdatesOptions = {}) {
  const handleUpdate = (ev: Events.WailsEvent) => {
    const updated = ev.data as TrackDTO
    if (!updated?.id) return
    const idx = tracks.value.findIndex(t => t.id === updated.id)
    if (idx === -1) return
    if (opts.belongs && !opts.belongs(updated)) {
      tracks.value = tracks.value.filter(t => t.id !== updated.id)
      return
    }
    tracks.value = tracks.value.map((t, i) => i === idx ? updated : t)
  }

  const handleDelete = (ev: Events.WailsEvent) => {
    const id = ev.data as string
    if (!id) return
    tracks.value = tracks.value.filter(t => t.id !== id)
  }

  let offUpdate: (() => void) | null = null
  let offDelete: (() => void) | null = null

  onMounted(() => {
    offUpdate = Events.On('library:track-updated', handleUpdate)
    offDelete = Events.On('library:track-deleted', handleDelete)
  })

  onUnmounted(() => {
    offUpdate?.()
    offDelete?.()
  })
}
