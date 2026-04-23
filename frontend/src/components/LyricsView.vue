<script setup lang="ts">
import { computed } from 'vue'
import PlainLyricsView from './PlainLyricsView.vue'
import SyncedLyricsView from './SyncedLyricsView.vue'

const props = defineProps<{
  lyrics?: string
  isLoading?: boolean
  currentPosition?: number
}>()

const emit = defineEmits<{
  seek: [time: number]
}>()

const LRC_PATTERN = /^\[(\d+):(\d+\.\d+)\]/m

const isSynced = computed(() => !!props.lyrics && LRC_PATTERN.test(props.lyrics))

interface SyncedLine {
  text: string
  time: number
}

const syncedLines = computed<SyncedLine[]>(() => {
  if (!props.lyrics) return []
  const linePattern = /^\[(\d+):(\d+\.\d+)\](.*)/
  return props.lyrics
    .split('\n')
    .flatMap(line => {
      const match = line.match(linePattern)
      if (!match) return []
      const minutes = parseInt(match[1], 10)
      const seconds = parseFloat(match[2])
      return [{ text: match[3].trim(), time: minutes * 60 + seconds }]
    })
})

const plainLines = computed<string[]>(() => {
  if (!props.lyrics) return []
  return props.lyrics.split('\n').filter(l => l.trim())
})
</script>

<template>
  <div class="h-full w-full flex flex-col overflow-hidden">
    <!-- Loading skeleton -->
    <div v-if="isLoading" class="flex-1 overflow-y-auto px-8 py-48">
      <div class="max-w-2xl mx-auto space-y-10">
        <div
          v-for="(width, i) in ['w-3/4', 'w-1/2', 'w-5/6', 'w-2/3', 'w-1/3', 'w-4/5', 'w-1/2', 'w-2/3', 'w-3/4', 'w-1/4']"
          :key="i"
          class="h-8 md:h-12 rounded-lg bg-white/[0.06] animate-pulse"
          :class="width"
        />
      </div>
    </div>

    <!-- Empty state -->
    <div
      v-else-if="!lyrics"
      class="flex-1 flex flex-col items-center justify-center text-white/20 p-12 text-center"
    >
      <p class="text-lg font-medium text-white/40">Lyrics are not available for this track.</p>
      <p class="text-sm text-white/20 mt-2">We're working on bringing lyrics to your collection soon.</p>
    </div>

    <!-- Synced lyrics -->
    <SyncedLyricsView
      v-else-if="isSynced"
      class="flex-1"
      :lines="syncedLines"
      :current-position="currentPosition ?? 0"
      @seek="(time) => emit('seek', time)"
    />

    <!-- Plain lyrics -->
    <PlainLyricsView
      v-else
      class="flex-1"
      :lines="plainLines"
    />
  </div>
</template>
