<script setup lang="ts">
import { Slider } from '@/components/ui/slider'
import {
  ListMusic,
  Mic2,
  Minimize2,
  Music,
  Pause,
  Play,
  Repeat,
  Repeat1,
  Shuffle,
  SkipBack,
  SkipForward,
  Volume2,
  VolumeX,
  X,
} from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { RepeatMode } from '../../bindings/changeme/internal/domain/models'
import { formatTime } from '../lib/utils'
import { usePlayerStore } from '../stores/player'
import { useDeviceStore } from '../stores/device'
import LivingArtworkBackground from './LivingArtworkBackground.vue'
import LyricsView from './LyricsView.vue'
import MarqueeText from './MarqueeText.vue'
import TrackTable from './TrackTable.vue'
import TabSwitcher from './ui/TabSwitcher.vue'

const store = usePlayerStore()
const deviceStore = useDeviceStore()

const isSeeking = ref(false)
const seekValue = ref(0)

const activeTab = computed({
  get: () => {
    if (store.isLyricsOpen) return 'lyrics'
    if (store.isQueueOpen) return 'queue'
    return null
  },
  set: (val: string | null) => {
    if (val === 'lyrics') {
      store.isLyricsOpen = true
      store.isQueueOpen = false
    } else if (val === 'queue') {
      store.isQueueOpen = true
      store.isLyricsOpen = false
    } else {
      store.isLyricsOpen = false
      store.isQueueOpen = false
    }
  },
})

const tabOptions = [
  { value: 'lyrics', label: 'Lyrics', icon: Mic2 },
  { value: 'queue', label: 'Up Next', icon: ListMusic },
]

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

function onSeekStart() { isSeeking.value = true }
async function onSeekEnd() {
  await store.seek((seekValue.value / 100) * store.duration)
  isSeeking.value = false
}

const showRightColumn = computed(() => store.isQueueOpen || store.isLyricsOpen)
</script>

