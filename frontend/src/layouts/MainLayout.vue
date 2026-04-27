<script setup lang="ts">
import FullScreenPlayer from '@/components/FullScreenPlayer.vue'
import MiniPlayer from '@/components/MiniPlayer.vue'
import PlayerFooter from '@/components/PlayerFooter.vue'
import LyricsDrawer from '@/components/LyricsDrawer.vue'
import QueueDrawer from '@/components/QueueDrawer.vue'
import TrackInfoDrawer from '@/components/TrackInfoDrawer.vue'
import Sidebar from '@/components/Sidebar.vue'
import { usePlayerStore } from '@/stores/player'
import { useDeviceStore } from '@/stores/device'
import { RouterView } from 'vue-router'
import { ref, computed, onUnmounted } from 'vue'

const SIDEBAR_MIN_WIDTH = 230;
const SIDEBAR_MAX_WIDTH = 250;
const OVERLAY_BREAKPOINT = 1240;
const playerStore = usePlayerStore()
const deviceStore = useDeviceStore()

const isResizing = ref(false)

const windowWidth = ref(window.innerWidth)
const handleResize = () => { windowWidth.value = window.innerWidth }
window.addEventListener('resize', handleResize)

const overlayMode = computed(() => windowWidth.value < OVERLAY_BREAKPOINT)
const anyDrawerOpen = computed(() =>
  playerStore.isQueueOpen || playerStore.isLyricsOpen || playerStore.isTrackInfoOpen
)

const closeAllDrawers = () => {
  playerStore.closeAllDrawers()
}

const startResizing = (e: MouseEvent) => {
  e.preventDefault()
  isResizing.value = true
  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', stopResizing)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

const handleMouseMove = (e: MouseEvent) => {
  if (!isResizing.value) return
  const newWidth = Math.max(SIDEBAR_MIN_WIDTH, Math.min(SIDEBAR_MAX_WIDTH, e.clientX))
  playerStore.sidebarWidth = newWidth
}

const stopResizing = () => {
  isResizing.value = false
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', stopResizing)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onUnmounted(() => {
  stopResizing()
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="h-full w-full flex flex-col overflow-hidden bg-background text-foreground">
    <!-- Main Content Area -->
    <div class="flex-1 min-h-0 flex overflow-hidden">
      <!-- Sidebar Panel -->
      <aside :style="{ width: playerStore.sidebarWidth + 'px' }"
        class="h-full overflow-hidden flex-shrink-0 select-none">
        <Sidebar
          :class="(deviceStore.isMac || deviceStore.isWindows) && !deviceStore.isWindowFullscreen ? 'pt-10' : 'pt-4'" />
      </aside>

      <!-- Resizer Handle -->
      <div class="w-px bg-foreground/[0.06] cursor-col-resize hover:bg-foreground/10 transition-colors relative z-10"
        @mousedown="startResizing">
        <div class="absolute inset-y-0 -left-1 -right-1 cursor-col-resize" />
      </div>

      <!-- View Content Panel -->
      <main class="flex-1 min-w-0 flex flex-col overflow-hidden">
        <RouterView v-slot="{ Component }">
          <KeepAlive :max="3">
            <component :is="Component" />
          </KeepAlive>
        </RouterView>
      </main>

      <!-- Queue Sidebar (with transition) -->
      <template v-if="!overlayMode">
        <div class="h-full bg-background transition-all duration-300 ease-in-out overflow-hidden flex-shrink-0 pt-4"
          :class="[
            playerStore.isQueueOpen ? 'w-80 border-l border-foreground/[0.06]' : 'w-0 border-l-0 border-transparent',
          ]">
          <div class="w-80 h-full">
            <QueueDrawer />
          </div>
        </div>
      </template>

      <!-- Lyrics Sidebar (with transition) -->
      <template v-if="!overlayMode">
        <div class="h-full bg-background transition-all duration-300 ease-in-out overflow-hidden flex-shrink-0 pt-4"
          :class="[
            playerStore.isLyricsOpen ? 'w-80 border-l border-foreground/[0.06]' : 'w-0 border-l-0 border-transparent',
          ]">
          <div class="w-80 h-full">
            <LyricsDrawer />
          </div>
        </div>
      </template>

      <!-- Track Info Sidebar (with transition) -->
      <template v-if="!overlayMode">
        <div class="h-full bg-background transition-all duration-300 ease-in-out overflow-hidden flex-shrink-0 pt-4"
          :class="[
            playerStore.isTrackInfoOpen ? 'w-80 border-l border-foreground/[0.06]' : 'w-0 border-l-0 border-transparent',
          ]">
          <div class="w-80 h-full">
            <TrackInfoDrawer />
          </div>
        </div>
      </template>
    </div>

    <!-- Overlay drawers (< 1500px) -->
    <template v-if="overlayMode">
      <!-- Backdrop -->
      <Transition name="fade">
        <div
          v-if="anyDrawerOpen"
          class="fixed inset-0 z-40 bg-black/40 backdrop-blur-sm"
          @click="closeAllDrawers"
        />
      </Transition>

      <!-- Queue overlay -->
      <Transition name="slide-right">
        <div
          v-if="playerStore.isQueueOpen"
          class="fixed right-0 top-0 bottom-0 z-50 w-80 bg-background border-l border-foreground/[0.06] pt-4"
        >
          <QueueDrawer />
        </div>
      </Transition>

      <!-- Lyrics overlay -->
      <Transition name="slide-right">
        <div
          v-if="playerStore.isLyricsOpen"
          class="fixed right-0 top-0 bottom-0 z-50 w-80 bg-background border-l border-foreground/[0.06] pt-4"
        >
          <LyricsDrawer />
        </div>
      </Transition>

      <!-- Track Info overlay -->
      <Transition name="slide-right">
        <div
          v-if="playerStore.isTrackInfoOpen"
          class="fixed right-0 top-0 bottom-0 z-50 w-80 bg-background border-l border-foreground/[0.06] pt-4"
        >
          <TrackInfoDrawer />
        </div>
      </Transition>
    </template>

    <!-- Player (mode-dependent) -->
    <MiniPlayer v-if="playerStore.playerMode === 'mini'" />
    <PlayerFooter v-else-if="playerStore.playerMode === 'sticky'" />

    <!-- FullScreen player overlays the entire UI -->
    <Transition name="slide-up">
      <FullScreenPlayer v-show="playerStore.playerMode === 'fullscreen'" />
    </Transition>
  </div>
</template>

<style scoped>
/* Ensure the layout takes up the full screen and doesn't scroll at the root level */
:global(body) {
  @apply overflow-hidden;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.5s cubic-bezier(0.6, 0, 0.4, 1);
}

.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(100%);
}

.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
