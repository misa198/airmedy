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
  Mic2,
  X,
} from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { RepeatMode } from '../../bindings/changeme/internal/domain/models'
import { formatTime } from '../lib/utils'
import LivingArtworkBackground from './LivingArtworkBackground.vue'
import { Slider } from '@/components/ui/slider'
import TrackTable from './TrackTable.vue'

const store = usePlayerStore()

const viewMode = ref<'classic' | 'living'>('living')
const rightColumnView = ref<'none' | 'queue' | 'lyrics'>('none')

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

const toggleRightColumn = (view: 'queue' | 'lyrics') => {
  if (rightColumnView.value === view) {
    rightColumnView.value = 'none'
  } else {
    rightColumnView.value = view
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex flex-col overflow-hidden bg-[#0A0A0A]">
    <LivingArtworkBackground :theme="store.theme" />

    <div class="relative z-10 flex flex-col h-full">
      <!-- Top bar -->
      <div class="flex items-center justify-between px-6 py-4">
        <button class="p-2 rounded-full hover:bg-white/8 transition-colors text-white/50 hover:text-white"
          @click="store.playerMode = 'sticky'">
          <Minimize2 class="w-5 h-5" />
        </button>
        <span class="text-xs font-semibold text-white/40 uppercase tracking-[0.2em]">Now Playing</span>
        <div class="flex items-center gap-1">
          <button class="p-2 rounded-full hover:bg-white/8 transition-colors"
            :class="rightColumnView === 'lyrics' ? 'text-primary' : 'text-white/50 hover:text-white'"
            @click="toggleRightColumn('lyrics')">
            <Mic2 class="w-4 h-4" />
          </button>
          <button class="p-2 rounded-full hover:bg-white/8 transition-colors"
            :class="rightColumnView === 'queue' ? 'text-primary' : 'text-white/50 hover:text-white'"
            @click="toggleRightColumn('queue')">
            <ListMusic class="w-4 h-4" />
          </button>
        </div>
      </div>

      <!-- Main content -->
      <div class="flex-1 flex items-center justify-center px-8 w-full max-w-[1400px] mx-auto overflow-hidden">
        <div class="flex-1 flex flex-row items-center justify-center gap-12 h-full">
          <!-- Left Column: Cover and Controls -->
          <div class="flex flex-col items-center justify-center gap-6 transition-all duration-500 ease-in-out"
            :class="rightColumnView === 'none' ? 'w-full max-w-lg' : 'w-1/2 max-w-md'">
            <!-- Artwork -->
            <div
              class="rounded-2xl shadow-[0_24px_60px_rgba(0,0,0,0.6)] overflow-hidden flex-shrink-0 ring-1 ring-white/8 transition-all duration-500"
              :class="rightColumnView === 'none' ? 'w-64 h-64 md:w-80 md:h-80' : 'w-56 h-56 md:w-64 md:h-64'">
              <img v-if="store.artworkUrl" :src="store.artworkUrl" :alt="trackTitle"
                class="w-full h-full object-cover" />
              <div v-else class="w-full h-full bg-white/5 flex items-center justify-center">
                <Music class="w-20 h-20 text-white/15" />
              </div>
            </div>

            <!-- Track info -->
            <div class="text-center w-full max-w-sm">
              <h1 class="text-2xl font-bold text-white truncate tracking-tight">{{ trackTitle }}</h1>
              <p class="text-base text-white/70 mt-1 truncate">{{ trackArtist }}</p>
              <p v-if="albumTitle" class="text-sm text-white/50 mt-0.5 truncate">{{ albumTitle }}</p>
            </div>

            <!-- Seek bar -->
            <div class="w-full max-w-sm space-y-1.5">
              <Slider :model-value="isSeeking ? seekValue : store.progressPercent" :min="0" :max="100" :step="0.1"
                @update:model-value="(v) => (seekValue = v)" @mousedown="onSeekStart" @mouseup="onSeekEnd"
                @touchstart="onSeekStart" @touchend="onSeekEnd" />
              <div class="flex justify-between text-[10px] text-white/50 tabular-nums">
                <span>{{ formatTime(displayPosition) }}</span>
                <span>{{ formatTime(store.duration) }}</span>
              </div>
            </div>

            <!-- Controls -->
            <div class="flex items-center gap-7">
              <button :class="store.shuffle ? 'text-primary' : 'text-white/30 hover:text-white/70'"
                class="transition-colors" @click="store.setShuffle(!store.shuffle)">
                <Shuffle class="w-5 h-5" />
              </button>
              <button class="text-white/70 hover:text-white transition-colors" @click="store.previous()">
                <SkipBack class="w-7 h-7 fill-current" />
              </button>
              <button
                class="w-14 h-14 bg-white rounded-full flex items-center justify-center hover:scale-105 transition-transform shadow-xl"
                @click="store.togglePlayPause()">
                <Pause v-if="store.isPlaying" class="w-6 h-6 fill-current text-black" />
                <Play v-else class="w-6 h-6 fill-current text-black ml-0.5" />
              </button>
              <button class="text-white/70 hover:text-white transition-colors" @click="store.next()">
                <SkipForward class="w-7 h-7 fill-current" />
              </button>
              <button :class="repeatActive ? 'text-primary' : 'text-white/30 hover:text-white/70'"
                class="transition-colors" @click="store.cycleRepeat()">
                <component :is="repeatIcon" class="w-5 h-5" />
              </button>
            </div>

            <!-- Volume -->
            <div class="flex items-center gap-3 w-full max-w-[220px]">
              <button class="text-white/30 hover:text-white/70 transition-colors flex-shrink-0"
                @click="store.setMuted(!store.muted)">
                <VolumeX v-if="store.muted" class="w-4 h-4" />
                <Volume2 v-else class="w-4 h-4" />
              </button>
              <Slider :model-value="store.muted ? 0 : store.volume" :min="0" :max="1" :step="0.01" class="flex-1"
                @update:model-value="(v) => store.setVolume(v)" />
            </div>
          </div>

          <!-- Right Column: Queue / Lyrics -->
          <Transition enter-active-class="transition-all duration-500 ease-out"
            enter-from-class="opacity-0 translate-x-12 scale-95" enter-to-class="opacity-100 translate-x-0 scale-100"
            leave-active-class="transition-all duration-300 ease-in"
            leave-from-class="opacity-100 translate-x-0 scale-100" leave-to-class="opacity-0 translate-x-12 scale-95">
            <div v-if="rightColumnView !== 'none'"
              class="w-1/2 h-[85%] max-w-xl bg-black/20 backdrop-blur-md rounded-3xl border border-white/5 flex flex-col overflow-hidden shadow-2xl">
              <!-- Right Column Header/Tabs -->
              <div class="flex items-center justify-between px-6 py-4 border-b border-white/5">
                <div class="flex gap-6">
                  <button @click="rightColumnView = 'queue'"
                    class="text-xs uppercase tracking-widest font-bold transition-colors"
                    :class="rightColumnView === 'queue' ? 'text-primary' : 'text-white/40 hover:text-white'">
                    Up Next
                  </button>
                  <button @click="rightColumnView = 'lyrics'"
                    class="text-xs uppercase tracking-widest font-bold transition-colors"
                    :class="rightColumnView === 'lyrics' ? 'text-primary' : 'text-white/40 hover:text-white'">
                    Lyrics
                  </button>
                </div>
                <button @click="rightColumnView = 'none'"
                  class="text-white/40 hover:text-white transition-colors p-1 hover:bg-white/5 rounded-full">
                  <X class="w-4 h-4" />
                </button>
              </div>

              <!-- Content Area -->
              <div class="flex-1 overflow-hidden">
                <div v-if="rightColumnView === 'queue'" class="h-full">
                  <TrackTable :tracks="store.queue" :show-album="false" :show-artwork="true"
                    @play-track="(track, index) => store.playTracks(store.queue, index)" />
                </div>
                <div v-else-if="rightColumnView === 'lyrics'"
                  class="h-full flex flex-col items-center justify-center text-white/20 p-12 text-center">
                  <Mic2 class="w-12 h-12 mb-4 opacity-10" />
                  <p class="text-lg font-medium text-white/40">Lyrics are not available for this track.</p>
                  <p class="text-sm text-white/20 mt-2">We're working on bringing lyrics to your collection soon.</p>
                </div>
              </div>
            </div>
          </Transition>
        </div>
      </div>
    </div>
  </div>
</template>
