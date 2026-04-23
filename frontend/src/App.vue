<script setup lang="ts">
import { onMounted, watch } from 'vue'
import MainLayout from './layouts/MainLayout.vue'
import { hexToRgba } from './lib/utils'
import { usePlayerStore } from './stores/player'
import { useDeviceStore } from './stores/device'
import { usePlaylistsStore } from './stores/playlists'

const playerStore = usePlayerStore()
const deviceStore = useDeviceStore()
const playlistsStore = usePlaylistsStore()

onMounted(() => {
  playerStore.init()
  deviceStore.init()
  deviceStore.checkFullscreen()
  playlistsStore.loadAll()
})

watch(
  () => playerStore.theme,
  (colors) => {
    if (!colors) return
    const root = document.documentElement
    root.style.setProperty('--dynamic-primary', colors.vibrant)
    root.style.setProperty('--dynamic-surface', hexToRgba(colors.dominant, 0.15))
    root.style.setProperty('--dynamic-glow', `0 0 40px ${hexToRgba(colors.vibrant, 0.3)}`)
  },
)

// Re-check when the player mode changes to fullscreen
watch(() => playerStore.playerMode, (newMode) => {
  if (newMode === 'fullscreen') {
    deviceStore.checkFullscreen()
  }
})
</script>

<template>
  <MainLayout />
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
