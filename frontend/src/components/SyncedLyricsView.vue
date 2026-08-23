<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import type { LyricLine } from '../composables/useLyrics'
import { useLyricsScrollMotion } from '../composables/useLyricsScrollMotion'

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
const isBrowsing = ref(false)
let scrollFrame: number | undefined
let resizeObserver: ResizeObserver | null = null
let waitingForLayout = false
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
    if (scrollFrame !== undefined) cancelAnimationFrame(scrollFrame)
    // A watcher with `immediate` runs before mount, when the container and
    // line refs do not exist yet. Defer to the first painted frame so opening
    // the lyrics panel immediately centers its already-active line.
    scrollFrame = requestAnimationFrame(() => {
      scrollFrame = undefined
      const container = scrollContainer.value
      const previous = lineRefs.value[previousIndex]
      const animated = !!(hasPositionedInitialLine && container && previous && isVisible(container, previous))
      waitingForLayout = index !== -1 && !scrollToActive(index, animated)
    })
  })
}

function enterBrowseMode() {
  isBrowsing.value = true
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

function immersiveLineStyle(index: number) {
  if (isBrowsing.value) return { filter: 'blur(0)', opacity: '1' }
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

function lineClasses(index: number) {
  if (isBrowsing.value) {
    return [
      'text-white blur-none opacity-100',
      { 'text-4xl': !props.immersive, 'text-[40px]': props.immersive },
    ]
  }
  const isActive = index === activeIndex.value
  const isNearActive = activeIndex.value !== -1 && Math.abs(index - activeIndex.value) <= 2

  return [
    props.immersive
      ? isActive
        ? 'text-white scale-[1.06]'
        : 'text-white'
      : isActive
        ? 'text-white scale-[1.03] blur-none opacity-100'
        : index < activeIndex.value
          ? 'text-white/20 blur-[0.5px] opacity-60 hover:text-white/40'
          : 'text-white/30 blur-[1px] opacity-40 hover:text-white/60 hover:blur-none',
    {
      'text-4xl': !props.immersive,
      'text-[40px]': props.immersive,
      'transform-gpu will-change-transform': isNearActive,
    },
  ]
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
      if (waitingForLayout && !isBrowsing.value) scheduleScrollToActive(activeIndex.value)
    })
    if (scrollContainer.value) resizeObserver.observe(scrollContainer.value)
  }
})

onUnmounted(() => {
  if (scrollFrame !== undefined) cancelAnimationFrame(scrollFrame)
  stopScrollAnimation()
  resizeObserver?.disconnect()
  resizeObserver = null
})
</script>

<template>
  <div ref="scrollContainer" class="h-full overflow-y-auto py-48 scrollbar-hide" :class="props.immersive ? 'pl-8 pr-16' : 'px-8'" @wheel.passive="enterBrowseMode" @pointerdown="enterBrowseMode">
    <div class="max-w-2xl mx-auto space-y-5">
      <div
        v-for="(line, index) in lines"
        :key="index"
        ref="lineRefs"
        data-test="lyric-line"
        class="font-bold transition-[filter,opacity,transform,scale] duration-300 ease-[cubic-bezier(0.4,0,0.2,1)] cursor-pointer select-none origin-left py-2"
        :class="lineClasses(index)"
        :style="props.immersive ? immersiveLineStyle(index) : undefined"
        @pointerdown.stop
        @click="seekAndResume(line.time, index)"
      >
        <div>{{ line.text }}</div>
        <div v-if="line.secondary" class="text-lg md:text-2xl font-bold mt-1 opacity-80">{{ line.secondary }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
</style>
