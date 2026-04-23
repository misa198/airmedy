<script setup lang="ts">
import FullScreenPlayer from '@/components/FullScreenPlayer.vue'
import MiniPlayer from '@/components/MiniPlayer.vue'
import PlayerFooter from '@/components/PlayerFooter.vue'
import QueueDrawer from '@/components/QueueDrawer.vue'
import Sidebar from '@/components/Sidebar.vue'
import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup
} from '@/components/ui/resizable'
import { usePlayerStore } from '@/stores/player'
import { useDeviceStore } from '@/stores/device'
import { RouterView } from 'vue-router'

const playerStore = usePlayerStore()
const deviceStore = useDeviceStore()
</script>

<template>
  <div class="h-full w-full flex flex-col overflow-hidden bg-background text-foreground">
    <!-- Main Content Area -->
    <div class="flex-1 min-h-0 flex overflow-hidden">
      <ResizablePanelGroup direction="horizontal">
        <!-- Sidebar Panel -->
        <ResizablePanel :default-size="20" :min-size="20" :max-size="35" class="h-full overflow-hidden">
          <Sidebar :class="deviceStore.isMac && !deviceStore.isWindowFullscreen ? 'pt-10' : 'pt-4'" />
        </ResizablePanel>

        <ResizableHandle with-handle />

        <!-- View Content Panel -->
        <ResizablePanel :default-size="80" class="h-full flex flex-col overflow-hidden">
          <main :class="['flex-1 overflow-hidden']">
            <RouterView v-slot="{ Component }">
              <KeepAlive :max="10">
                <component :is="Component" />
              </KeepAlive>
            </RouterView>
          </main>
        </ResizablePanel>
      </ResizablePanelGroup>

      <!-- Queue Sidebar (with transition) -->
      <div class="h-full bg-background transition-all duration-300 ease-in-out overflow-hidden flex-shrink-0" :class="[
        playerStore.isQueueOpen && playerStore.playerMode !== 'fullscreen'
          ? 'w-80 border-l border-white/[0.06]'
          : 'w-0 border-l-0 border-transparent'
      ]">
        <div class="w-80 h-full">
          <QueueDrawer />
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
