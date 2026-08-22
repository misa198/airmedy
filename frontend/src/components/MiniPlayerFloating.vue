<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  SkipBack, SkipForward, Play, Pause,
  Pin, PinOff, X, Music,
  Shuffle, Repeat, Repeat1,
  Volume2, VolumeX,
  Mic2, ListMusic, Goal, Radio,
} from '@lucide/vue'
import LazyImg from '@/components/LazyImg.vue'
import { usePlayerStore } from '@/stores/player'
import { useMoodRadioStore } from '@/stores/moodRadio'
import { RepeatMode } from '../../bindings/airmedy/internal/domain/models'
import { formatTime, hexToRgba, getTrackDisplayTitle } from '@airmedy/utils'
import { Slider } from '@airmedy/ui'
import { MarqueeText } from '@airmedy/ui'
import * as WindowService from '../../bindings/airmedy/internal/infra/wails/windowservice'
import PlayerControlButton from './player/PlayerControlButton.vue'
import MiniPlayerLyrics from './MiniPlayerLyrics.vue'
import QueueTrackList from './QueueTrackList.vue'
import { useAppStore } from '@/stores/app'
import { useDeviceStore } from '@/stores/device'
import { useArtworkCrossfadeOpacity } from '@/composables/useArtworkCrossfadeOpacity'

const store = usePlayerStore()
const moodRadioStore = useMoodRadioStore()
const appStore = useAppStore()
const deviceStore = useDeviceStore()
const { t } = useI18n()
const artworkCrossfade = computed(() =>
  appStore.blendArtworkDuringCrossfade ? store.artworkCrossfade : null,
)
const { outgoingOpacity, incomingOpacity } = useArtworkCrossfadeOpacity(artworkCrossfade)

const alwaysOnTop = ref(false)
const isSeeking = ref(false)
const seekValue = ref(0)
const isHovered = ref(false)
const showVolume = ref(false)
const activePanel = ref<'lyrics' | 'queue' | null>(null)
const queueTrackList = ref<InstanceType<typeof QueueTrackList> | null>(null)
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
  await WindowService.SetMiniAlwaysOnTop(alwaysOnTop.value)
}

async function togglePanel(panel: 'lyrics' | 'queue') {
  const nextPanel = activePanel.value === panel ? null : panel
  if (nextPanel === null) {
    activePanel.value = null
    await WindowService.SetMiniPlayerExpanded(false)
    return
  }
  if (activePanel.value === null) {
    await WindowService.SetMiniPlayerExpanded(true)
  }
  activePanel.value = nextPanel
}

