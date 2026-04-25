import { onMounted, onUnmounted, type Ref } from 'vue'
import { Events } from '@wailsio/runtime'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

export function useLibraryUpdates(tracks: Ref<TrackDTO[]>) {
  const handleUpdate = (ev: Events.WailsEvent) => {
    const updated = ev.data as TrackDTO
    if (!updated?.id) return
    const idx = tracks.value.findIndex(t => t.id === updated.id)
    if (idx !== -1) {
      tracks.value[idx] = updated
    }
  }

  const handleDelete = (ev: Events.WailsEvent) => {
    const id = ev.data as string
    if (!id) return
    tracks.value = tracks.value.filter(t => t.id !== id)
  }

  onMounted(() => {
    Events.On('library:track-updated', handleUpdate)
    Events.On('library:track-deleted', handleDelete)
  })

  onUnmounted(() => {
    Events.Off('library:track-updated', 'library:track-deleted')
  })
}
