<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { Events } from '@wailsio/runtime'
import { usePlayerStore } from '@/stores/player'
import { useDeviceStore } from '@/stores/device'
import MiniPlayerFloating from '@/components/MiniPlayerFloating.vue'

const playerStore = usePlayerStore()
const deviceStore = useDeviceStore()

let offWindowShow: (() => void) | null = null

onMounted(() => {
  playerStore.init()
  deviceStore.init()
  offWindowShow = Events.On(Events.Types.Common.WindowShow, () => {
    playerStore.syncState()
  })
})

onUnmounted(() => {
  offWindowShow?.()
  deviceStore.dispose()
})
</script>

<template>
  <div class="h-full w-full bg-[#0A0A0A] text-white overflow-hidden dark">
    <MiniPlayerFloating />
  </div>
</template>
