import { computed } from 'vue'
import { useDeviceStore } from '@/stores/device'
import { usePlayerStore } from '@/stores/player'

export function useCustomTitleBar() {
  const deviceStore = useDeviceStore()
  const playerStore = usePlayerStore()

  const hasCustomTitleBar = computed(() =>
    (deviceStore.isWindows || deviceStore.isLinux)
  // true
    && !deviceStore.isWindowFullscreen
    && playerStore.playerMode !== 'mini',
  )
  const canConfigureTitleBar = computed(() =>
    (deviceStore.isWindows || deviceStore.isLinux) && hasCustomTitleBar.value,
  // true
  )

  return { hasCustomTitleBar, canConfigureTitleBar }
}
