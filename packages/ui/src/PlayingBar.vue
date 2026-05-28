<template>
  <div class="flex items-end gap-[2px] h-3 w-[10px]">
    <div v-for="i in 3" :key="i" :ref="(el) => setBarRef(el as HTMLElement | null, i - 1)"
      class="w-[2px] h-full bg-primary origin-bottom" :class="isPlaying && !stopping ? `bar-${i}` : ''" :style="{
        transform: !isPlaying || stopping ? pauseTransforms[i - 1] : undefined,
        transition: stopping ? 'transform 0.4s ease-in-out' : 'none',
      }" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

const props = defineProps<{ isPlaying: boolean }>()

const pauseTransformsValues = ['scaleY(0.3)', 'scaleY(0.3)', 'scaleY(0.3)']
const barEls: (HTMLElement | null)[] = [null, null, null]
const stopping = ref(false)
const pauseTransforms = ref<string[]>(
  props.isPlaying ? ['', '', ''] : pauseTransformsValues,
)

function setBarRef(el: HTMLElement | null, i: number) {
  barEls[i] = el
}

watch(() => props.isPlaying, (playing) => {
  if (!playing) {
    // Capture mid-animation transform values before removing animation
    pauseTransforms.value = barEls.map(el =>
      el ? getComputedStyle(el).transform : 'scaleY(1)',
    )
    stopping.value = true
    // Next frame: animation removed, bars at captured positions → transition to bottom
    requestAnimationFrame(() => {
      pauseTransforms.value = pauseTransformsValues
    })
  } else {
    stopping.value = false
    pauseTransforms.value = ['', '', '']
  }
})
</script>

<style scoped>
@keyframes playing-bar-1 {

  0%,
  100% {
    transform: scaleY(0.3);
  }

  50% {
    transform: scaleY(0.8);
  }
}

@keyframes playing-bar-2 {

  0%,
  100% {
    transform: scaleY(1.0);
  }

  50% {
    transform: scaleY(0.4);
  }
}

@keyframes playing-bar-3 {

  0%,
  100% {
    transform: scaleY(0.6);
  }

  50% {
    transform: scaleY(0.9);
  }
}

.bar-1 {
  animation: playing-bar-1 0.8s ease-in-out infinite;
}

.bar-2 {
  animation: playing-bar-2 0.6s ease-in-out infinite;
}

.bar-3 {
  animation: playing-bar-3 0.7s ease-in-out infinite;
}
</style>
