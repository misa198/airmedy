<script setup lang="ts">
import LyricsView from '../PlayerLyrics.vue'

defineProps<{
  lyrics?: string
  loading: boolean
  position: number
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'seek', time: number): void
}>()
</script>

<template>
  <div class="absolute left-0 top-[2%] bottom-[8%] flex flex-col overflow-hidden w-[50cqw] max-w-2xl">
    <LyricsView
      :lyrics="lyrics"
      :is-loading="loading"
      :current-position="position"
      immersive
      class="dark flex-1"
      @seek="(time) => emit('seek', time)"
    />
  </div>
</template>
