<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Repeat,
  Repeat1,
  Shuffle,
  Volume2,
  VolumeX,
  Maximize2,
  Music,
  ListMusic,
} from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { RepeatMode } from '../../bindings/changeme/internal/domain/models'
import { formatTime } from '../lib/utils'

const store = usePlayerStore()

const isSeeking = ref(false)
const seekValue = ref(0)

const displayPosition = computed(() =>
  isSeeking.value ? (seekValue.value / 100) * store.duration : store.position,
)

const trackTitle = computed(() => store.currentTrack?.title ?? 'Not Playing')
const trackArtist = computed(() => {
  const artists = store.currentTrack?.artists
  if (!artists || artists.length === 0) return 'Select a track to start listening'
  return artists.filter((a): a is NonNullable<typeof a> => a !== null).map((a) => a.name).join(', ')
})

const repeatIcon = computed(() => {
  switch (store.repeatMode) {
    case RepeatMode.RepeatModeOne:
      return Repeat1
    default:
      return Repeat
  }
})
const repeatActive = computed(
  () =>
    store.repeatMode === RepeatMode.RepeatModeOne ||
    store.repeatMode === RepeatMode.RepeatModeAll,
)

function onSeekStart(e: Event) {
  isSeeking.value = true
  seekValue.value = Number((e.target as HTMLInputElement).value)
}

function onSeekMove(e: Event) {
  if (isSeeking.value) {
    seekValue.value = Number((e.target as HTMLInputElement).value)
  }
}

async function onSeekEnd(e: Event) {
  const pct = Number((e.target as HTMLInputElement).value)
  await store.seek((pct / 100) * store.duration)
  isSeeking.value = false
}

function cycleRepeat() {
  switch (store.repeatMode) {
    case RepeatMode.RepeatModeOff:
      store.setRepeatMode(RepeatMode.RepeatModeAll)
      break
    case RepeatMode.RepeatModeAll:
      store.setRepeatMode(RepeatMode.RepeatModeOne)
      break
    default:
      store.setRepeatMode(RepeatMode.RepeatModeOff)
  }
}
</script>

<template>
  <div class="h-24 bg-card border-t flex items-center px-6 gap-6 shadow-2xl">
    <!-- Track Info -->
    <div class="flex items-center gap-4 w-1/4 min-w-[200px]">
      <div
        class="w-14 h-14 bg-accent rounded-md flex items-center justify-center shadow-inner overflow-hidden border flex-shrink-0"
      >
        <img
          v-if="store.artworkUrl"
          :src="store.artworkUrl"
          :alt="trackTitle"
          class="w-full h-full object-cover"
        />
        <Music v-else class="w-6 h-6 text-muted-foreground" />
      </div>
      <div class="flex flex-col min-w-0">
        <span class="font-medium text-sm truncate">{{ trackTitle }}</span>
        <span class="text-xs text-muted-foreground truncate">{{ trackArtist }}</span>
      </div>
    </div>

    <!-- Playback Controls -->
    <div class="flex-1 flex flex-col items-center max-w-2xl">
      <div class="flex items-center gap-6 mb-2">
        <button
          class="transition-colors"
          :class="store.shuffle ? 'text-primary' : 'text-muted-foreground hover:text-foreground'"
          @click="store.setShuffle(!store.shuffle)"
        >
          <Shuffle class="w-4 h-4" />
        </button>
        <button
          class="text-foreground hover:text-primary transition-colors"
          @click="store.previous()"
        >
          <SkipBack class="w-5 h-5 fill-current" />
        </button>
        <button
          class="w-10 h-10 bg-primary rounded-full flex items-center justify-center hover:scale-105 transition-transform shadow-lg"
          @click="store.togglePlayPause()"
        >
          <Pause v-if="store.isPlaying" class="w-5 h-5 fill-current" />
          <Play v-else class="w-5 h-5 fill-current ml-0.5" />
        </button>
        <button
          class="text-foreground hover:text-primary transition-colors"
          @click="store.next()"
        >
          <SkipForward class="w-5 h-5 fill-current" />
        </button>
        <button
          class="transition-colors"
          :class="repeatActive ? 'text-primary' : 'text-muted-foreground hover:text-foreground'"
          @click="cycleRepeat()"
        >
          <component :is="repeatIcon" class="w-4 h-4" />
        </button>
      </div>
      <div class="w-full flex items-center gap-3">
        <span class="text-[10px] text-muted-foreground w-8 text-right">
          {{ formatTime(displayPosition) }}
        </span>
        <input
          type="range"
          min="0"
          max="100"
          step="0.1"
          :value="isSeeking ? seekValue : store.progressPercent"
          class="flex-1 h-1 accent-primary cursor-pointer"
          @mousedown="onSeekStart"
          @input="onSeekMove"
          @mouseup="onSeekEnd"
          @touchstart="onSeekStart"
          @touchend="onSeekEnd"
        />
        <span class="text-[10px] text-muted-foreground w-8">
          {{ formatTime(store.duration) }}
        </span>
      </div>
    </div>

    <!-- Volume & Options -->
    <div class="flex items-center justify-end gap-4 w-1/4 min-w-[180px]">
      <div class="flex items-center gap-2 w-28">
        <button
          class="text-muted-foreground hover:text-foreground transition-colors flex-shrink-0"
          @click="store.setMuted(!store.muted)"
        >
          <VolumeX v-if="store.muted" class="w-4 h-4" />
          <Volume2 v-else class="w-4 h-4" />
        </button>
        <input
          type="range"
          min="0"
          max="1"
          step="0.01"
          :value="store.muted ? 0 : store.volume"
          class="flex-1 h-1 accent-primary cursor-pointer"
          @input="(e) => store.setVolume(Number((e.target as HTMLInputElement).value))"
        />
      </div>
      <button
        class="transition-colors"
        :class="store.isQueueOpen ? 'text-primary' : 'text-muted-foreground hover:text-foreground'"
        @click="store.toggleQueue()"
      >
        <ListMusic class="w-4 h-4" />
      </button>
      <button
        class="text-muted-foreground hover:text-foreground transition-colors"
        @click="store.playerMode = 'fullscreen'"
      >
        <Maximize2 class="w-4 h-4" />
      </button>
    </div>
  </div>
</template>
