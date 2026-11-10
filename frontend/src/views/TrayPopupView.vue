<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Events } from '@wailsio/runtime'
import { Music, SquareArrowOutUpRight } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { formatTime, getTrackDisplayTitle, hexToRgba } from '@airmedy/utils'
import { MarqueeText, Slider } from '@airmedy/ui'
import { usePlayerStore } from '@/stores/player'
import { useAppStore } from '@/stores/app'
import LazyImg from '@/components/LazyImg.vue'
import LivingArtworkBackground from '@/components/LivingArtworkBackground.vue'
import PlayerFavoriteButton from '@/components/player/PlayerFavoriteButton.vue'
import PlayerPlaybackControls from '@/components/player/PlayerPlaybackControls.vue'
import * as WindowService from '../../bindings/airmedy/internal/infra/wails/windowservice'

const { t } = useI18n()
const player = usePlayerStore()
const app = useAppStore()
const isSeeking = ref(false)
const seekValue = ref(0)
const displayPosition = computed(() => isSeeking.value ? seekValue.value * player.duration / 100 : player.position)
const title = computed(() => player.currentTrack ? getTrackDisplayTitle(player.currentTrack) || t('player.not_playing') : t('player.not_playing'))
const artist = computed(() => player.currentTrack?.artists?.filter((artist): artist is NonNullable<typeof artist> => !!artist).map(artist => artist.name).join(', ') || t('player.select_track'))
const backgroundStyle = computed(() => app.trayBackgroundMode !== 'monochrome' && player.theme ? {
  '--fullscreen-player-artwork-tint': hexToRgba(player.theme.backdrop, 0.4),
} : undefined)
const windowFocused = ref(document.hasFocus())
const windowVisible = ref(false)
const solidTintReady = ref(false)
let solidTintFrame = 0
let offVisibility: (() => void) | undefined
watch([() => app.trayBackgroundMode, () => player.theme, windowVisible], ([mode, theme, visible]) => {
  if (mode !== 'solid' || !visible) {
    cancelAnimationFrame(solidTintFrame)
    solidTintReady.value = false
  } else if (theme && !solidTintReady.value) {
    cancelAnimationFrame(solidTintFrame)
    solidTintFrame = requestAnimationFrame(() => {
      solidTintFrame = requestAnimationFrame(() => { solidTintReady.value = true })
    })
  }
}, { immediate: true, flush: 'post' })

async function finishSeek() {
  if (!isSeeking.value) return
  try {
    await player.seek(seekValue.value * player.duration / 100)
  } finally {
    isSeeking.value = false
  }
}

function updateSeek(value: number) {
  seekValue.value = value
  if (!isSeeking.value) void player.seek(value * player.duration / 100)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') Events.Emit('tray-popup:hide')
}

async function showAirmedy() {
  await WindowService.ShowCurrent()
  Events.Emit('tray-popup:hide')
}

function onFocus() { windowFocused.value = true }
function onBlur() { windowFocused.value = false }

onMounted(() => {
  offVisibility = Events.On('tray-popup:visibility', (event: Events.WailsEvent) => { windowVisible.value = event.data === true })
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('focus', onFocus)
  window.addEventListener('blur', onBlur)
})
onUnmounted(() => {
  offVisibility?.()
  cancelAnimationFrame(solidTintFrame)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('focus', onFocus)
  window.removeEventListener('blur', onBlur)
})
</script>

