<script setup lang="ts">
import {
  ListMusic,
  Mic2,
  Minimize2,
} from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { usePlayerStore } from '../stores/player'
import { useDeviceStore } from '../stores/device'
import LivingArtworkBackground from './LivingArtworkBackground.vue'
import { TabSwitcher } from '@airmedy/ui'
import { useI18n } from 'vue-i18n'
import { getTrackDisplayTitle } from '@airmedy/utils'

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

// Queue panel holds a 50k-track virtual list; keep it mounted after first
// open (v-show toggle) instead of remounting on every open/close. Lyrics
// panel stays fully lazy (v-if) — cheap to mount, and should reload on open.
const hasOpenedQueue = ref(store.isQueueOpen)
watch(() => store.isQueueOpen, (open) => {
  if (open) hasOpenedQueue.value = true
})

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
const artworkMaxSize = computed(() => !showRightColumn.value || !appStore.highContrastLyrics ? 22 : 20)
const artworkCrossfade = computed(() =>
  appStore.blendArtworkDuringCrossfade ? store.artworkCrossfade : null,
)
</script>

<template>
  <div
    data-test="fullscreen-player"
    class="fixed inset-0 z-100 flex flex-col overflow-hidden bg-[#0A0A0A] select-none dark"
    @contextmenu.prevent="openQuickSettingsMenu">
    <LivingArtworkBackground :theme="store.theme" :is-playing="store.isPlaying"
      :artwork-crossfade="artworkCrossfade" />

    <div class="relative z-10 flex flex-col h-full text-white">
      <!-- Top bar -->
      <div class="flex items-center justify-between px-6 py-4" 
        style="-webkit-app-region: drag"
        @dblclick="deviceStore.toggleMaximize"
      >
        <div class="w-[120px]" style="-webkit-app-region: no-drag">
          <button class="p-2 rounded-full hover:bg-white/8 transition-all text-white/60 hover:text-white"
            :class="{ 'mt-8': deviceStore.isMac && !deviceStore.isWindowFullscreen }"
            @click="store.setPlayerMode('sticky')">
            <Minimize2 class="w-5 h-5" />
          </button>
        </div>
        <span class="text-xs font-semibold text-white/40 uppercase tracking-[0.2em]">
          {{ t('player.now_playing') }}
        </span>
        <div data-fullscreen-player-interactive="true" class="flex items-center gap-2 w-[120px] justify-end" style="-webkit-app-region: no-drag">
          <TabSwitcher v-model="activeTab" :options="tabOptions" />
        </div>
      </div>

      <!-- Main content -->
      <div class="flex-1 flex items-center justify-center px-8 w-full max-w-[1400px] mx-auto overflow-hidden">
        <div
          class="flex-1 flex flex-row items-center justify-center h-full transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)] relative @container"
          :class="!showRightColumn ? 'gap-0' : 'gap-12 lg:gap-16 xl:gap-20 2xl:gap-24 transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)]'">
          <!-- Left Column: Cover and Controls -->
          <div
            class="flex flex-col items-center justify-center transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)]"
            :class="!showRightColumn ? 'w-full max-w-lg' : 'w-1/2 max-w-md'">
            <div class="flex flex-col items-center justify-center gap-[clamp(0.75rem,2.5vh,1.5rem)] w-full min-h-0">
              <!-- Artwork -->
              <PlayerArtwork :artwork-url="store.artworkUrl" :track-title="trackTitle" :is-playing="store.isPlaying"
                :crossfade="artworkCrossfade"
                :max-size="artworkMaxSize"
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

          <!-- Right Column Spacer (animates layout) -->
          <div
            data-fullscreen-player-interactive="true"
            class="h-full transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)] relative flex items-center justify-center"
            :class="!showRightColumn ? 'w-0' : 'w-1/2 max-w-2xl'">

            <!-- Right Column Content (Queue or Lyrics) -->
            <!-- Two separate Transitions (each with exactly one child) so the queue panel can stay
                 mounted after first open (v-show toggle) while lyrics keeps mounting fresh on open. -->
            <Transition enter-active-class="transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)]"
              enter-from-class="opacity-0 translate-x-24" enter-to-class="opacity-100 translate-x-0"
              leave-active-class="transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)]"
              leave-from-class="opacity-100 translate-x-0" leave-to-class="opacity-0 translate-x-24">
              <PlayerQueuePanel v-if="hasOpenedQueue" v-show="store.isQueueOpen" key="queue" :queue="store.queue"
                @close="store.closeAllDrawers()" @play-track="(index) => store.playQueueIndex(index)" />
            </Transition>

            <Transition enter-active-class="transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)]"
              enter-from-class="opacity-0 translate-x-24" enter-to-class="opacity-100 translate-x-0"
              leave-active-class="transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)]"
              leave-from-class="opacity-100 translate-x-0" leave-to-class="opacity-0 translate-x-24">
              <PlayerLyricsPanel v-if="store.isLyricsOpen && appStore.highContrastLyrics" key="high-contrast-lyrics"
                :lyrics="store.lyrics?.content" :loading="store.lyricsLoading" :position="store.position"
                @close="store.closeAllDrawers()" @seek="(time) => store.seek(time)" />
              <ImmersiveLyricsPanel v-else-if="store.isLyricsOpen" key="immersive-lyrics"
                :lyrics="store.lyrics?.content" :loading="store.lyricsLoading" :position="store.position"
                @close="store.closeAllDrawers()" @seek="(time) => store.seek(time)" />
            </Transition>
          </div>
        </div>
      </div>
    </div>

    <TrackContextMenu ref="trackContextMenu" />
    <PlayerQuickSettingsMenu ref="quickSettingsMenu" />
  </div>
</template>
