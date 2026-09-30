<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import type { LyricLine } from '../composables/useLyrics'
import { useLyricsScrollMotion } from '../composables/useLyricsScrollMotion'
import KaraokeText from './KaraokeText.vue'
import { useGpuLyrics } from '../composables/useGpuLyrics'
import { lyricAppearance } from '../lib/lyricsGpuLayout'

const props = defineProps<{
  secondary?: (string | undefined)[]
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
const isBrowsing = ref(false)
const canvas = ref<HTMLCanvasElement | null>(null)
const hovered = ref(-1)
const { ready: gpuReady, draw, layout } = useGpuLyrics(canvas, scrollContainer, lineRefs, computed(() => ({
  lines: props.lines, active: activeIndex.value, browsing: isBrowsing.value,
  immersive: !!props.immersive, hovered: hovered.value, position: props.currentPosition, reducedMotion: false,
})))

function domAppearance(index: number) {
  if (gpuReady.value) return { opacity: 0 }
  const appearance = lyricAppearance(index, activeIndex.value, isBrowsing.value, !!props.immersive, hovered.value)
  return { opacity: appearance.alpha, filter: appearance.blur ? `blur(${appearance.blur}px)` : undefined }
}
let scrollFrame: number | undefined
let resizeObserver: ResizeObserver | null = null
let disposed = false
let hasPositionedInitialLine = false
let previousActiveIndex = -1
const { scrollTo, stop: stopScrollAnimation } = useLyricsScrollMotion()

// Reset stale refs when the track's lines change so indexes stay aligned.
watch(() => props.lines, () => {
  lineRefs.value = []
  isBrowsing.value = false
  hasPositionedInitialLine = false
  previousActiveIndex = -1
})

watch(() => props.secondary, () => {
  layout()
  if (!isBrowsing.value) scheduleScrollToActive(activeIndex.value)
}, { flush: 'post' })

watch([() => props.lines, () => props.immersive], () => nextTick(layout), { flush: 'post' })

function isVisible(container: HTMLElement, el: HTMLElement) {
  return el.offsetTop < container.scrollTop + container.clientHeight
    && el.offsetTop + el.clientHeight > container.scrollTop
}

function scrollToActive(index: number, animated: boolean) {
  if (index === -1) return false
  const container = scrollContainer.value
  const el = lineRefs.value[index]
  // The fullscreen right column animates from zero width. Wait until it has a
  // real layout; otherwise offset measurements are invalid on first open.
  if (!container || !el || container.clientHeight === 0 || container.clientWidth === 0) return false
  const activeLineViewportPosition = props.immersive ? 0.32 : 0.5
  scrollTo(container, el.offsetTop - container.clientHeight * activeLineViewportPosition + el.clientHeight / 2, animated)
  hasPositionedInitialLine = true
  return true
}

function scheduleScrollToActive(index: number, previousIndex = previousActiveIndex) {
  stopScrollAnimation()
  nextTick(() => {
    if (disposed || isBrowsing.value) return
    if (scrollFrame !== undefined) cancelAnimationFrame(scrollFrame)
    // A watcher with `immediate` runs before mount, when the container and
    // line refs do not exist yet. Defer to the first painted frame so opening
    // the lyrics panel immediately centers its already-active line.
    scrollFrame = requestAnimationFrame(() => {
      scrollFrame = undefined
      const container = scrollContainer.value
      const previous = lineRefs.value[previousIndex]
      const animated = !!(hasPositionedInitialLine && container && previous && isVisible(container, previous))
      scrollToActive(index, animated)
    })
  })
}

function enterBrowseMode() {
  isBrowsing.value = true
  stopScrollAnimation()
  if (scrollFrame !== undefined) cancelAnimationFrame(scrollFrame)
}

function seekAndResume(time: number, index: number) {
  isBrowsing.value = false
  // The tapped line is necessarily visible. Use it as the scroll reference so
  // it glides into the active slot immediately, instead of waiting for the
  // playback position update and potentially snapping there.
  hasPositionedInitialLine = true
  previousActiveIndex = index
  scheduleScrollToActive(index, index)
  emit('seek', time)
}

// flush:'post' → DOM patched before measuring offsets. The mounted hook is
// required because the immediate watcher runs before template refs exist.
watch(activeIndex, (newIndex) => {
  const previousIndex = previousActiveIndex
  if (!isBrowsing.value) scheduleScrollToActive(newIndex, previousIndex)
  previousActiveIndex = newIndex
}, { flush: 'post', immediate: true })

onMounted(() => {
  scheduleScrollToActive(activeIndex.value)
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      layout()
      if (!isBrowsing.value) scheduleScrollToActive(activeIndex.value)
    })
    if (scrollContainer.value) resizeObserver.observe(scrollContainer.value)
  }
})

onUnmounted(() => {
  disposed = true
  if (scrollFrame !== undefined) cancelAnimationFrame(scrollFrame)
  stopScrollAnimation()
  resizeObserver?.disconnect()
  resizeObserver = null
})
</script>

<template>
  <div class="relative h-full min-h-0 text-foreground">
  <div ref="scrollContainer" class="h-full overflow-y-auto py-48 scrollbar-hide text-foreground [overflow-anchor:none]" :class="props.immersive ? 'pl-8 pr-16' : 'px-8'" @scroll.passive="draw" @wheel.passive="enterBrowseMode" @pointerdown="enterBrowseMode">
    <div class="max-w-2xl mx-auto space-y-5">
      <div
        v-for="(line, index) in lines"
        :key="index"
        ref="lineRefs"
        data-test="lyric-line"
        role="button"
        tabindex="0"
        :aria-current="index === activeIndex ? 'true' : undefined"
        class="font-bold cursor-pointer select-none focus-visible:outline-none focus-visible:underline focus-visible:decoration-foreground/60 focus-visible:underline-offset-4"
        @mouseenter="hovered = index"
        @mouseleave="hovered = -1"
        @keydown.enter.prevent="seekAndResume(line.time, index)"
        @pointerdown.stop
        @click="seekAndResume(line.time, index)"
      >
        <div
          data-test="lyric-content"
          class="py-2"
          :class="props.immersive ? 'text-[40px]' : 'text-4xl'"
          :style="domAppearance(index)"
        >
          <div><KaraokeText :line="line" :position="!gpuReady && !isBrowsing && index === activeIndex ? currentPosition : undefined" /></div>
          <div v-if="secondary?.[index] || line.secondary" data-lyric-secondary class="text-lg md:text-2xl font-bold mt-1 opacity-80">{{ secondary?.[index] || line.secondary }}</div>
        </div>
      </div>
    </div>
  </div>
  <canvas ref="canvas" aria-hidden="true" class="absolute inset-0 pointer-events-none" :style="{ visibility: gpuReady ? 'visible' : 'hidden' }" />
  </div>
</template>
