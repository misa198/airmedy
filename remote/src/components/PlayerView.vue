<script setup lang="ts">
import NowPlaying from './NowPlaying.vue'
import PlayerControls from './PlayerControls.vue'

defineProps<{
  showQueue: boolean
}>()
</script>

<template>
  <div
    class="h-full flex flex-col items-center justify-center lg:transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)] overflow-auto"
    :class="!showQueue ? 'w-full lg:max-w-lg' : 'hidden lg:flex lg:w-1/2 lg:max-w-md'">
    <div class="flex flex-col items-center justify-center gap-8 w-full">
      <NowPlaying :show-queue="showQueue" />
      <PlayerControls />
    </div>
  </div>
</template>
