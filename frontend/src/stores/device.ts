import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Events, Window } from '@wailsio/runtime'
import { GetPlatform } from '../../bindings/airmedy/internal/infra/wails/greetservice'

export const useDeviceStore = defineStore('device', () => {
  const isMac = ref(false)
  const isWindows = ref(false)
  const isLinux = ref(false)
  const isWindowFullscreen = ref(false)
  const isWindowMaximized = ref(false)

  async function checkFullscreen() {
    try {
      const isFs = await Window.IsFullscreen()
      isWindowFullscreen.value = isFs
      
      const isMax = await Window.IsMaximised()
      isWindowMaximized.value = isMax
    } catch (e) {
      console.error('Failed to check window state', e)
    }
  }

  async function init() {
    console.log('[DeviceStore] Initializing...')
    try {
      const platform = await GetPlatform()
      isMac.value = platform === 'darwin'
      isWindows.value = platform === 'windows'
      isLinux.value = platform === 'linux'
      await checkFullscreen()
    } catch (e) {
      console.error('Failed to init device store', e)
    }

    // Window events
    Events.On(Events.Types.Common.WindowFullscreen, checkFullscreen)
    Events.On(Events.Types.Common.WindowUnFullscreen, checkFullscreen)
    Events.On(Events.Types.Common.WindowDidResize, checkFullscreen)
    Events.On(Events.Types.Common.WindowMaximise, checkFullscreen)
    Events.On(Events.Types.Common.WindowUnMaximise, checkFullscreen)

    if (isMac.value) {
      Events.On(Events.Types.Mac.WindowDidEnterFullScreen, checkFullscreen)
      Events.On(Events.Types.Mac.WindowDidExitFullScreen, checkFullscreen)
    }
  }

  async function toggleFullscreen() {
    try {
      await Window.ToggleFullscreen()
      await checkFullscreen()
    } catch (e) {
      console.error('Failed to toggle fullscreen', e)
    }
  }

  async function toggleMaximize() {
    try {
      if (await Window.IsMaximised()) {
        await Window.UnMaximise()
      } else {
        await Window.Maximise()
      }
      await checkFullscreen()
    } catch (e) {
      console.error('Failed to toggle maximize', e)
    }
  }

  return {
    isMac,
    isWindows,
    isLinux,
    isWindowFullscreen,
    isWindowMaximized,
    init,
    checkFullscreen,
    toggleFullscreen,
    toggleMaximize,
  }
})
