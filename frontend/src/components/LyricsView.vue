<script setup lang="ts">
import { Mic2 } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

interface LyricLine {
  text: string
  time?: number
}

const props = defineProps<{
  lyrics?: string | LyricLine[]
  isLoading?: boolean
  currentPosition?: number
}>()

const emit = defineEmits<{
  'seek': [time: number]
}>()

const displayLyrics = computed<LyricLine[]>(() => {
  if (typeof props.lyrics === 'string') {
    return parseLyrics(props.lyrics)
  }
  return (props.lyrics ?? []) as LyricLine[]
})

// Parse LRC-format or plain text into LyricLine[].
function parseLyrics(raw: string): LyricLine[] {
  const lrcPattern = /^\[(\d+):(\d+\.\d+)\](.*)/
  const lines = raw.split('\n')
  const result: LyricLine[] = []
  for (const line of lines) {
    const match = line.match(lrcPattern)
    if (match) {
      const minutes = parseInt(match[1], 10)
      const seconds = parseFloat(match[2])
      result.push({ text: match[3].trim(), time: minutes * 60 + seconds })
    } else if (line.trim()) {
      result.push({ text: line.trim() })
    }
  }
  return result
}

const activeIndex = computed(() => {
  if (props.currentPosition !== undefined) {
    const idx = [...displayLyrics.value].reverse().findIndex(line => line.time !== undefined && line.time <= props.currentPosition!)
    if (idx !== -1) return displayLyrics.value.length - 1 - idx
  }
  return -1
})

const scrollContainer = ref<HTMLElement | null>(null)
const lineRefs = ref<HTMLElement[]>([])

// Auto-scroll to active line
watch(activeIndex, (newIndex) => {
  if (newIndex !== -1 && lineRefs.value[newIndex] && scrollContainer.value) {
    const container = scrollContainer.value
    const element = lineRefs.value[newIndex]

    const containerHeight = container.clientHeight
    const elementTop = element.offsetTop
    const elementHeight = element.clientHeight

    const targetScroll = elementTop - (containerHeight / 2) + (elementHeight / 2)

    container.scrollTo({
      top: targetScroll,
      behavior: 'smooth'
    })
  }
})

const handleLineClick = (line: LyricLine) => {
  if (line.time !== undefined) {
    emit('seek', line.time)
  }
}

</script>

<template>
  <div class="h-full w-full flex flex-col overflow-hidden relative group">
    <div v-if="isLoading" class="flex-1 flex items-center justify-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
    </div>

    <div v-else-if="displayLyrics.length === 0"
      class="flex-1 flex flex-col items-center justify-center text-white/20 p-12 text-center">
      <Mic2 class="w-12 h-12 mb-4 opacity-10" />
      <p class="text-lg font-medium text-white/40">Lyrics are not available for this track.</p>
      <p class="text-sm text-white/20 mt-2">We're working on bringing lyrics to your collection soon.</p>
    </div>

    <div ref="scrollContainer" class="flex-1 overflow-y-auto px-8 py-48 scrollbar-hide scroll-smooth">
      <div class="max-w-2xl mx-auto space-y-10">
        <div v-for="(line, index) in displayLyrics" :key="index" ref="lineRefs" @click="handleLineClick(line)"
          class="text-2xl md:text-4xl font-extrabold transition-all duration-100 cursor-pointer select-none origin-left py-2"
          :class="[
            index === activeIndex
              ? 'text-white scale-105 blur-none opacity-100'
              : index < activeIndex
                ? 'text-white/20 blur-[0.5px] opacity-60 hover:text-white/40'
                : 'text-white/30 blur-[1px] opacity-40 hover:text-white/60 hover:blur-none'
          ]">
          {{ line.text }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.scrollbar-hide::-webkit-scrollbar {
  display: none;
}

.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
