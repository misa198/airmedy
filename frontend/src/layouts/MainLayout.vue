<script setup lang="ts">
import FullScreenPlayer from '@/components/FullScreenPlayer.vue'
import MiniPlayer from '@/components/MiniPlayer.vue'
import PlayerFooter from '@/components/PlayerFooter.vue'
import LyricsDrawer from '@/components/LyricsDrawer.vue'
import QueueDrawer from '@/components/QueueDrawer.vue'
import TrackInfoDrawer from '@/components/TrackInfoDrawer.vue'
import UpdateDialog from '@/components/UpdateDialog.vue'
import Sidebar from '@/components/Sidebar.vue'
import { usePlayerStore } from '@/stores/player'
import { useDeviceStore } from '@/stores/device'
import { useAppStore } from '@/stores/app'
import { RouterView } from 'vue-router'
import { ref, onUnmounted } from 'vue'

const SIDEBAR_MIN_WIDTH = 230;
const SIDEBAR_MAX_WIDTH = 250;
const playerStore = usePlayerStore()
const deviceStore = useDeviceStore()
const appStore = useAppStore()

const isResizing = ref(false)
const showUpdateDialog = ref(false)

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
})
</script>

<template>
  <div class="h-full w-full flex flex-col overflow-hidden bg-background text-foreground">
    <!-- Window Drag Area (Double click to toggle maximize) -->
    <div
      v-if="!deviceStore.isWindowFullscreen && playerStore.playerMode !== 'fullscreen'"
      class="fixed top-0 left-0 right-0 h-10 z-[60] select-none pointer-events-none"
    >
      <div 
        class="w-full h-full pointer-events-auto"
        style="-webkit-app-region: drag"
        @dblclick="deviceStore.toggleMaximize"
      />
    </div>

    <!-- Main Content Area -->
    <div class="flex-1 min-h-0 flex overflow-hidden">
      <!-- Sidebar Panel -->
      <aside :style="{ width: playerStore.sidebarWidth + 'px' }"
        class="h-full overflow-hidden flex-shrink-0 select-none">
        <Sidebar
          :class="deviceStore.isMac && !deviceStore.isWindowFullscreen ? 'pt-10' : 'pt-4'" />
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

      <!-- Queue Sidebar -->
      <div class="h-full bg-background transition-[width] duration-300 ease-in-out overflow-hidden flex-shrink-0 pt-4"
        :class="[
          playerStore.isQueueOpen ? 'w-80 border-l border-foreground/[0.06]' : 'w-0 border-l-0 border-transparent',
        ]">
        <div class="w-80 h-full">
          <QueueDrawer />
        </div>
      </div>

      <!-- Lyrics Sidebar -->
      <div class="h-full bg-background transition-[width] duration-300 ease-in-out overflow-hidden flex-shrink-0 pt-4"
        :class="[
          playerStore.isLyricsOpen ? 'w-80 border-l border-foreground/[0.06]' : 'w-0 border-l-0 border-transparent',
        ]">
        <div class="w-80 h-full">
          <LyricsDrawer />
        </div>
      </div>

      <!-- Track Info Sidebar -->
      <div class="h-full bg-background transition-[width] duration-300 ease-in-out overflow-hidden flex-shrink-0 pt-4"
        :class="[
          playerStore.isTrackInfoOpen ? 'w-80 border-l border-foreground/[0.06]' : 'w-0 border-l-0 border-transparent',
        ]">
        <div class="w-80 h-full">
          <TrackInfoDrawer />
        </div>
      </div>
    </div>

    <!-- Player (mode-dependent) -->
    <MiniPlayer v-if="playerStore.playerMode === 'mini'" />
    <PlayerFooter v-else-if="playerStore.playerMode === 'sticky'" />

    <!-- FullScreen player overlays the entire UI -->
    <Transition name="slide-up">
      <FullScreenPlayer v-show="playerStore.playerMode === 'fullscreen'" />
    </Transition>

    <UpdateDialog 
      v-model:open="appStore.isUpdateDialogOpen"
      :update-info="appStore.updateInfo"
    />
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

</style>
