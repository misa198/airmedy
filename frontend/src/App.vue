<script setup lang="ts">
import { onMounted, watch, onUnmounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MainLayout from './layouts/MainLayout.vue'
import FindLyricsDialog from './components/FindLyricsDialog.vue'
import { hexToRgba } from './lib/utils'
import { usePlayerStore } from './stores/player'
import { useDeviceStore } from './stores/device'
import { usePlaylistsStore } from './stores/playlists'
import { useAppStore } from './stores/app'
import { useI18n } from 'vue-i18n'
import { Events } from '@wailsio/runtime'
import * as WindowService from '../bindings/airmedy/internal/infra/wails/windowservice'

const route = useRoute()
const router = useRouter()
const { locale } = useI18n()
const playerStore = usePlayerStore()
const deviceStore = useDeviceStore()
const playlistsStore = usePlaylistsStore()
const appStore = useAppStore()

const isRouterReady = ref(false)
const isMiniPlayer = computed(() => {
  return route.name === 'mini-player' || 
         route.path === '/mini-player' || 
         window.location.hash.includes('mini-player') ||
         window.location.search.includes('mode=mini')
})

const handleKeyDown = (e: KeyboardEvent) => {
  const target = e.target as HTMLElement
  if (
    target.tagName === 'INPUT' ||
    target.tagName === 'TEXTAREA' ||
    target.isContentEditable
  ) {
    return
  }

  const isMac = deviceStore.isMac
  const ctrlKey = isMac ? e.metaKey : e.ctrlKey
  const altKey = e.altKey

  if (e.code === 'Space') {
    e.preventDefault()
    playerStore.togglePlayPause()
  } else if (ctrlKey && e.key === 'ArrowRight') {
    e.preventDefault()
    if (altKey) playerStore.fastForward()
    else playerStore.next()
  } else if (ctrlKey && e.key === 'ArrowLeft') {
    e.preventDefault()
    if (altKey) playerStore.rewind()
    else playerStore.previous()
  } else if (ctrlKey && e.key === 'ArrowUp') {
    e.preventDefault()
    playerStore.increaseVolume()
  } else if (ctrlKey && e.key === 'ArrowDown') {
    e.preventDefault()
    if (altKey) playerStore.toggleMute()
    else playerStore.decreaseVolume()
  } else if (ctrlKey && e.key.toLowerCase() === 's') {
    e.preventDefault()
    playerStore.setShuffle(!playerStore.shuffle)
  } else if (ctrlKey && e.key.toLowerCase() === 'r') {
    e.preventDefault()
    playerStore.cycleRepeat()
  } else if (ctrlKey && e.key.toLowerCase() === 'f') {
    e.preventDefault()
    router.push('/search')
  }
}

onMounted(async () => {
  // Wait for router to be ready to ensure route.name is populated
  await router.isReady()

  // If we are in mini-player window but not on the right route, force it
  if (isMiniPlayer.value && route.name !== 'mini-player') {
    await router.replace('/mini-player')
  }

  isRouterReady.value = true

  // Load settings
  await appStore.loadSettings()
  locale.value = appStore.language

  // Handle global events
  offSettings = Events.On('open-settings', () => {
    if (isMiniPlayer.value) {
      WindowService.CloseMiniPlayer()
    } else {
      if (playerStore.playerMode !== 'sticky') {
        playerStore.playerMode = 'sticky'
      }
      router.push('/settings')
    }
  })

  offSearch = Events.On('open-search', () => {
    if (isMiniPlayer.value) {
      WindowService.CloseMiniPlayer()
    } else {
      router.push('/search')
    }
  })

  if (isMiniPlayer.value) return

  playerStore.init()
  deviceStore.init()
  deviceStore.checkFullscreen()
  playlistsStore.loadAll()

  offCycleRepeat = Events.On('player:cycle-repeat', () => {
    playerStore.cycleRepeat()
  })

  window.addEventListener('keydown', handleKeyDown)
})

let offSettings: (() => void) | null = null
let offSearch: (() => void) | null = null
let offCycleRepeat: (() => void) | null = null

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown)
  offSettings?.()
  offSearch?.()
  offCycleRepeat?.()
  playerStore.dispose()
  deviceStore.dispose()
  playlistsStore.dispose()
  appStore.dispose()
})

const updateDynamicColors = (colors: any) => {
  if (!colors) return
  const root = document.documentElement
  const isDark = root.classList.contains('dark')
  
  root.style.setProperty('--dynamic-primary', colors.vibrant)
  root.style.setProperty('--dynamic-surface', hexToRgba(colors.dominant, isDark ? 0.15 : 0.05))
  root.style.setProperty('--dynamic-glow', `0 0 40px ${hexToRgba(colors.vibrant, isDark ? 0.3 : 0.1)}`)
}

watch(
  () => playerStore.theme,
  (colors) => updateDynamicColors(colors),
)

watch(
  () => appStore.theme,
  () => {
    updateDynamicColors(playerStore.theme)
  },
)

watch(
  () => appStore.language,
  (newLang) => {
    locale.value = newLang
  },
)

watch(() => playerStore.playerMode, (newMode) => {
  if (newMode === 'fullscreen') {
    deviceStore.checkFullscreen()
  }
  updateDynamicColors(playerStore.theme)
})
</script>

<template>
  <div v-if="!isRouterReady" class="h-full w-full bg-background" />
  <template v-else>
    <RouterView v-if="isMiniPlayer" />
    <MainLayout v-else />
  </template>
  <FindLyricsDialog />
</template>

<style>
/* Global styles */
html,
body,
#app {
  height: 100%;
  width: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
}

#app {
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
</style>
