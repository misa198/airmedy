<script setup lang="ts">
import { onMounted } from 'vue'
import { usePlayerStore } from '@/stores/player'
import MiniPlayerFloating from '@/components/MiniPlayerFloating.vue'

const playerStore = usePlayerStore()

onMounted(() => {
  playerStore.init()
})
</script>

<template>
  <div class="h-full w-full bg-[#0A0A0A] text-white overflow-hidden dark">
    <MiniPlayerFloating />
  </div>
</template>
