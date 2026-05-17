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
  Maximize,
  Music,
  ListMusic,
  Mic2,
  PictureInPicture2,
} from 'lucide-vue-next'
import LazyImg from '@/components/LazyImg.vue'
import { usePlayerStore } from '../stores/player'
import { RepeatMode } from '../../bindings/airmedy/internal/domain/models'
import { formatTime, getTrackDisplayTitle } from '../lib/utils'
import { Slider } from '@/components/ui/slider'
import * as WindowService from '../../bindings/airmedy/internal/infra/wails/windowservice'
import { useI18n } from 'vue-i18n'
import TrackContextMenu from './TrackContextMenu.vue'
import MarqueeText from './MarqueeText.vue'

const { t } = useI18n()
const store = usePlayerStore()

const trackContextMenu = ref<InstanceType<typeof TrackContextMenu> | null>(null)

function openArtworkContextMenu(e: MouseEvent) {
  if (!store.currentTrack) return
  trackContextMenu.value?.open(e, store.currentTrack, { excludeDelete: true, excludePlayNext: true })
}

const isSeeking = ref(false)
const seekValue = ref(0)

const displayPosition = computed(() =>
  isSeeking.value ? (seekValue.value / 100) * store.duration : store.position,
)

const trackTitle = computed(() => store.currentTrack ? (getTrackDisplayTitle(store.currentTrack) || t('player.not_playing')) : t('player.not_playing'))
const trackArtist = computed(() => {
  const artists = store.currentTrack?.artists
  if (!artists || artists.length === 0) return t('player.select_track')
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

</script>

<template>
  <div
    class="h-[72px] bg-background border-t border-foreground/[0.06] flex items-center justify-between px-6 gap-6 select-none">
    <!-- Track Info -->
    <div class="flex items-center justify-start gap-3 w-1/4 min-w-[200px]">
      <div
        class="w-12 h-12 rounded-lg overflow-hidden flex-shrink-0 shadow-lg ring-1 ring-foreground/10 cursor-pointer transition-transform hover:scale-105 active:scale-95"
        @click="store.openTrackInfo(store.currentTrack)" @contextmenu.prevent="openArtworkContextMenu">
        <LazyImg v-if="store.artworkUrlSm" :src="store.artworkUrlSm" :alt="trackTitle"
          class="w-full h-full object-cover" />
        <div v-else class="w-full h-full bg-foreground/5 flex items-center justify-center">
          <Music class="w-5 h-5 text-foreground opacity-40" />
        </div>
      </div>
      <div class="flex flex-col min-w-0 flex-1">
        <MarqueeText :text="trackTitle" content-class="font-medium text-sm leading-tight" />
        <MarqueeText :text="trackArtist" content-class="text-xs text-foreground opacity-60 leading-tight mt-0.5" />
      </div>
    </div>

    <!-- Playback Controls -->
    <div class="flex-1 flex flex-col items-center gap-2 max-w-[600px]">
      <div class="flex items-center gap-5">
        <button class="transition-opacity"
          :class="store.shuffle ? 'text-primary opacity-100' : 'text-foreground opacity-60 hover:text-foreground opacity-50'"
          @click="store.setShuffle(!store.shuffle)" :title="t('player.shuffle')">
          <Shuffle class="w-4 h-4" />
        </button>
        <button class="text-foreground opacity-50 hover:text-foreground transition-colors" @click="store.previous()"
          :title="t('player.previous')">
          <SkipBack class="w-5 h-5 fill-current" />
        </button>
        <button
          class="w-8 h-8 bg-primary rounded-full flex items-center justify-center hover:scale-105 transition-transform"
          @click="store.togglePlayPause()" :title="store.isPlaying ? t('player.pause') : t('player.play')">
          <Pause v-if="store.isPlaying" class="w-4 h-4 fill-current text-primary-foreground" />
          <Play v-else class="w-4 h-4 fill-current text-primary-foreground ml-0.5" />
        </button>
        <button class="text-foreground opacity-50 hover:text-foreground transition-colors" @click="store.next()"
          :title="t('player.next')">
          <SkipForward class="w-5 h-5 fill-current" />
        </button>
        <button class="transition-colors"
          :class="repeatActive ? 'text-primary' : 'text-foreground opacity-60 hover:text-foreground opacity-50'"
          @click="store.cycleRepeat()" :title="t('player.repeat')">
          <component :is="repeatIcon" class="w-4 h-4" />
        </button>
      </div>

      <!-- Seek bar -->
      <div class="w-full flex items-center gap-2">
        <span class="text-[10px] text-foreground opacity-50 tabular-nums w-8 text-right">
          {{ formatTime(displayPosition) }}
        </span>
        <Slider :model-value="isSeeking ? seekValue : store.progressPercent" :min="0" :max="100" :step="0.1"
          class="flex-1" @update:model-value="(v) => (seekValue = v)" @mousedown="onSeekStart" @mouseup="onSeekEnd"
          @touchstart="onSeekStart" @touchend="onSeekEnd" />
        <span class="text-[10px] text-foreground opacity-50 tabular-nums w-8">
          {{ formatTime(store.duration) }}
        </span>
      </div>
    </div>

    <!-- Volume & Options -->
    <div class="flex items-center justify-end gap-4 w-1/4 min-w-[200px]">
      <div class="flex items-center gap-2 w-28">
        <button class="text-foreground opacity-60 hover:text-foreground opacity-50 transition-colors flex-shrink-0"
          @click="store.setMuted(!store.muted)" :title="store.muted ? t('player.unmute') : t('player.mute')">
          <VolumeX v-if="store.muted" class="w-4 h-4" />
          <Volume2 v-else class="w-4 h-4" />
        </button>
        <Slider :model-value="store.muted ? 0 : store.volume" :min="0" :max="1" :step="0.01" class="flex-1"
          @update:model-value="(v) => store.setVolume(v)" />
      </div>
      <button class="transition-colors"
        :class="store.isLyricsOpen ? 'text-primary' : 'text-foreground opacity-60 hover:text-foreground opacity-50'"
        @click="store.toggleLyrics()" :title="t('player.lyrics')">
        <Mic2 class="w-4 h-4" />
      </button>
      <button class="transition-colors"
        :class="store.isQueueOpen ? 'text-primary' : 'text-foreground opacity-60 hover:text-foreground opacity-50'"
        @click="store.toggleQueue()" :title="t('player.queue')">
        <ListMusic class="w-4 h-4" />
      </button>
      <button class="text-foreground opacity-60 hover:text-foreground opacity-50 transition-colors"
        @click="WindowService.ToggleMiniPlayer()" :title="t('player.mini_player')">
        <PictureInPicture2 class="w-4 h-4" />
      </button>
      <button class="text-foreground opacity-60 hover:text-foreground opacity-50 transition-colors"
        @click="store.playerMode = 'fullscreen'" :title="t('player.fullscreen')">
        <Maximize class="w-4 h-4" />
      </button>
    </div>
  </div>

  <TrackContextMenu ref="trackContextMenu" />
</template>
