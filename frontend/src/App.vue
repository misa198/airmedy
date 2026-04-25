<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MainLayout from './layouts/MainLayout.vue'
import { hexToRgba } from './lib/utils'
import { usePlayerStore } from './stores/player'
import { useDeviceStore } from './stores/device'
import { usePlaylistsStore } from './stores/playlists'
import { useAppStore } from './stores/app'
import { useI18n } from 'vue-i18n'
import { Events } from '@wailsio/runtime'

const route = useRoute()
const router = useRouter()
const { locale } = useI18n()
const playerStore = usePlayerStore()
const deviceStore = useDeviceStore()
const playlistsStore = usePlaylistsStore()
const appStore = useAppStore()

onMounted(async () => {
  // Load settings
  await appStore.loadSettings()
  locale.value = appStore.language

  if (route.name === 'mini-player') return
  playerStore.init()
  deviceStore.init()
  deviceStore.checkFullscreen()
  playlistsStore.loadAll()

  // Handle global events
  Events.On('open-settings', () => {
    router.push('/settings')
  })
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
  <RouterView v-if="route.name === 'mini-player'" />
  <MainLayout v-else />
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

/* Custom scrollbar styling */
::-webkit-scrollbar {
  width: 8px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: rgba(161, 161, 170, 0.3);
  border-radius: 9999px;
}

::-webkit-scrollbar-thumb:hover {
  background: rgba(161, 161, 170, 0.5);
}
</style>
