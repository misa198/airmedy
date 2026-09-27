<script setup lang="ts">
import type { LyricLine, LyricWord } from '../composables/useLyrics'

defineProps<{
  line: LyricLine
  position?: number
}>()

function progress(word: LyricWord, position: number) {
  if (word.end === word.start) return position >= word.start ? 100 : 0
  return Math.max(0, Math.min(100, (position - word.start) / (word.end - word.start) * 100))
}
</script>

<template>
  <span v-if="line.words" class="karaoke-text"><span
    v-for="(word, index) in line.words" :key="index" class="karaoke-segment"
    :class="{ 'karaoke-word': position !== undefined }"
    :style="{ '--word-progress': `${position === undefined ? 100 : progress(word, position)}%` }"
  >{{ word.text }}</span></span>
  <span v-else>{{ line.text }}</span>
</template>

<style scoped>
@property --karaoke-unsung-opacity {
  syntax: '<number>';
  inherits: false;
  initial-value: 1;
}

.karaoke-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.karaoke-segment {
  --karaoke-unsung-opacity: 1;
  /* Inline fragments share one gradient, so a timestamped phrase can wrap. */
  /* Inherit the row's fading color instead of snapping to the theme foreground. */
  background-image: linear-gradient(to right,
    currentColor 0%, currentColor var(--word-progress),
    color-mix(in srgb, currentColor calc(var(--karaoke-unsung-opacity) * 100%), transparent) var(--word-progress),
    color-mix(in srgb, currentColor calc(var(--karaoke-unsung-opacity) * 100%), transparent) 100%);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  transition: --karaoke-unsung-opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.karaoke-word {
  --karaoke-unsung-opacity: 0.35;
}

@media (prefers-reduced-motion: reduce) {
  .karaoke-segment {
    transition: none;
  }
}
</style>
