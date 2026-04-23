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
  Mic2,
} from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { RepeatMode } from '../../bindings/changeme/internal/domain/models'
import { formatTime } from '../lib/utils'
import { Slider } from '@/components/ui/slider'

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

const repeatIcon = computed(() =>
  store.repeatMode === RepeatMode.RepeatModeOne ? Repeat1 : Repeat,
)
const repeatActive = computed(
  () =>
    store.repeatMode === RepeatMode.RepeatModeOne ||
    store.repeatMode === RepeatMode.RepeatModeAll,
)

function onSeekStart() {
  isSeeking.value = true
}

async function onSeekEnd() {
  await store.seek((seekValue.value / 100) * store.duration)
  isSeeking.value = false
}

const openLyrics = () => {
  store.playerMode = 'fullscreen'
  if (!store.isLyricsOpen) {
    store.toggleLyrics()
  }
}
</script>

<template>
  <div
    class="h-[72px] bg-background/80 backdrop-blur-2xl border-t border-white/[0.06] flex items-center justify-between px-6 gap-6">
    <!-- Track Info -->
    <div class="flex items-center justify-start gap-3 w-1/4 min-w-[200px]">
      <div class="w-12 h-12 rounded-lg overflow-hidden flex-shrink-0 shadow-lg ring-1 ring-white/10">
        <img v-if="store.artworkUrl" :src="store.artworkUrl" :alt="trackTitle" class="w-full h-full object-cover" />
        <div v-else class="w-full h-full bg-white/5 flex items-center justify-center">
          <Music class="w-5 h-5 text-white/20" />
        </div>
      </div>
      <div class="flex flex-col min-w-0">
        <span class="font-medium text-sm truncate leading-tight">{{ trackTitle }}</span>
        <span class="text-xs text-white/40 truncate leading-tight mt-0.5">{{ trackArtist }}</span>
      </div>
    </div>

    <!-- Playback Controls -->
    <div class="flex-1 flex flex-col items-center gap-2 max-w-[600px]">
      <div class="flex items-center gap-5">
        <button class="transition-opacity"
          :class="store.shuffle ? 'text-primary opacity-100' : 'text-white/40 hover:text-white/70'"
          @click="store.setShuffle(!store.shuffle)">
          <Shuffle class="w-4 h-4" />
        </button>
        <button class="text-white/70 hover:text-white transition-colors" @click="store.previous()">
          <SkipBack class="w-5 h-5 fill-current" />
        </button>
        <button
          class="w-8 h-8 bg-white rounded-full flex items-center justify-center hover:scale-105 transition-transform"
          @click="store.togglePlayPause()">
          <Pause v-if="store.isPlaying" class="w-4 h-4 fill-current text-black" />
          <Play v-else class="w-4 h-4 fill-current text-black ml-0.5" />
        </button>
        <button class="text-white/70 hover:text-white transition-colors" @click="store.next()">
          <SkipForward class="w-5 h-5 fill-current" />
        </button>
        <button class="transition-colors" :class="repeatActive ? 'text-primary' : 'text-white/40 hover:text-white/70'"
          @click="store.cycleRepeat()">
          <component :is="repeatIcon" class="w-4 h-4" />
        </button>
      </div>

      <!-- Seek bar -->
      <div class="w-full flex items-center gap-2">
        <span class="text-[10px] text-white/30 tabular-nums w-8 text-right">
          {{ formatTime(displayPosition) }}
        </span>
        <Slider :model-value="isSeeking ? seekValue : store.progressPercent" :min="0" :max="100" :step="0.1"
          class="flex-1" @update:model-value="(v) => (seekValue = v)" @mousedown="onSeekStart" @mouseup="onSeekEnd"
          @touchstart="onSeekStart" @touchend="onSeekEnd" />
        <span class="text-[10px] text-white/30 tabular-nums w-8">
          {{ formatTime(store.duration) }}
        </span>
      </div>
    </div>

    <!-- Volume & Options -->
    <div class="flex items-center justify-end gap-4 w-1/4 min-w-[200px]">
      <div class="flex items-center gap-2 w-28">
        <button class="text-white/40 hover:text-white/70 transition-colors flex-shrink-0"
          @click="store.setMuted(!store.muted)">
          <VolumeX v-if="store.muted" class="w-4 h-4" />
          <Volume2 v-else class="w-4 h-4" />
        </button>
        <Slider :model-value="store.muted ? 0 : store.volume" :min="0" :max="1" :step="0.01" class="flex-1"
          @update:model-value="(v) => store.setVolume(v)" />
      </div>
      <button class="transition-colors"
        :class="store.isLyricsOpen ? 'text-primary' : 'text-white/40 hover:text-white/70'" @click="openLyrics">
        <Mic2 class="w-4 h-4" />
      </button>
      <button class="transition-colors"
        :class="store.isQueueOpen ? 'text-primary' : 'text-white/40 hover:text-white/70'" @click="store.toggleQueue()">
        <ListMusic class="w-4 h-4" />
      </button>
      <button class="text-white/40 hover:text-white/70 transition-colors" @click="store.playerMode = 'fullscreen'">
        <Maximize2 class="w-4 h-4" />
      </button>
    </div>
  </div>
</template>
