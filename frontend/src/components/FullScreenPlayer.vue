<script setup lang="ts">
import {
  ListMusic,
  Mic2,
  Minimize2,
} from '@lucide/vue'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { usePlayerStore } from '../stores/player'
import { useDeviceStore } from '../stores/device'
import LivingArtworkBackground from './LivingArtworkBackground.vue'
import { TabSwitcher } from '@airmedy/ui'
import { useI18n } from 'vue-i18n'
import { getTrackDisplayTitle, hexToRgba } from '@airmedy/utils'

import PlayerArtwork from './player/PlayerArtwork.vue'
import PlayerTrackInfo from './player/PlayerTrackInfo.vue'
import PlayerSeekBar from './player/PlayerSeekBar.vue'
import PlayerPlaybackControls from './player/PlayerPlaybackControls.vue'
import PlayerVolumeControl from './player/PlayerVolumeControl.vue'
import PlayerQueuePanel from './player/PlayerQueuePanel.vue'
import PlayerLyricsPanel from './player/PlayerLyricsPanel.vue'
import ImmersiveLyricsPanel from './player/ImmersiveLyricsPanel.vue'
import TrackContextMenu from './TrackContextMenu.vue'
import PlayerQuickSettingsMenu from './player/PlayerQuickSettingsMenu.vue'
import { useAppStore } from '../stores/app'

const { t } = useI18n()
const store = usePlayerStore()
const deviceStore = useDeviceStore()
const appStore = useAppStore()

const trackContextMenu = ref<InstanceType<typeof TrackContextMenu> | null>(null)
const quickSettingsMenu = ref<InstanceType<typeof PlayerQuickSettingsMenu> | null>(null)
const queuePanel = ref<InstanceType<typeof PlayerQueuePanel> | null>(null)

// Queue panel holds a 50k-track virtual list; keep it mounted after first
// open (v-show toggle) instead of remounting on every open/close. Lyrics
// panel mounts fresh on open, but keeps its GPU renderer alive through leave.
const hasOpenedQueue = ref(store.isQueueOpen)
const renderLyrics = ref(store.isLyricsOpen)
watch(() => store.isLyricsOpen, (open) => {
  if (open) renderLyrics.value = true
})
watch(() => store.isQueueOpen, (open) => {
  if (!open) return
  hasOpenedQueue.value = true
  nextTick(() => queuePanel.value?.scrollToCurrentTrack())
})

function finishLyricsClose(event: TransitionEvent) {
  if (event.target === event.currentTarget && event.propertyName === 'opacity' && !store.isLyricsOpen) {
    renderLyrics.value = false
  }
}

function openContextMenu(e: MouseEvent) {
  if (!store.currentTrack) return
  trackContextMenu.value?.open(e, store.currentTrack, { excludePlayNext: true, excludeAddToQueue: true })
}

function openQuickSettingsMenu(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target.closest('button, [role="slider"], [data-fullscreen-player-interactive="true"]')) return
  quickSettingsMenu.value?.open(e)
}

function openTrackInfo() {
  if (!store.currentTrack) return
  if (store.playerMode === 'fullscreen') {
    store.setPlayerMode('sticky')
  }
  store.openTrackInfo(store.currentTrack)
}

const activeTab = computed({
  get: () => {
    if (store.isLyricsOpen) return 'lyrics'
    if (store.isQueueOpen) return 'queue'
    return null
  },
  set: (val: string | null) => {
    if (val === 'lyrics') {
      store.openLyrics()
    } else if (val === 'queue') {
      store.openQueue()
    } else {
      store.closeAllDrawers()
    }
  },
})

const tabOptions = computed(() => [
  { value: 'lyrics', label: t('player.lyrics'), icon: Mic2 },
  { value: 'queue', label: t('player.up_next'), icon: ListMusic },
])