<template>
  <div
    class="tray-artwork-background relative flex h-full w-full flex-col overflow-hidden px-4 py-4 text-mini-player-pill-foreground select-none"
    :class="{ 'tray-monochrome': app.trayBackgroundMode === 'monochrome', 'tray-solid-tint-ready': app.trayBackgroundMode === 'solid' && solidTintReady }"
    :style="backgroundStyle">
    <LivingArtworkBackground v-if="windowFocused && app.trayBackgroundMode === 'living'" :theme="player.theme" :is-playing="player.isPlaying" />
    <div class="relative z-10 flex min-w-0 items-center gap-3 mb-2">
      <div
        class="flex h-[58px] w-[58px] shrink-0 items-center justify-center overflow-hidden rounded-lg border border-[color:var(--mini-player-pill-border)] bg-mini-player-pill-background shadow-[var(--tray-artwork-shadow)]"
        @click="showAirmedy">
        <LazyImg v-if="player.artworkUrlSm" :src="player.artworkUrlSm" :alt="title"
          class="h-full w-full object-cover" />
        <Music v-else class="h-6 w-6 text-mini-player-pill-foreground/60" />
      </div>
      <div class="min-w-0 flex-1" @click="showAirmedy">
        <MarqueeText :text="title" :title="title" fade content-class="text-[13px] font-semibold" />
        <MarqueeText :text="artist" :title="artist" fade content-class="text-xs text-mini-player-pill-foreground/60" />
      </div>
      <div class="flex shrink-0 items-center gap-1">
        <PlayerFavoriteButton mini />
        <!-- <button type="button"
          class="flex h-7 w-7 items-center justify-center rounded-full text-mini-player-pill-foreground/60 transition-all duration-300 hover:text-mini-player-pill-foreground/90 focus-visible:outline-2 focus-visible:outline-mini-player-pill-foreground/70"
          :title="t('player.show_airmedy')" :aria-label="t('player.show_airmedy')" @click="showAirmedy">
          <SquareArrowOutUpRight class="h-4 w-4" />
        </button> -->
      </div>
    </div>

    <div data-test="tray-progress" class="relative z-10 w-full">
      <Slider v-if="player.currentTrack && player.duration > 0"
        :model-value="isSeeking ? seekValue : player.progressPercent" :min="0" :max="100" :step="0.1" class="w-full"
        track-color-class="bg-mini-player-pill-foreground" track-background="var(--mini-player-pill-track)"
        thumb-color="var(--mini-player-pill-foreground)" :aria-label="t('player.seek')" @update:model-value="updateSeek"
        @mousedown="isSeeking = true" @touchstart="isSeeking = true" @mouseup="finishSeek" @touchend="finishSeek" />
      <div v-else class="h-4 w-full flex items-center">
        <div class="h-1 w-full rounded-full bg-mini-player-pill-foreground/15" />
      </div>
      <div data-test="tray-time-labels"
        class="mt-0.5 flex justify-between text-[11px] tabular-nums text-mini-player-pill-foreground/60">
        <span>{{ formatTime(displayPosition) }}</span>
        <span>{{ formatTime(player.duration) }}</span>
      </div>
    </div>

    <div class="relative z-10 w-full flex justify-center">
      <PlayerPlaybackControls class="self-center" :is-playing="player.isPlaying" :shuffle="player.shuffle"
        :repeat-mode="player.repeatMode" :show-indicator="app.showPlayerIndicator" :disabled="!player.currentTrack"
        compact @toggle-play="player.togglePlayPause()" @previous="player.previous()" @next="player.next()"
        @toggle-shuffle="player.setShuffle(!player.shuffle)" @cycle-repeat="player.cycleRepeat()" />
    </div>
  </div>
</template>

<style scoped>
.tray-artwork-background {
  background-color: var(--tray-player-background);
  background-image: linear-gradient(var(--fullscreen-player-artwork-tint), var(--fullscreen-player-artwork-tint)),
    linear-gradient(var(--tray-player-overlay), var(--tray-player-overlay));
}
.tray-solid-tint-ready {
  transition: --fullscreen-player-artwork-tint 2s cubic-bezier(0.33, 0.64, 0.67, 0.87);
}
.tray-monochrome {
  --tray-artwork-shadow: none;
  --mini-player-pill-foreground: var(--text-main);
  --mini-player-pill-active: var(--text-main);
  --mini-player-pill-active-foreground: var(--bg-main);
  --mini-player-pill-background: var(--bg-glass);
  --mini-player-pill-border: var(--border-glass);
  --color-mini-player-pill-foreground: var(--mini-player-pill-foreground);
  --color-mini-player-pill-active: var(--mini-player-pill-active);
  --color-mini-player-pill-active-foreground: var(--mini-player-pill-active-foreground);
  --color-mini-player-pill-background: var(--mini-player-pill-background);
  --color-mini-player-pill-border: var(--mini-player-pill-border);
  --mini-player-pill-track: color-mix(in srgb, var(--text-main) 15%, transparent);
  background-color: var(--bg-main);
  background-image: none;
  transition: none;
}
</style>
