import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Events, Window } from '@wailsio/runtime'
import { GetPlatform } from '../../bindings/changeme/internal/infra/wails/greetservice'

export const useDeviceStore = defineStore('device', () => {
  const isMac = ref(false)
  const isWindowFullscreen = ref(false)

  async function checkFullscreen() {
    try {
      const isFs = await Window.IsFullscreen()
      console.log('[DeviceStore] checkFullscreen result:', isFs)
      isWindowFullscreen.value = isFs
    } catch (e) {
      console.error('Failed to check fullscreen state', e)
    }
  }

  async function init() {
    console.log('[DeviceStore] Initializing...')
    try {
      const platform = await GetPlatform()
      console.log('[DeviceStore] Platform:', platform)
      isMac.value = platform === 'darwin'
      await checkFullscreen()
    } catch (e) {
      console.error('Failed to init device store', e)
    }

    // Window events
    Events.On(Events.Types.Common.WindowFullscreen, checkFullscreen)
    Events.On(Events.Types.Common.WindowUnFullscreen, checkFullscreen)
    Events.On(Events.Types.Common.WindowDidResize, checkFullscreen)

    if (isMac.value) {
      Events.On(Events.Types.Mac.WindowDidEnterFullScreen, checkFullscreen)
      Events.On(Events.Types.Mac.WindowDidExitFullScreen, checkFullscreen)
      Events.On(Events.Types.Mac.WindowWillEnterFullScreen, checkFullscreen)
      Events.On(Events.Types.Mac.WindowWillExitFullScreen, checkFullscreen)
    }
  }

  return {
    isMac,
    isWindowFullscreen,
    init,
    checkFullscreen,
  }
})
