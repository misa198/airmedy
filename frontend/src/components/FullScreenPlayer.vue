<script setup lang="ts">
import { computed } from 'vue'
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
  Minimize2,
  ListMusic,
  Music,
} from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { RepeatMode } from '../../bindings/changeme/internal/domain/models'
import { formatTime } from '../lib/utils'

const store = usePlayerStore()

const trackTitle = computed(() => store.currentTrack?.title ?? 'Not Playing')
const trackArtist = computed(() =>
  store.currentTrack?.artists?.map((a) => a?.name).filter(Boolean).join(', ') ?? '',
)
const albumTitle = computed(() => store.currentTrack?.album?.title ?? '')

const repeatIcon = computed(() =>
  store.repeatMode === RepeatMode.RepeatModeOne ? Repeat1 : Repeat,
)
const repeatActive = computed(
  () =>
    store.repeatMode === RepeatMode.RepeatModeOne ||
    store.repeatMode === RepeatMode.RepeatModeAll,
)

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

async function onSeek(e: Event) {
  const pct = Number((e.target as HTMLInputElement).value)
  await store.seek((pct / 100) * store.duration)
}
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex flex-col overflow-hidden"
    :style="{
      background: store.theme
        ? `linear-gradient(135deg, rgba(${parseInt(store.theme.dominant.slice(1, 3), 16)}, ${parseInt(store.theme.dominant.slice(3, 5), 16)}, ${parseInt(store.theme.dominant.slice(5, 7), 16)}, 0.9) 0%, #0A0A0A 100%)`
        : '#0A0A0A',
    }"
  >
    <!-- Top bar -->
    <div class="flex items-center justify-between p-6">
      <button
        class="p-2 rounded-full hover:bg-white/10 transition-colors text-white/70 hover:text-white"
        @click="store.playerMode = 'sticky'"
      >
        <Minimize2 class="w-5 h-5" />
      </button>
      <span class="text-sm font-medium text-white/60 uppercase tracking-widest">Now Playing</span>
      <button
        class="p-2 rounded-full hover:bg-white/10 transition-colors"
        :class="store.isQueueOpen ? 'text-primary' : 'text-white/70 hover:text-white'"
        @click="store.toggleQueue()"
      >
        <ListMusic class="w-5 h-5" />
      </button>
    </div>

    <!-- Main content -->
    <div class="flex-1 flex flex-col items-center justify-center px-8 gap-8">
      <!-- Large artwork -->
      <div class="w-64 h-64 md:w-80 md:h-80 rounded-2xl shadow-2xl overflow-hidden border border-white/10 flex-shrink-0">
        <img
          v-if="store.artworkUrl"
          :src="store.artworkUrl"
          :alt="trackTitle"
          class="w-full h-full object-cover"
        />
        <div v-else class="w-full h-full bg-white/10 flex items-center justify-center">
          <Music class="w-24 h-24 text-white/20" />
        </div>
      </div>

      <!-- Track info -->
      <div class="text-center max-w-md">
        <h1 class="text-3xl font-bold text-white truncate">{{ trackTitle }}</h1>
        <p class="text-lg text-white/60 mt-1 truncate">{{ trackArtist }}</p>
        <p v-if="albumTitle" class="text-sm text-white/40 mt-0.5 truncate">{{ albumTitle }}</p>
      </div>

      <!-- Progress -->
      <div class="w-full max-w-md space-y-2">
        <input
          type="range"
          min="0"
          max="100"
          step="0.1"
          :value="store.progressPercent"
          class="w-full h-1 accent-primary cursor-pointer"
          @input="onSeek"
        />
        <div class="flex justify-between text-xs text-white/40 font-mono">
          <span>{{ formatTime(store.position) }}</span>
          <span>{{ formatTime(store.duration) }}</span>
        </div>
      </div>

      <!-- Controls -->
      <div class="flex items-center gap-8">
        <button
          class="transition-colors"
          :class="store.shuffle ? 'text-primary' : 'text-white/50 hover:text-white'"
          @click="store.setShuffle(!store.shuffle)"
        >
          <Shuffle class="w-5 h-5" />
        </button>
        <button class="text-white hover:text-primary transition-colors" @click="store.previous()">
          <SkipBack class="w-7 h-7 fill-current" />
        </button>
        <button
          class="w-16 h-16 bg-white rounded-full flex items-center justify-center hover:scale-105 transition-transform shadow-xl"
          @click="store.togglePlayPause()"
        >
          <Pause v-if="store.isPlaying" class="w-7 h-7 fill-current text-black" />
          <Play v-else class="w-7 h-7 fill-current text-black ml-1" />
        </button>
        <button class="text-white hover:text-primary transition-colors" @click="store.next()">
          <SkipForward class="w-7 h-7 fill-current" />
        </button>
        <button
          class="transition-colors"
          :class="repeatActive ? 'text-primary' : 'text-white/50 hover:text-white'"
          @click="cycleRepeat()"
        >
          <component :is="repeatIcon" class="w-5 h-5" />
        </button>
      </div>

      <!-- Volume -->
      <div class="flex items-center gap-3 w-full max-w-xs">
        <button
          class="text-white/50 hover:text-white transition-colors flex-shrink-0"
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
    </div>
  </div>
</template>
