<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { Mic2, X } from '@lucide/vue'
import { usePlayerStore } from '../stores/player'
import { useI18n } from 'vue-i18n'
import { useLyrics } from '../composables/useLyrics'

const { t } = useI18n()
const store = usePlayerStore()

const lyricsContent = computed(() => store.lyrics?.content)
const { isSynced, syncedLines, plainLines } = useLyrics(lyricsContent)

const activeIndex = computed(() => {
  const pos = store.position
  const idx = [...syncedLines.value].reverse().findIndex(line => line.time <= pos)
  return idx !== -1 ? syncedLines.value.length - 1 - idx : -1
})

const scrollContainer = ref<HTMLElement | null>(null)
const lineRefs = ref<HTMLElement[]>([])
const rootEl = ref<HTMLElement | null>(null)
const isBrowsing = ref(false)
let scrollFrame: number | undefined
let resizeObserver: ResizeObserver | null = null
let waitingForLayout = false
let hasPositionedInitialLine = false
let previousActiveIndex = -1

// Reset stale refs when lines change so indexes stay aligned.
watch(() => syncedLines.value, () => {
  lineRefs.value = []
  isBrowsing.value = false
  hasPositionedInitialLine = false
  previousActiveIndex = -1
})

function isVisible(container: HTMLElement, el: HTMLElement) {
  return el.offsetTop < container.scrollTop + container.clientHeight
    && el.offsetTop + el.clientHeight > container.scrollTop
}

function scrollToActive(index: number, behavior: ScrollBehavior): boolean {
  if (index === -1) return false
  const container = scrollContainer.value
  const el = lineRefs.value[index]
  // The drawer animates in from zero width/height. Wait until it has a real
  // layout; otherwise offset measurements are invalid on first open.
  if (!container || !el || container.clientHeight === 0 || container.clientWidth === 0) return false
  container.scrollTo({
    top: el.offsetTop - container.clientHeight / 2 + el.clientHeight / 2,
    behavior,
  })
  hasPositionedInitialLine = true
  return true
}

function scheduleScrollToActive(index: number, previousIndex = previousActiveIndex) {
  nextTick(() => {
    if (scrollFrame !== undefined) cancelAnimationFrame(scrollFrame)
    scrollFrame = requestAnimationFrame(() => {
      scrollFrame = undefined
      const container = scrollContainer.value
      const previous = lineRefs.value[previousIndex]
      const behavior: ScrollBehavior = hasPositionedInitialLine && container && previous && isVisible(container, previous)
        ? 'smooth'
        : 'auto'
      waitingForLayout = index !== -1 && !scrollToActive(index, behavior)
    })
  })
}

function enterBrowseMode() {
  isBrowsing.value = true
}

function seekAndResume(time: number, index: number) {
  isBrowsing.value = false
  // The listener selected a visible line, so animate that exact line into the
  // active position before the player position update arrives.
  hasPositionedInitialLine = true
  previousActiveIndex = index
  scheduleScrollToActive(index, index)
  store.seek(time)
}

// flush:'post' → DOM patched before measuring offsets.
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
    else if (rootEl.value) resizeObserver.observe(rootEl.value)
  }
})

onUnmounted(() => {
  if (scrollFrame !== undefined) cancelAnimationFrame(scrollFrame)
  resizeObserver?.disconnect()
  resizeObserver = null
})
</script>

<template>
  <div ref="rootEl" class="h-full w-full bg-background flex flex-col">
    <!-- Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-foreground/[0.06] select-none">
      <div class="flex items-center gap-2 font-semibold flex-shrink-0">
        <Mic2 class="w-4 h-4 text-primary" />
        <div class="max-w-[100px] truncate">{{ t('player.lyrics') }}</div>
      </div>

      <button
        class="p-1.5 rounded-full hover:bg-foreground/8 transition-colors text-dim hover:text-foreground flex-shrink-0"
        @click="store.toggleLyrics()">
        <X class="w-4 h-4" />
      </button>
    </div>

    <!-- Body -->
    <div class="flex-1 overflow-hidden">
      <!-- Loading skeleton -->
      <div v-if="store.lyricsLoading" class="px-4 py-8 space-y-4">
        <div v-for="(width, i) in ['w-3/4', 'w-1/2', 'w-5/6', 'w-2/3', 'w-1/3', 'w-4/5', 'w-1/2', 'w-2/3']" :key="i"
          class="h-4 rounded bg-foreground/[0.06] animate-pulse" :class="width" />
      </div>

      <!-- No lyrics -->
      <div v-else-if="!lyricsContent"
        class="h-full flex flex-col items-center justify-center text-muted-foreground gap-3 px-6 text-center">
        <Mic2 class="w-10 h-10 opacity-20" />
        <p class="text-sm">{{ t('player.lyrics_not_available') }}</p>
      </div>

      <!-- Synced view -->
      <div v-else-if="isSynced" ref="scrollContainer"
        class="h-full overflow-y-auto px-4 py-10 scrollbar-hide" @wheel.passive="enterBrowseMode" @pointerdown="enterBrowseMode">
        <div class="space-y-6">
          <div v-for="(line, index) in syncedLines" :key="index" ref="lineRefs"
            class="transition-all duration-150 cursor-pointer select-none leading-snug py-1 origin-left" :class="[
              isBrowsing
                ? 'text-foreground opacity-100'
                : index === activeIndex
                ? 'text-foreground'
                : index < activeIndex
                  ? 'text-foreground/40 opacity-60 hover:text-foreground/50'
                  : 'text-foreground/40 opacity-40 hover:text-foreground/40',
            ]" @pointerdown.stop @click="seekAndResume(line.time, index)">
            <div class="text-[21pt] font-bold">{{ line.text }}</div>
            <div v-if="line.secondary" class="text-[15pt] opacity-50 mt-0.5">{{ line.secondary }}</div>
          </div>
        </div>
      </div>

      <!-- Plain view -->
      <div v-else class="h-full overflow-y-auto px-4 py-6 scrollbar-hide">
        <div class="space-y-3.5">
          <div v-for="(line, index) in plainLines" :key="index" class="leading-relaxed select-text">
            <p class="text-sm text-foreground/80">{{ line.primary }}</p>
            <p v-if="line.secondary" class="text-xs text-foreground/40 mt-0.5">{{ line.secondary }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
</style>
