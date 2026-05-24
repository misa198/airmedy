import { ref } from 'vue'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

const isVisible = ref(false)
const targetTrack = ref<TrackDTO | null>(null)

export function useFindLyricsDialog() {
  function open(track: TrackDTO) {
    targetTrack.value = track
    isVisible.value = true
  }

  function close() {
    isVisible.value = false
    targetTrack.value = null
  }

  return {
    isVisible,
    targetTrack,
    open,
    close,
  }
}
