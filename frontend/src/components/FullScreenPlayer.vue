<script setup lang="ts">
import { computed, ref } from 'vue'
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
  Sparkles,
} from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { RepeatMode } from '../../bindings/changeme/internal/domain/models'
import { formatTime } from '../lib/utils'
import LivingArtworkBackground from './LivingArtworkBackground.vue'
import { Slider } from '@/components/ui/slider'

const store = usePlayerStore()

const viewMode = ref<'classic' | 'living'>('living')

const isSeeking = ref(false)
const seekValue = ref(0)

const displayPosition = computed(() =>
  isSeeking.value ? (seekValue.value / 100) * store.duration : store.position,
)

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

const classicBackground = computed(() => {
  if (!store.theme) return '#0A0A0A'
  const d = store.theme.dominant
  const r = parseInt(d.slice(1, 3), 16)
  const g = parseInt(d.slice(3, 5), 16)
  const b = parseInt(d.slice(5, 7), 16)
  return `linear-gradient(160deg, rgba(${r}, ${g}, ${b}, 0.7) 0%, #0A0A0A 60%)`
})

function onSeekStart() { isSeeking.value = true }
async function onSeekEnd() {
  await store.seek((seekValue.value / 100) * store.duration)
  isSeeking.value = false
}
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex flex-col overflow-hidden"
    :style="viewMode === 'classic' ? { background: classicBackground } : { background: '#0A0A0A' }"
  >
    <LivingArtworkBackground v-if="viewMode === 'living'" :theme="store.theme" />

    <div class="relative z-10 flex flex-col h-full">
      <!-- Top bar -->
      <div class="flex items-center justify-between px-6 py-4">
        <button
          class="p-2 rounded-full hover:bg-white/8 transition-colors text-white/50 hover:text-white"
          @click="store.playerMode = 'sticky'"
        >
          <Minimize2 class="w-5 h-5" />
        </button>
        <span class="text-xs font-semibold text-white/40 uppercase tracking-[0.2em]">Now Playing</span>
        <div class="flex items-center gap-1">
          <button
            class="p-2 rounded-full hover:bg-white/8 transition-colors"
            :class="viewMode === 'living' ? 'text-primary' : 'text-white/50 hover:text-white'"
            @click="viewMode = viewMode === 'classic' ? 'living' : 'classic'"
          >
            <Sparkles class="w-4 h-4" />
          </button>
          <button
            class="p-2 rounded-full hover:bg-white/8 transition-colors"
            :class="store.isQueueOpen ? 'text-primary' : 'text-white/50 hover:text-white'"
            @click="store.toggleQueue()"
          >
            <ListMusic class="w-4 h-4" />
          </button>
        </div>
      </div>

      <!-- Main content -->
      <div class="flex-1 flex flex-col items-center justify-center px-8 gap-6">
        <!-- Artwork -->
        <div class="w-64 h-64 md:w-72 md:h-72 rounded-2xl shadow-[0_24px_60px_rgba(0,0,0,0.6)] overflow-hidden flex-shrink-0 ring-1 ring-white/8">
          <img
            v-if="store.artworkUrl"
            :src="store.artworkUrl"
            :alt="trackTitle"
            class="w-full h-full object-cover"
          />
          <div v-else class="w-full h-full bg-white/5 flex items-center justify-center">
            <Music class="w-20 h-20 text-white/15" />
          </div>
        </div>

        <!-- Track info -->
        <div class="text-center max-w-sm w-full">
          <h1 class="text-2xl font-bold text-white truncate tracking-tight">{{ trackTitle }}</h1>
          <p class="text-base text-white/50 mt-1 truncate">{{ trackArtist }}</p>
          <p v-if="albumTitle" class="text-sm text-white/30 mt-0.5 truncate">{{ albumTitle }}</p>
        </div>

        <!-- Seek bar -->
        <div class="w-full max-w-sm space-y-1.5">
          <Slider
            :model-value="isSeeking ? seekValue : store.progressPercent"
            :min="0"
            :max="100"
            :step="0.1"
            @update:model-value="(v) => (seekValue = v)"
            @mousedown="onSeekStart"
            @mouseup="onSeekEnd"
            @touchstart="onSeekStart"
            @touchend="onSeekEnd"
          />
          <div class="flex justify-between text-[10px] text-white/30 tabular-nums">
            <span>{{ formatTime(displayPosition) }}</span>
            <span>{{ formatTime(store.duration) }}</span>
          </div>
        </div>

        <!-- Controls -->
        <div class="flex items-center gap-7">
          <button
            :class="store.shuffle ? 'text-primary' : 'text-white/30 hover:text-white/70'"
            class="transition-colors"
            @click="store.setShuffle(!store.shuffle)"
          >
            <Shuffle class="w-5 h-5" />
          </button>
          <button class="text-white/70 hover:text-white transition-colors" @click="store.previous()">
            <SkipBack class="w-7 h-7 fill-current" />
          </button>
          <button
            class="w-14 h-14 bg-white rounded-full flex items-center justify-center hover:scale-105 transition-transform shadow-xl"
            @click="store.togglePlayPause()"
          >
            <Pause v-if="store.isPlaying" class="w-6 h-6 fill-current text-black" />
            <Play v-else class="w-6 h-6 fill-current text-black ml-0.5" />
          </button>
          <button class="text-white/70 hover:text-white transition-colors" @click="store.next()">
            <SkipForward class="w-7 h-7 fill-current" />
          </button>
          <button
            :class="repeatActive ? 'text-primary' : 'text-white/30 hover:text-white/70'"
            class="transition-colors"
            @click="store.cycleRepeat()"
          >
            <component :is="repeatIcon" class="w-5 h-5" />
          </button>
        </div>

        <!-- Volume -->
        <div class="flex items-center gap-3 w-full max-w-[220px]">
          <button
            class="text-white/30 hover:text-white/70 transition-colors flex-shrink-0"
            @click="store.setMuted(!store.muted)"
          >
            <VolumeX v-if="store.muted" class="w-4 h-4" />
            <Volume2 v-else class="w-4 h-4" />
          </button>
          <Slider
            :model-value="store.muted ? 0 : store.volume"
            :min="0"
            :max="1"
            :step="0.01"
            class="flex-1"
            @update:model-value="(v) => store.setVolume(v)"
          />
        </div>
      </div>
    </div>
  </div>
</template>