<template>
  <div class="fixed inset-0 z-50 flex flex-col overflow-hidden bg-[#0A0A0A]">
    <LivingArtworkBackground :theme="store.theme" :is-playing="store.isPlaying" />

    <div class="relative z-10 flex flex-col h-full">
      <!-- Top bar -->
      <div class="flex items-center justify-between px-6 py-4">
        <button class="p-2 rounded-full hover:bg-white/8 transition-all text-white/60 hover:text-white"
          :class="{ 'mt-8': deviceStore.isMac && !deviceStore.isWindowFullscreen }" @click="store.playerMode = 'sticky'">
          <Minimize2 class="w-5 h-5" />
        </button>
        <span class="text-xs font-semibold text-white/40 uppercase tracking-[0.2em]">Now Playing</span>
        <div class="flex items-center gap-2">
          <TabSwitcher v-model="activeTab" :options="tabOptions" />
        </div>
      </div>

      <!-- Main content -->
      <div class="flex-1 flex items-center justify-center px-8 w-full max-w-[1400px] mx-auto overflow-hidden">
        <div
          class="flex-1 flex flex-row items-center justify-center h-full transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)] relative @container"
          :class="!showRightColumn ? 'gap-0' : 'gap-12'">
          <!-- Left Column: Cover and Controls -->
          <div
            class="flex flex-col items-center justify-center transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)]"
            :class="!showRightColumn ? 'w-full max-w-lg' : 'w-1/2 max-w-md'">
            <div class="flex flex-col items-center justify-center gap-6 w-full">
              <!-- Artwork -->
              <div
                class="rounded-2xl shadow-[0_24px_60px_rgba(0,0,0,0.6)] overflow-hidden flex-shrink-0 ring-1 ring-white/8 transition-all duration-700 ease-[cubic-bezier(0.4,0,0.2,1)]"
                :class="[
                  !showRightColumn ? 'w-64 h-64 md:w-80 md:h-80' : 'w-56 h-56 md:w-64 md:h-64',
                  store.isPlaying ? 'scale-100' : 'scale-[0.80]'
                ]">
                <img v-if="store.artworkUrl" :src="store.artworkUrl" :alt="trackTitle"
                  class="w-full h-full object-cover" />
                <div v-else class="w-full h-full bg-white/5 flex items-center justify-center">
                  <Music class="w-20 h-20 text-white/15" />
                </div>
              </div>

              <!-- Track info -->
              <div class="text-center w-full max-w-sm mx-auto">
                <MarqueeText :text="trackTitle"
                  content-class="text-2xl font-bold text-white tracking-tight text-center" />
                <MarqueeText :text="trackArtist" content-class="text-base text-white/80 mt-1 text-center" />
                <MarqueeText v-if="albumTitle" :text="albumTitle"
                  content-class="text-sm text-white/60 mt-0.5 text-center" />
              </div>

              <!-- Seek bar -->
              <div class="w-full max-w-sm space-y-1.5">
                <Slider :model-value="isSeeking ? seekValue : store.progressPercent" :min="0" :max="100" :step="0.1"
                  @update:model-value="(v) => (seekValue = v)" @mousedown="onSeekStart" @mouseup="onSeekEnd"
                  @touchstart="onSeekStart" @touchend="onSeekEnd" />
                <div class="flex justify-between text-[10.5px] text-white/60 tabular-nums">
                  <span>{{ formatTime(displayPosition) }}</span>
                  <span>{{ formatTime(store.duration) }}</span>
                </div>
              </div>

              <!-- Controls -->
              <div class="flex items-center gap-7">
                <button :class="store.shuffle ? 'text-white/80' : 'text-white/30 hover:text-white/80'"
                  class="transition-colors" @click="store.setShuffle(!store.shuffle)">
                  <Shuffle class="w-5 h-5" />
                </button>
                <button class="text-white/80 hover:text-white transition-colors" @click="store.previous()">
                  <SkipBack class="w-7 h-7 fill-current" />
                </button>
                <button
                  class="w-14 h-14 bg-white rounded-full flex items-center justify-center hover:scale-105 transition-transform shadow-xl"
                  @click="store.togglePlayPause()">
                  <Pause v-if="store.isPlaying" class="w-6 h-6 fill-current text-black" />
                  <Play v-else class="w-6 h-6 fill-current text-black ml-0.5" />
                </button>
                <button class="text-white/80 hover:text-white transition-colors" @click="store.next()">
                  <SkipForward class="w-7 h-7 fill-current" />
                </button>
                <button :class="repeatActive ? 'text-white/80' : 'text-white/30 hover:text-white/80'"
                  class="transition-colors" @click="store.cycleRepeat()">
                  <component :is="repeatIcon" class="w-5 h-5" />
                </button>
              </div>

              <!-- Volume -->
              <div class="flex items-center gap-3 w-full max-w-[220px]">
                <button class="text-white/80 hover:text-white/80 transition-colors flex-shrink-0"
                  @click="store.setMuted(!store.muted)">
                  <VolumeX v-if="store.muted" class="w-4 h-4" />
                  <Volume2 v-else class="w-4 h-4" />
                </button>
                <Slider :model-value="store.muted ? 0 : store.volume" :min="0" :max="1" :step="0.01" class="flex-1"
                  @update:model-value="(v) => store.setVolume(v)" />
              </div>
            </div>
          </div>

          <!-- Right Column Spacer (animates layout) -->
          <div
            class="h-full transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)] relative flex items-center justify-center"
            :class="!showRightColumn ? 'w-0' : 'w-1/2 max-w-xl'">

            <!-- Right Column Content (Queue or Lyrics) -->
            <Transition enter-active-class="transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)]"
              enter-from-class="opacity-0 translate-x-24" enter-to-class="opacity-100 translate-x-0"
              leave-active-class="transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)]"
              leave-from-class="opacity-100 translate-x-0" leave-to-class="opacity-0 translate-x-24">
              <!-- Right Column: Queue -->
              <div v-if="store.isQueueOpen" key="queue"
                class="absolute left-0 h-[85%] my-auto bg-black/30 backdrop-blur-3xl rounded-3xl border border-white/10 flex flex-col overflow-hidden shadow-2xl w-[50cqw] max-w-xl">
                <div class="flex-1 flex flex-col h-full">
                  <!-- Queue Header -->
                  <div class="flex items-center justify-between px-6 py-4 border-b border-white/5">
                    <div class="flex items-center gap-2 text-white/80">
                      <ListMusic class="w-4 h-4" />
                      <span class="text-sm font-semibold uppercase tracking-wider">Up Next</span>
                    </div>
                    <button @click="store.isQueueOpen = false"
                      class="text-white/40 hover:text-white transition-colors p-1 hover:bg-white/5 rounded-full">
                      <X class="w-4 h-4" />
                    </button>
                  </div>

                  <!-- Content Area -->
                  <div class="flex-1 overflow-hidden">
                    <TrackTable :tracks="store.queue" :show-album="false" :show-artwork="true" :scroll-to-current="true"
                      @play-track="(track, index) => store.playTracks(store.queue, index)" />
                  </div>
                </div>
              </div>

              <!-- Right Column: Lyrics -->
              <div v-else-if="store.isLyricsOpen" key="lyrics"
                class="absolute left-0 h-[85%] my-auto bg-black/30 backdrop-blur-3xl rounded-3xl border border-white/10 flex flex-col overflow-hidden shadow-2xl w-[50cqw] max-w-xl">
                <div class="flex-1 flex flex-col h-full">
                  <!-- Lyrics Header -->
                  <div class="flex items-center justify-between px-6 py-4 border-b border-white/5">
                    <div class="flex items-center gap-2 text-white/80">
                      <Mic2 class="w-4 h-4" />
                      <span class="text-sm font-semibold uppercase tracking-wider">Lyrics</span>
                    </div>
                    <button @click="store.isLyricsOpen = false"
                      class="text-white/40 hover:text-white transition-colors p-1 hover:bg-white/5 rounded-full">
                      <X class="w-4 h-4" />
                    </button>
                  </div>

                  <!-- Content Area -->
                  <div class="flex-1 overflow-hidden">
                    <LyricsView :current-position="store.position" @seek="(time) => store.seek(time)" />
                  </div>
                </div>
              </div>
            </Transition>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
