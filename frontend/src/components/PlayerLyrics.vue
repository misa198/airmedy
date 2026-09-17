<script setup lang="ts">
import { computed, toRef } from 'vue'
import { LoaderCircle, Languages, RotateCcw } from '@lucide/vue'
import { useRomanization } from '../composables/useRomanization'
import PlainLyricsView from './PlainLyricsView.vue'
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
      <button
        type="button"
        data-test="romanization-toggle"
        :aria-pressed="enabled"
        :aria-busy="loading"
        :aria-label="$t(error && enabled ? 'player.romanization_retry' : loading ? 'player.romanization_loading' : 'player.romanization')"
        :title="[$t(error && enabled ? 'player.romanization_retry' : 'player.romanization'), $t(mandarinDefault ? 'player.romanization_mandarin' : 'player.romanization_hint')].join(' — ')"
        class="relative isolate bg-foreground/[0.05] h-10 w-10 shrink-0 flex items-center justify-center rounded-full border border-foreground/[0.08] backdrop-blur-md focus-visible:outline-2 focus-visible:outline-foreground transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]"
        :class="enabled ? 'text-background' : 'text-foreground/60 hover:text-foreground/90'"
        @click="error && enabled ? retry() : toggle()"
      >
        <span
          aria-hidden="true"
          data-test="romanization-fill"
          class="absolute inset-1 rounded-full bg-foreground shadow-sm transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]"
          :class="enabled ? 'opacity-100 scale-100' : 'opacity-0 scale-80'"
        />
        <LoaderCircle v-if="loading" class="relative z-10 size-4 animate-spin" aria-hidden="true" />
        <RotateCcw v-else-if="error && enabled" class="relative z-10 size-4" aria-hidden="true" />
        <Languages v-else class="relative z-10 size-4" aria-hidden="true" />
      </button>
    </Teleport>
  </div>
</template>
