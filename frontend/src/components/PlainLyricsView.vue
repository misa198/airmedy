<script setup lang="ts">
import type { PlainLine } from '../composables/useLyrics'

defineProps<{
  lines: PlainLine[]
}>()
</script>

<template>
  <div class="h-full overflow-y-auto px-8 py-16 scrollbar-hide">
    <div class="max-w-2xl mx-auto space-y-4">
      <div
        v-for="(line, index) in lines"
        :key="index"
        class="leading-relaxed select-text"
      >
        <p class="text-lg md:text-xl font-medium text-white/70">{{ line.primary }}</p>
        <p v-if="line.secondary" class="text-base md:text-lg font-medium text-white/40 mt-0.5">{{ line.secondary }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
</style>
