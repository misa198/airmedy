<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { 
  ResizableHandle, 
  ResizablePanel, 
  ResizablePanelGroup 
} from '@/components/ui/resizable'
import Sidebar from '@/components/Sidebar.vue'
import PlayerFooter from '@/components/PlayerFooter.vue'
import MiniPlayer from '@/components/MiniPlayer.vue'
import FullScreenPlayer from '@/components/FullScreenPlayer.vue'
import QueueDrawer from '@/components/QueueDrawer.vue'
import { RouterView } from 'vue-router'
import { usePlayerStore } from '@/stores/player'

const playerStore = usePlayerStore()
import { GetPlatform } from '../../bindings/changeme/internal/infra/wails/greetservice'

const isMac = ref(false)

onMounted(async () => {
  try {
    const platform = await GetPlatform()
    isMac.value = platform === 'darwin'
  } catch (err) {
    console.error('Failed to get platform:', err)
  }
})
</script>

<template>
  <div class="h-full w-full flex flex-col overflow-hidden bg-background text-foreground">
    <!-- Main Content Area -->
    <div class="flex-1 min-h-0 flex overflow-hidden">
      <ResizablePanelGroup direction="horizontal">
        <!-- Sidebar Panel -->
        <ResizablePanel 
          :default-size="25" 
          :min-size="24" 
          :max-size="35"
          class="h-full overflow-hidden"
        >
          <Sidebar :class="isMac ? 'pt-10' : ''" />
        </ResizablePanel>
        
        <ResizableHandle with-handle />
        
        <!-- View Content Panel -->
        <ResizablePanel 
          :default-size="75" 
          class="h-full flex flex-col overflow-hidden"
        >
          <main :class="['flex-1 overflow-y-auto overflow-x-hidden', isMac ? 'pt-10' : '']">
            <RouterView />
          </main>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>

    <!-- Player (mode-dependent) -->
    <MiniPlayer v-if="playerStore.playerMode === 'mini'" />
    <PlayerFooter v-else-if="playerStore.playerMode === 'sticky'" />

    <!-- FullScreen player overlays the entire UI -->
    <FullScreenPlayer v-if="playerStore.playerMode === 'fullscreen'" />

    <!-- Queue Drawer (slides in from right, above footer) -->
    <QueueDrawer />
  </div>
</template>

<style scoped>
/* Ensure the layout takes up the full screen and doesn't scroll at the root level */
:global(body) {
  @apply overflow-hidden;
}
</style>
