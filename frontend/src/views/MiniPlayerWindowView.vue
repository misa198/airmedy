<script setup lang="ts">
import { onMounted } from 'vue'
import { Events } from '@wailsio/runtime'
import { usePlayerStore } from '@/stores/player'
import MiniPlayerFloating from '@/components/MiniPlayerFloating.vue'

const playerStore = usePlayerStore()

onMounted(() => {
  playerStore.init()

  // Re-sync state whenever the mini player window is shown
  // This fixes stale data caused by the window being suspended while hidden
  Events.On(Events.Types.Common.WindowShow, () => {
    playerStore.syncState()
  })
})
</script>

<template>
  <div class="h-full w-full bg-[#0A0A0A] text-white overflow-hidden dark">
    <MiniPlayerFloating />
  </div>
</template>
