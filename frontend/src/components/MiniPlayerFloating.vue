<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import {
  SkipBack, SkipForward, Play, Pause,
  Pin, PinOff, X, Music,
  Shuffle, Repeat, Repeat1,
  Volume2, VolumeX,
} from 'lucide-vue-next'
import LazyImg from '@/components/LazyImg.vue'
import { Window } from '@wailsio/runtime'
import { usePlayerStore } from '@/stores/player'
import { RepeatMode } from '../../bindings/airmedy/internal/domain/models'
import { formatTime, hexToRgba, getTrackDisplayTitle } from '@/lib/utils'
import { Slider } from '@/components/ui/slider'
import MarqueeText from '@/components/MarqueeText.vue'
import * as WindowService from '../../bindings/airmedy/internal/infra/wails/windowservice'
import { useGlassBlur } from '@/composables/useGlassBlur'

const store = usePlayerStore()

const canvasRef = ref<HTMLCanvasElement | null>(null)
useGlassBlur(canvasRef, computed(() => store.artworkUrlMd ?? null))

const alwaysOnTop = ref(false)
const isSeeking = ref(false)
const seekValue = ref(0)
const isHovered = ref(false)
const showVolume = ref(false)
let volumeHideTimer: ReturnType<typeof setTimeout> | null = null

const displayPosition = computed(() =>
  isSeeking.value ? (seekValue.value / 100) * store.duration : store.position,
)
const trackTitle = computed(() => store.currentTrack ? (getTrackDisplayTitle(store.currentTrack) || 'Not Playing') : 'Not Playing')
const trackArtist = computed(() =>
  store.currentTrack?.artists
    ?.filter((a): a is NonNullable<typeof a> => a !== null)
    .map((a) => a.name)
    .join(', ') ?? '',
)
const repeatActive = computed(
  () => store.repeatMode === RepeatMode.RepeatModeOne || store.repeatMode === RepeatMode.RepeatModeAll,
)
const repeatIcon = computed(() =>
  store.repeatMode === RepeatMode.RepeatModeOne ? Repeat1 : Repeat,
)

async function toggleAlwaysOnTop() {
  alwaysOnTop.value = !alwaysOnTop.value
  await Window.SetAlwaysOnTop(alwaysOnTop.value)
}

function onSeekStart() { isSeeking.value = true }
async function onSeekEnd() {
  await store.seek((seekValue.value / 100) * store.duration)
  isSeeking.value = false
}

function onVolumeEnter() {
  if (volumeHideTimer) { clearTimeout(volumeHideTimer); volumeHideTimer = null }
  showVolume.value = true
}
function onVolumeLeave() {
  volumeHideTimer = setTimeout(() => { showVolume.value = false }, 300)
}

onUnmounted(() => {
  if (volumeHideTimer) clearTimeout(volumeHideTimer)
})

watch(() => store.theme, (colors) => {
  if (!colors) return
  const root = document.documentElement
  root.style.setProperty('--dynamic-primary', colors.vibrant)
  root.style.setProperty('--dynamic-surface', hexToRgba(colors.dominant, 0.15))
  root.style.setProperty('--dynamic-glow', `0 0 40px ${hexToRgba(colors.vibrant, 0.3)}`)
})
</script>

