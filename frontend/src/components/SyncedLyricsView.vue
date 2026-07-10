<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { LyricLine } from '../composables/useLyrics'

const props = defineProps<{
  lines: LyricLine[]
  currentPosition: number
  immersive?: boolean
}>()

const emit = defineEmits<{
  seek: [time: number]
}>()

const activeIndex = computed(() => {
  const idx = [...props.lines].reverse().findIndex(
    line => line.time <= props.currentPosition
  )
  return idx !== -1 ? props.lines.length - 1 - idx : -1
})

const scrollContainer = ref<HTMLElement | null>(null)
const lineRefs = ref<HTMLElement[]>([])

// Reset stale refs when the track's lines change so indexes stay aligned.
watch(() => props.lines, () => {
  lineRefs.value = []
})

function scrollToActive(index: number) {
  if (index === -1) return
  const container = scrollContainer.value
  const el = lineRefs.value[index]
  // Container may be hidden (clientHeight 0) or refs not laid out yet.
  if (!container || !el || container.clientHeight === 0) return
  const activeLineViewportPosition = props.immersive ? 0.32 : 0.5
  container.scrollTo({
    top: el.offsetTop - container.clientHeight * activeLineViewportPosition + el.clientHeight / 2,
    behavior: 'smooth',
  })
}

function immersiveLineStyle(index: number) {
  const distance = Math.abs(index - activeIndex.value)
  if (distance === 0) return { filter: 'blur(0)', opacity: '1' }

  // Keep the lines beside the current lyric legible. Far lines fade away more
  // than they blur, avoiding the visually noisy, out-of-focus wall of text.
  const blurByDistance = [0, 0.35, 1.25, 2]
  const opacityByDistance = [1, 0.25, 0.15, 0.1]
  const level = Math.min(distance, blurByDistance.length - 1)

  return {
    filter: `blur(${blurByDistance[level]}px)`,
    opacity: String(opacityByDistance[level]),
  }
}

// flush:'post' → DOM patched + layout settled before measuring offsets.
// immediate + nextTick handles first paint and the lyrics-just-loaded race
// where the active line exists before its ref is populated.
watch(activeIndex, (newIndex) => {
  nextTick(() => scrollToActive(newIndex))
}, { flush: 'post', immediate: true })
</script>

<template>
  <div ref="scrollContainer" class="h-full overflow-y-auto px-8 py-48 scrollbar-hide scroll-smooth">
    <div class="max-w-2xl mx-auto space-y-10">
      <div
        v-for="(line, index) in lines"
        :key="index"
        ref="lineRefs"
        data-test="lyric-line"
        class="font-bold transition-[filter,opacity,transform] duration-300 ease-[cubic-bezier(0.4,0,0.2,1)] cursor-pointer select-none origin-left py-2"
        :class="[
          props.immersive
            ? index === activeIndex
              ? 'text-white scale-105'
              : 'text-white'
            : index === activeIndex
            ? 'text-white scale-105 blur-none opacity-100'
            : index < activeIndex
              ? 'text-white/20 blur-[0.5px] opacity-60 hover:text-white/40'
              : 'text-white/30 blur-[1px] opacity-40 hover:text-white/60 hover:blur-none',
              {
                'text-4xl': !props.immersive,
                'text-[44px]': props.immersive,
              }
        ]"
        :style="props.immersive ? immersiveLineStyle(index) : undefined"
        @click="emit('seek', line.time)"
      >
        <div>{{ line.text }}</div>
        <div v-if="line.secondary" class="text-lg md:text-2xl font-bold mt-1 opacity-80">{{ line.secondary }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
</style>
