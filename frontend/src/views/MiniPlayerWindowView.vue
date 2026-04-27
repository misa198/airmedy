<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { Events } from '@wailsio/runtime'
import { usePlayerStore } from '@/stores/player'
import MiniPlayerFloating from '@/components/MiniPlayerFloating.vue'

const playerStore = usePlayerStore()

let offWindowShow: (() => void) | null = null

onMounted(() => {
  playerStore.init()
  offWindowShow = Events.On(Events.Types.Common.WindowShow, () => {
    playerStore.syncState()
  })
})

onUnmounted(() => {
  offWindowShow?.()
})
</script>

<template>
  <div class="h-full w-full bg-[#0A0A0A] text-white overflow-hidden dark">
    <MiniPlayerFloating />
  </div>
</template>