const trackTitle = computed(() => store.currentTrack ? (getTrackDisplayTitle(store.currentTrack) || t('player.not_playing')) : t('player.not_playing'))
const trackArtist = computed(() =>
  store.currentTrack?.artists?.map((a) => a?.name).filter(Boolean).join(', ') ?? '',
)
const albumTitle = computed(() => store.currentTrack?.album?.title ?? '')

const showRightColumn = computed(() => store.isQueueOpen || store.isLyricsOpen)
const artworkCrossfade = computed(() =>
  appStore.blendArtworkDuringCrossfade ? store.artworkCrossfade : null,
)
const solidArtworkStyle = computed(() => store.theme ? {
  '--fullscreen-player-artwork-tint': hexToRgba(store.theme.backdrop, 0.4),
  '--fullscreen-player-artwork-tint-duration': `${artworkCrossfade.value?.durationMs ?? 1500}ms`,
} : undefined)

function handleKeyDown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || store.playerMode !== 'fullscreen') return

  event.preventDefault()
  store.setPlayerMode('sticky')
}

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown)
})
</script>

<template>
  <div
    data-test="fullscreen-player"
    class="fixed inset-0 z-100 flex flex-col overflow-hidden bg-[#0A0A0A] select-none dark"
    @contextmenu.prevent="openQuickSettingsMenu">
    <LivingArtworkBackground v-if="appStore.livingArtworkBackground" :theme="store.theme" :is-playing="store.isPlaying"
      :artwork-crossfade="artworkCrossfade" />
    <div v-else data-test="solid-artwork-background" class="fullscreen-player-solid-background" :style="solidArtworkStyle" />

    <div class="relative z-10 flex flex-col h-full text-white">
      <!-- Top bar -->
      <div class="flex items-center justify-between px-6 py-4" 
        style="-webkit-app-region: drag"
        @dblclick="deviceStore.toggleMaximize"
      >
        <div class="w-[160px]" style="-webkit-app-region: no-drag">
          <button class="p-2 rounded-full hover:bg-white/8 transition-all text-white/60 hover:text-white"
            :class="{ 'mt-8': deviceStore.isMac && !deviceStore.isWindowFullscreen }"
            @click="store.setPlayerMode('sticky')">
            <Minimize2 class="w-5 h-5" />
          </button>
        </div>
        <span class="text-xs font-semibold text-white/40 uppercase tracking-[0.2em]">
          {{ t('player.now_playing') }}
        </span>
        <div data-fullscreen-player-interactive="true" class="flex items-center gap-2 w-[160px] justify-end" style="-webkit-app-region: no-drag">
          <div id="fullscreen-lyrics-actions" class="contents" />
          <TabSwitcher v-model="activeTab" :options="tabOptions" />
        </div>
      </div>

      <!-- Main content -->
      <div data-test="fullscreen-player-content"
        class="flex-1 flex items-center justify-center px-8 w-full max-w-[1400px] mx-auto overflow-visible">
        <div class="relative h-full w-full @container">
          <!-- Left Column: Cover and Controls -->
          <div
            data-test="fullscreen-player-left-column"
            class="fullscreen-player-motion absolute inset-y-0 left-0 w-1/2 flex flex-col items-center justify-center transform-gpu"
            :class="showRightColumn ? 'translate-x-0' : 'translate-x-1/2'">
            <div class="flex flex-col items-center justify-center gap-[clamp(0.75rem,2.5vh,1.5rem)] w-full max-w-lg min-h-0">
              <!-- Artwork -->
              <PlayerArtwork :artwork-url="store.artworkUrl" :track-title="trackTitle" :is-playing="store.isPlaying"
                :crossfade="artworkCrossfade"
                :max-size="24"
                data-fullscreen-player-interactive="true"
                class="-translate-y-4 cursor-pointer"
                @click="openTrackInfo"
                @contextmenu.prevent.stop="openContextMenu" />

              <!-- Track info -->
              <PlayerTrackInfo :title="trackTitle" :artist="trackArtist" :album="albumTitle"
                data-fullscreen-player-interactive="true"
                class="cursor-pointer"
                @click="openTrackInfo"
                @contextmenu.prevent.stop="openContextMenu" />

              <!-- Seek bar -->
              <PlayerSeekBar :progress-percent="store.progressPercent" :position="store.position"
                :duration="store.duration" data-fullscreen-player-interactive="true" @seek="(v) => store.seek(v)" />

              <!-- Controls -->
              <PlayerPlaybackControls :is-playing="store.isPlaying" :shuffle="store.shuffle"
                :repeat-mode="store.repeatMode" :show-indicator="appStore.showPlayerIndicator"
                data-fullscreen-player-interactive="true"
                @toggle-play="store.togglePlayPause()" @next="store.next()"
                @previous="store.previous()" @toggle-shuffle="store.setShuffle(!store.shuffle)"
                @cycle-repeat="store.cycleRepeat()" />

              <!-- Volume -->
              <PlayerVolumeControl :volume="store.volume" :muted="store.muted"
                data-fullscreen-player-interactive="true"
                @update:volume="(v) => store.setVolume(v)" @update:muted="(v) => store.setMuted(v)" />
            </div>
          </div>

          <!-- Fixed geometry keeps panel motion on the compositor. -->
          <div
            data-test="fullscreen-player-right-column"
            data-fullscreen-player-interactive="true"
            class="absolute inset-y-0 right-0 w-1/2 max-w-2xl [contain:layout]"
            :class="showRightColumn ? 'pointer-events-auto' : 'pointer-events-none'">

            <!-- Right Column Content (Queue or Lyrics) -->
            <div data-test="fullscreen-queue-panel-motion"
              class="fullscreen-player-motion absolute inset-0"
              :class="store.isQueueOpen ? 'opacity-100 translate-x-0' : 'opacity-0 translate-x-1/2'"
              :inert="!store.isQueueOpen" :aria-hidden="!store.isQueueOpen">
              <PlayerQueuePanel v-if="hasOpenedQueue" ref="queuePanel" :queue="store.queue"
                @close="store.closeAllDrawers()" @play-track="(index) => store.playQueueIndex(index)" />
            </div>

            <div data-test="fullscreen-lyrics-panel-motion"
              class="fullscreen-player-motion absolute inset-0"
              :class="store.isLyricsOpen ? 'opacity-100 translate-x-0' : 'opacity-0 translate-x-1/2'"
              :inert="!store.isLyricsOpen" :aria-hidden="!store.isLyricsOpen"
              @transitionend="finishLyricsClose">
              <PlayerLyricsPanel v-if="renderLyrics && appStore.highContrastLyrics" key="high-contrast-lyrics"
                :lyrics="store.lyrics?.content" :loading="store.lyricsLoading" :position="store.position"
                @close="store.closeAllDrawers()" @seek="(time) => store.seek(time)" />
              <ImmersiveLyricsPanel v-else-if="renderLyrics" key="immersive-lyrics"
                :lyrics="store.lyrics?.content" :loading="store.lyricsLoading" :position="store.position"
                @close="store.closeAllDrawers()" @seek="(time) => store.seek(time)" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <TrackContextMenu ref="trackContextMenu" />
    <PlayerQuickSettingsMenu ref="quickSettingsMenu" />
  </div>
</template>

<style scoped>
.fullscreen-player-solid-background {
  position: absolute;
  inset: 0;
  background-color: var(--fullscreen-player-solid-background);
  background-image: linear-gradient(var(--fullscreen-player-artwork-tint), var(--fullscreen-player-artwork-tint)),
    linear-gradient(var(--fullscreen-player-solid-overlay), var(--fullscreen-player-solid-overlay));
  transition: --fullscreen-player-artwork-tint var(--fullscreen-player-artwork-tint-duration) ease-in-out;
}

.fullscreen-player-motion {
  transition: translate 0.5s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: translate, opacity;
}
</style>
