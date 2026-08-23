<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, toRef, watch } from 'vue'
import { useLyrics } from '@/composables/useLyrics'
import { lyricsMotionClasses, useLyricsScrollMotion } from '@/composables/useLyricsScrollMotion'

const props = defineProps<{
  lyrics?: string
  loading?: boolean
  currentPosition: number
}>()

const emit = defineEmits<{
  seek: [time: number]
}>()

const { isSynced, syncedLines, plainLines } = useLyrics(toRef(props, 'lyrics'))
const activeIndex = computed(() => {
  for (let index = syncedLines.value.length - 1; index >= 0; index--) {
    if (syncedLines.value[index].time <= props.currentPosition) return index
  }
  return -1
})

const scrollContainer = ref<HTMLElement | null>(null)
const lineRefs = ref<HTMLElement[]>([])
const rootEl = ref<HTMLElement | null>(null)
const isBrowsing = ref(false)
let scrollFrame: number | undefined
let resizeObserver: ResizeObserver | null = null
let waitingForLayout = false
let hasPositionedLine = false
let previousActiveIndex = -1
const { scrollTo, stop: stopScrollAnimation } = useLyricsScrollMotion()

function scrollToActive(index: number, animated: boolean) {
  if (index === -1) return false
  const container = scrollContainer.value
  const line = lineRefs.value[index]
  if (!container || !line || container.clientHeight === 0 || container.clientWidth === 0) return false

  // Mini player lines wrap frequently. Measure relative to this scroll panel:
  // offsetTop can otherwise be relative to the nested lyric list.
  const lineTop = line.getBoundingClientRect().top - container.getBoundingClientRect().top + container.scrollTop
  const top = lineTop - container.clientHeight / 4 + line.clientHeight / 2
  scrollTo(container, top, animated)
  hasPositionedLine = true
  return true
}

function scheduleScrollToActive(index: number, previousIndex = previousActiveIndex) {
  stopScrollAnimation()
  nextTick(() => {
    if (scrollFrame !== undefined) cancelAnimationFrame(scrollFrame)
    scrollFrame = requestAnimationFrame(() => {
      scrollFrame = undefined
      const animated = hasPositionedLine && previousIndex !== -1
      waitingForLayout = !scrollToActive(index, animated)
    })
  })
}

function enterBrowseMode() {
  isBrowsing.value = true
}

function seekAndResume(time: number, index: number) {
  isBrowsing.value = false
  hasPositionedLine = true
  previousActiveIndex = index
  scheduleScrollToActive(index, index)
  emit('seek', time)
}

watch(syncedLines, () => {
  lineRefs.value = []
  isBrowsing.value = false
  hasPositionedLine = false
  previousActiveIndex = -1
})

watch(activeIndex, (index) => {
  const previousIndex = previousActiveIndex
  if (!isBrowsing.value) scheduleScrollToActive(index, previousIndex)
  previousActiveIndex = index
}, { flush: 'post', immediate: true })

onMounted(() => {
  scheduleScrollToActive(activeIndex.value)
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      if (waitingForLayout && !isBrowsing.value) scheduleScrollToActive(activeIndex.value)
    })
    if (scrollContainer.value) resizeObserver.observe(scrollContainer.value)
    else if (rootEl.value) resizeObserver.observe(rootEl.value)
  }
})

onUnmounted(() => {
  if (scrollFrame !== undefined) cancelAnimationFrame(scrollFrame)
  stopScrollAnimation()
  resizeObserver?.disconnect()
})
</script>

<template>
  <div ref="rootEl" class="h-full w-full overflow-hidden">
    <div v-if="loading" data-test="mini-lyrics-loading" class="h-full space-y-3 px-4 py-5">
      <div v-for="width in ['w-3/4', 'w-5/6', 'w-1/2', 'w-2/3']" :key="width"
        class="h-4 rounded bg-foreground/[0.04] animate-pulse" :class="width" />
    </div>

    <div v-else-if="!lyrics" data-test="mini-lyrics-empty"
      class="h-full flex items-center justify-center px-6 text-center text-sm text-[color:var(--text-muted)]">
      {{ $t('player.lyrics_not_available') }}
    </div>

    <div v-else-if="isSynced" ref="scrollContainer" data-test="mini-synced-lyrics"
      class="h-full overflow-y-auto px-4 pt-6 scrollbar-hide" @wheel.passive="enterBrowseMode"
      @pointerdown="enterBrowseMode">
      <div class="space-y-5">
        <button v-for="(line, index) in syncedLines" :key="`${line.time}-${index}`" ref="lineRefs" type="button"
          data-test="mini-lyric-line"
          class="block w-full origin-left text-left leading-snug text-[20px]"
          :class="[
            lyricsMotionClasses,
            !isBrowsing && (index === activeIndex || (activeIndex > 0 && index === activeIndex - 1) || (index === activeIndex + 1)) ? 'transform-gpu' : '',
            isBrowsing
              ? 'text-foreground opacity-100'
              : index === activeIndex
                ? 'text-foreground opacity-100'
                : index < activeIndex
                  ? 'text-foreground/40 opacity-60 hover:text-foreground/50'
                  : 'text-foreground/40 opacity-50 hover:text-foreground/50',
          ]"
          @pointerdown.stop
          @click="seekAndResume(line.time, index)">
          <span class="font-bold">{{ line.text }}</span>
          <span v-if="line.secondary" class="mt-0.5 block text-[14px] opacity-70">{{ line.secondary }}</span>
        </button>
      </div>
    </div>

    <div v-else data-test="mini-plain-lyrics" class="h-full overflow-y-auto px-4 py-6 scrollbar-hide">
      <div class="space-y-2 text-sm leading-relaxed">
        <div v-for="(line, index) in plainLines" :key="index" data-test="mini-plain-lyric-line"
          class="text-foreground">
          <p>{{ line.primary }}</p>
          <p v-if="line.secondary" class="mt-0.5 text-xs text-[color:var(--text-muted)]">{{ line.secondary }}</p>
        </div>
      </div>
    </div>
  </div>
</template>
