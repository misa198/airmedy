<script setup lang="ts">
import { computed, toRef } from 'vue'
import { useRomanization } from '../composables/useRomanization'
import PlainLyricsView from './PlainLyricsView.vue'
import RomanizationToggle from './RomanizationToggle.vue'
import SyncedLyricsView from './SyncedLyricsView.vue'
import { useLyrics } from '../composables/useLyrics'

const props = defineProps<{
  lyrics?: string
  isLoading?: boolean
  currentPosition?: number
  immersive?: boolean
  romanization?: boolean
}>()

const emit = defineEmits<{
  seek: [time: number]
}>()

const { isSynced, syncedLines, plainLines } = useLyrics(toRef(props, 'lyrics'))
const mainLines = computed(() => isSynced.value ? syncedLines.value.map(line => line.text) : plainLines.value.map(line => line.primary))
const { supported, mandarinDefault, loading, error, enabled, secondary, toggle, retry } = useRomanization(
  mainLines, computed(() => !!props.romanization && !props.isLoading),
)
</script>

<template>
  <div class="h-full w-full flex flex-col overflow-hidden">
    <!-- Loading skeleton -->
    <div v-if="isLoading" class="flex-1 overflow-hidden px-8 py-14">
      <div class="max-w-2xl mx-auto space-y-10">
        <div
          v-for="(width, i) in ['w-3/4', 'w-1/2', 'w-5/6', 'w-2/3', 'w-1/3', 'w-4/5', 'w-1/2', 'w-2/3', 'w-3/4']"
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
      <p class="text-lg font-medium text-white/40">{{ $t('player.lyrics_not_available') }}</p>
      <p class="text-sm text-white/20 mt-2">{{ $t('player.lyrics_coming_soon') }}</p>
    </div>

    <!-- Synced lyrics -->
    <SyncedLyricsView
      v-else-if="isSynced"
      class="flex-1 min-h-0"
      :lines="syncedLines"
      :secondary="secondary"
      :current-position="currentPosition ?? 0"
      :immersive="immersive"
      @seek="(time) => emit('seek', time)"
    />

    <!-- Plain lyrics -->
    <PlainLyricsView
      v-else
      class="flex-1 min-h-0"
      :lines="plainLines"
      :secondary="secondary"
    />
    <Teleport v-if="romanization && supported" to="#fullscreen-lyrics-actions">
      <RomanizationToggle
        :enabled="enabled" :loading="loading" :error="error" :mandarin-default="mandarinDefault"
        @activate="error && enabled ? retry() : toggle()"
      />
    </Teleport>
  </div>
</template>
