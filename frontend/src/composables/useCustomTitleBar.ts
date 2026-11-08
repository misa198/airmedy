import { computed } from 'vue'
import { useDeviceStore } from '@/stores/device'
import { usePlayerStore } from '@/stores/player'

export function useCustomTitleBar() {
  const deviceStore = useDeviceStore()
  const playerStore = usePlayerStore()

  return computed(() =>
    (deviceStore.isWindows || deviceStore.isLinux)
    && !deviceStore.isWindowFullscreen
    && playerStore.playerMode !== 'fullscreen'
    && playerStore.playerMode !== 'mini',
  )
}