<template>
  <div class="relative w-full h-full overflow-hidden select-none dark" style="-webkit-app-region: drag"
    @mouseenter="isHovered = true" @mouseleave="isHovered = false"
    @dblclick="async () => { (await Window.IsMaximised()) ? Window.UnMaximise() : Window.Maximise() }">
    <!-- Artwork fills entire window -->
    <div class="absolute inset-0 bg-[#0A0A0A]" style="-webkit-app-region: no-drag">
      <LazyImg v-if="store.artworkUrl" :src="store.artworkUrl" :alt="trackTitle" class="w-full h-full object-cover" />
      <div v-else class="w-full h-full flex items-center justify-center bg-white/5">
        <Music class="w-16 h-16 text-white/20" />
      </div>
    </div>

    <!-- Options pill: always visible, top-right -->
    <div class="absolute top-2 right-2 z-30" style="-webkit-app-region: no-drag">
      <!-- Volume slider popup -->
      <Transition name="fade">
        <div v-if="showVolume && isHovered"
          class="absolute top-full right-0 mt-2 px-2.5 py-2 rounded-xl bg-black/20 backdrop-blur-md border border-white/5"
          @mouseenter="onVolumeEnter" @mouseleave="onVolumeLeave">
          <Slider :model-value="store.muted ? 0 : store.volume * 100" :min="0" :max="100" :step="1" class="w-20"
            @update:model-value="(v) => store.setVolume(v / 100)" />
        </div>
      </Transition>

      <!-- Three-button pill -->
      <div
        class="inline-flex items-center p-1 rounded-full bg-black/20 backdrop-blur-md border border-white/5 h-8 select-none"
        :class="isHovered ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'">
        <button
          class="w-6 h-6 flex items-center justify-center rounded-full text-white/50 hover:text-white/80 transition-colors"
          @mouseenter="onVolumeEnter" @mouseleave="onVolumeLeave" @click="showVolume = !showVolume">
          <VolumeX v-if="store.muted" class="w-3.5 h-3.5" />
          <Volume2 v-else class="w-3.5 h-3.5" />
        </button>
        <button class="w-6 h-6 flex items-center justify-center rounded-full transition-colors"
          :class="alwaysOnTop ? 'text-white/80' : 'text-white/50 hover:text-white/80'" @click="toggleAlwaysOnTop()">
          <Pin v-if="alwaysOnTop" class="w-3.5 h-3.5" />
          <PinOff v-else class="w-3.5 h-3.5" />
        </button>
        <button
          class="w-6 h-6 flex items-center justify-center rounded-full text-white/50 hover:text-white/80 transition-colors"
          @click="WindowService.CloseMiniPlayer()">
          <X class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>

    <!-- OGL glass panel: WebGL blur, no backdrop-filter, no flicker -->
    <canvas
      ref="canvasRef"
      class="absolute bottom-0 left-0 pointer-events-none transition-opacity duration-200 blur-xl"
      :class="isHovered ? 'opacity-100' : 'opacity-0'"
      style="height: 250px;width: 500px;"
    />

    <!-- Content overlay (hover-triggered) -->
    <div class="absolute bottom-0 left-0 right-0 px-3 pb-2 transition-opacity duration-200"
      :class="isHovered ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'"
      style="-webkit-app-region: no-drag">
      <MarqueeText :text="trackTitle" content-class="text font-semibold leading-tight text-white" />
      <MarqueeText :text="trackArtist" content-class="text-xs text-white/50 leading-tight mt-0.5" />

      <!-- Seek bar -->
      <div class="flex items-center gap-1.5 mt-2">
        <span class="text-[10px] text-white/40 tabular-nums w-7 text-right shrink-0">
          {{ formatTime(displayPosition) }}
        </span>
        <Slider :model-value="isSeeking ? seekValue : store.progressPercent" :min="0" :max="100" :step="0.1"
          class="flex-1" @update:model-value="(v) => (seekValue = v)" @mousedown="onSeekStart" @mouseup="onSeekEnd" />
        <span class="text-[10px] text-white/40 tabular-nums w-7 shrink-0">
          {{ formatTime(store.duration) }}
        </span>
      </div>

      <!-- Controls: shuffle, prev, play/pause, next, loop -->
      <div class="flex items-center justify-center gap-4 mt-2 mb-1">
        <button class="transition-colors" :class="store.shuffle ? 'text-white/80' : 'text-white/20 hover:text-white/70'"
          @click="store.setShuffle(!store.shuffle)">
          <Shuffle class="w-3.5 h-3.5" />
        </button>
        <button class="text-white/80 hover:text-white/90 transition-colors" @click="store.previous()">
          <SkipBack class="w-4 h-4 fill-current" />
        </button>
        <button
          class="w-9 h-9 bg-white rounded-full flex items-center justify-center hover:scale-105 transition-transform shrink-0"
          @click="store.togglePlayPause()">
          <Pause v-if="store.isPlaying" class="w-[18px] h-[18px] fill-current text-[#0A0A0A]" />
          <Play v-else class="w-[18px] h-[18px] fill-current text-[#0A0A0A] ml-0.5" />
        </button>
        <button class="text-white/80 hover:text-white/90 transition-colors" @click="store.next()">
          <SkipForward class="w-4 h-4 fill-current" />
        </button>
        <button class="transition-colors" :class="repeatActive ? 'text-white/80' : 'text-white/20 hover:text-white/70'"
          @click="store.cycleRepeat()">
          <component :is="repeatIcon" class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