onMounted(async () => {
  // Reflect the restored pin state (Go already re-applied it to the native window).
  const state = await WindowService.GetMiniState()
  alwaysOnTop.value = state.always_on_top
})

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
  <div class="h-full w-full overflow-hidden select-none flex flex-col">
    <div class="relative aspect-square w-full shrink-0 overflow-hidden" style="-webkit-app-region: drag"
      @mouseenter="isHovered = true" @mouseleave="isHovered = false">
      <!-- Artwork fills entire window -->
      <div class="absolute inset-0 bg-[#0A0A0A]" style="-webkit-app-region: no-drag">
        <template v-if="artworkCrossfade">
          <LazyImg v-if="artworkCrossfade.fromUrl" :src="artworkCrossfade.fromUrl" :alt="trackTitle"
            class="absolute inset-0 w-full h-full object-cover" :style="{ opacity: outgoingOpacity }" />
          <LazyImg v-if="artworkCrossfade.toUrl" :src="artworkCrossfade.toUrl" :alt="trackTitle"
            class="absolute inset-0 w-full h-full object-cover artwork-crossfade-incoming"
            :style="{ opacity: incomingOpacity }" />
        </template>
        <LazyImg v-else-if="store.artworkUrl" :src="store.artworkUrl" :alt="trackTitle"
          class="w-full h-full object-cover" />
        <div v-else class="w-full h-full flex items-center justify-center bg-white/5">
          <Music class="w-16 h-16 text-white/20" />
        </div>
      </div>

      <!-- Windows-only drag handle: Wails v3 uses --wails-draggable (not -webkit-app-region) -->
      <div v-if="deviceStore.isWindows" class="absolute top-0 left-0 right-0 h-10 z-20"
        style="--wails-draggable: drag" />

      <div class="absolute top-2 right-2 z-30" style="-webkit-app-region: no-drag">
        <div
          class="relative inline-flex items-center gap-0.5 p-1 rounded-full bg-mini-player-pill-background backdrop-blur-md border border-mini-player-pill-border h-8 transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]"
          :class="isHovered ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'">
          <span data-test="mini-player-panel-indicator" aria-hidden="true"
            class="absolute top-1 left-1 w-6 h-6 rounded-full bg-mini-player-pill-active transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]"
            :class="[
              activePanel ? 'opacity-100' : 'opacity-0',
              activePanel === 'queue' ? 'translate-x-[26px]' : 'translate-x-0',
            ]" />
          <button data-test="mini-player-lyrics"
            class="relative z-10 w-6 h-6 flex items-center justify-center rounded-full text-mini-player-pill-foreground transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]"
            :class="activePanel === 'lyrics' ? 'text-mini-player-pill-active-foreground!' : ''" :aria-label="t('player.lyrics')"
            :aria-pressed="activePanel === 'lyrics'" :title="t('player.lyrics')" @click="togglePanel('lyrics')">
            <Mic2 class="w-3.5 h-3.5" />
          </button>
          <button data-test="mini-player-queue"
            class="relative z-10 w-6 h-6 flex items-center justify-center rounded-full text-mini-player-pill-foreground transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]"
            :class="activePanel === 'queue' ? 'text-mini-player-pill-active-foreground!' : ''" :aria-label="t('player.queue')"
            :aria-pressed="activePanel === 'queue'" :title="t('player.queue')" @click="togglePanel('queue')">
            <ListMusic class="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      <!-- Options pill: always visible, top-left -->
      <div class="absolute top-2 left-2 z-30" style="-webkit-app-region: no-drag">
        <!-- Volume slider popup -->
        <Transition name="fade">
          <div v-if="showVolume && isHovered"
            class="absolute top-full left-0 mt-2 px-2.5 py-2 rounded-full bg-mini-player-pill-background backdrop-blur-md border border-mini-player-pill-border"
            @mouseenter="onVolumeEnter" @mouseleave="onVolumeLeave">
            <Slider :model-value="store.muted ? 0 : store.volume * 100" :min="0" :max="100" :step="1" :scrollable="true"
              class="w-20" track-color-class="bg-mini-player-pill-foreground"
              track-background="var(--mini-player-pill-track)" thumb-color="var(--mini-player-pill-foreground)"
              @update:model-value="(v) => store.setVolume(v / 100)" />
          </div>
        </Transition>

        <!-- Three-button pill -->
        <div
          class="inline-flex items-center p-1 rounded-full bg-mini-player-pill-background backdrop-blur-md border border-mini-player-pill-border h-8 select-none transition-all"
          :class="isHovered ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'">
          <button class="w-6 h-6 flex items-center justify-center rounded-full text-mini-player-pill-foreground transition-colors"
            @click="WindowService.CloseMiniPlayer()">
            <X class="w-3.5 h-3.5" />
          </button>
          <button class="w-6 h-6 flex items-center justify-center rounded-full transition-colors"
            :class="alwaysOnTop ? 'text-mini-player-pill-foreground/80' : 'text-mini-player-pill-foreground'" @click="toggleAlwaysOnTop()">
            <Pin v-if="alwaysOnTop" class="w-3.5 h-3.5" />
            <PinOff v-else class="w-3.5 h-3.5" />
          </button>
          <button class="w-6 h-6 flex items-center justify-center rounded-full text-mini-player-pill-foreground transition-colors"
            @mouseenter="onVolumeEnter" @mouseleave="onVolumeLeave" @click="showVolume = !showVolume">
            <VolumeX v-if="store.muted" class="w-3.5 h-3.5" />
            <Volume2 v-else class="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      <!-- Glassmorphism panel: backdrop blur fading bottom → top -->
      <div class="glass-panel absolute bottom-0 left-0 right-0 pointer-events-none transition-opacity duration-200"
        :class="isHovered ? 'opacity-100' : 'opacity-0'" />

      <!-- Content overlay (hover-triggered) -->
      <div class="absolute bottom-0 left-0 right-0 px-3 pb-2 transition-opacity duration-200"
        :class="isHovered ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'"
        style="-webkit-app-region: no-drag">
        <MarqueeText :text="trackTitle" content-class="text font-semibold leading-tight text-white" />
        <MarqueeText :text="trackArtist" content-class="text-xs text-white/90 leading-tight mt-0.5" />

        <!-- Seek bar -->
        <div class="flex items-center gap-1.5 mt-2">
          <span class="text-[10px] text-white/80 tabular-nums w-7 text-right shrink-0">
            {{ formatTime(displayPosition) }}
          </span>
          <Slider :model-value="isSeeking ? seekValue : store.progressPercent" :min="0" :max="100" :step="0.1"
            class="flex-1" track-color-class="bg-mini-player-pill-foreground"
            track-background="var(--mini-player-pill-track)" thumb-color="var(--mini-player-pill-foreground)"
            @update:model-value="(v) => (seekValue = v)" @mousedown="onSeekStart" @mouseup="onSeekEnd" />
          <span class="text-[10px] text-white/80 tabular-nums w-7 shrink-0">
            {{ formatTime(store.duration) }}
          </span>
        </div>

        <!-- Controls: shuffle, prev, play/pause, next, loop -->
        <div class="flex items-center justify-center gap-4 mt-2 mb-1">
          <PlayerControlButton class="transition-colors"
            :class="store.shuffle ? 'text-white/80' : 'text-white/20 hover:text-white/70'" :active="store.shuffle"
            :show-indicator="appStore.showPlayerIndicator" dot-class="bg-white"
            @click="store.setShuffle(!store.shuffle)">
            <Shuffle class="w-3.5 h-3.5" />
          </PlayerControlButton>
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
          <PlayerControlButton class="transition-colors"
            :class="repeatActive ? 'text-white/80' : 'text-white/20 hover:text-white/70'" :active="repeatActive"
            :show-indicator="appStore.showPlayerIndicator" dot-class="bg-white" @click="store.cycleRepeat()">
            <component :is="repeatIcon" class="w-3.5 h-3.5" />
          </PlayerControlButton>
        </div>
      </div>
    </div>

    <section v-if="activePanel" data-test="mini-player-panel" class="flex-1 min-h-0 flex flex-col overflow-hidden"
      style="-webkit-app-region: no-drag">
      <MiniPlayerLyrics v-if="activePanel === 'lyrics'" :lyrics="store.lyrics?.content"
        :loading="store.lyricsLoading" :current-position="store.position" @seek="store.seek" />
      <div v-else class="h-full min-h-0 flex flex-col">
        <div class="flex items-center justify-between shrink-0 px-3 py-2 border-b border-[color:var(--border-glass)]">
          <div class="flex items-center gap-2 text-foreground">
            <ListMusic class="w-4 h-4 text-primary" />
            <span class="text-sm font-semibold">{{ t('player.up_next') }}</span>
            <Radio v-if="moodRadioStore.active" data-test="mini-player-mood-radio" class="w-3.5 h-3.5 text-primary" :title="t('player.mood_radio_active')" />
          </div>
          <button
            data-test="mini-player-scroll-to-current"
            class="p-1.5 rounded-full text-foreground/70 hover:bg-foreground/[0.04] hover:text-foreground transition-colors"
            :title="t('player.scroll_to_current')"
            @click="queueTrackList?.scrollToCurrentTrack()"
          >
            <Goal class="w-4 h-4" />
          </button>
        </div>
        <QueueTrackList ref="queueTrackList" scroll-to-current-on-mount :context-menu-options="{ miniPlayer: true, showRemoveFromQueue: true }" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.artwork-crossfade-incoming {
  mix-blend-mode: plus-lighter;
}
</style>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Glassmorphism: blurs artwork behind, strongest at bottom, fades to nothing at top */
.glass-panel {
  height: 260px;
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  background: linear-gradient(to top, rgba(0, 0, 0, 0.8), rgba(0, 0, 0, 0) 90%);
  -webkit-mask-image: linear-gradient(to top, #000 0%, #000 25%, transparent 100%);
  mask-image: linear-gradient(to top, #000 0%, #000 25%, transparent 100%);
  /* Force GPU compositing layer — kills backdrop-filter repaint flicker on macOS */
  transform: translateZ(0);
  will-change: transform, opacity;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  isolation: isolate;
}
</style>
