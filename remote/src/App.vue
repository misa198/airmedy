<script setup lang="ts">
import { onMounted, onUnmounted, computed, ref } from 'vue'
import { Loader2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { connect, disconnect } from './ws'
import { usePlayerStore } from './stores/player'
import Auth from './components/Auth.vue'
import PlayerView from './components/PlayerView.vue'
import QueueView from './components/QueueView.vue'

const { t } = useI18n()
const store = usePlayerStore()
const showQueue = ref(false)

const isAuth = computed(() => store.authState === 'authenticated')
const needsAuth = computed(() =>
  store.authState === 'required' || store.authState === 'failed'
)
const currentTrackArtwork = computed(() => {
  const track = store.currentTrack
  return track?.artwork_key ? store.artworkUrl(track.artwork_key, 'lg') : null
})

// 1-column vs 2-column layout mode (lg = 1024px)
const windowWidth = ref(window.innerWidth)
const isLargeScreen = computed(() => windowWidth.value >= 1024)
const onResize = () => { windowWidth.value = window.innerWidth }

onMounted(() => {
  connect()
  window.addEventListener('resize', onResize)
})
onUnmounted(() => {
  disconnect()
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <!-- Auth screen (if connected but needs authentication) -->
  <Auth v-if="needsAuth && store.connected" />

  <!-- Player UI (if connected and authenticated) -->
  <div v-else-if="store.connected && isAuth" class="relative min-h-dvh min-h-[700px] flex flex-col bg-background select-none dark text-white">
    <!-- Blurred Artwork Background -->
    <div v-if="currentTrackArtwork" class="absolute inset-0 z-0 overflow-hidden pointer-events-none">
      <div
        class="absolute inset-0 bg-cover bg-center bg-no-repeat blur-[100px] opacity-30 scale-125 transform-gpu transition-all duration-1000"
        :style="{ backgroundImage: `url(${currentTrackArtwork})` }"></div>
      <div class="absolute inset-0 bg-gradient-to-b from-background/40 to-background/90"></div>
    </div>

    <div class="relative z-10 flex flex-col flex-1">
      <!-- Top bar -->
      <div class="flex items-center justify-center px-4 md:px-6 py-4">
        <div class="flex items-center gap-2 md:w-[120px] justify-center">
          <!-- Mode Switcher (Tab style) -->
          <div class="flex items-center bg-white/5 p-1 rounded-full border border-white/5 whitespace-nowrap">
            <button class="px-3 md:px-4 py-1.5 rounded-full text-xs font-bold transition-all"
              :class="!showQueue ? 'bg-white/10 text-white shadow-lg' : 'text-white/40 hover:text-white'"
              @click="showQueue = false">{{ t('app.player') }}</button>
            <button class="px-3 md:px-4 py-1.5 rounded-full text-xs font-bold transition-all whitespace-nowrap"
              :class="showQueue ? 'bg-white/10 text-white shadow-lg' : 'text-white/40 hover:text-white'"
              @click="showQueue = true">{{ t('app.queue') }}</button>
          </div>
        </div>
      </div>

      <!-- Main content -->
      <div class="h-[calc(100dvh-100px)] min-h-[300px] flex items-center justify-center px-4 md:px-8 w-full max-w-[1400px] mx-auto overflow-hidden">

        <!-- 1-column mode (mobile): fade transition between player and queue -->
        <template v-if="!isLargeScreen">
          <Transition name="view-fade" mode="out-in">
            <PlayerView v-if="!showQueue" key="player" :show-queue="false" class="w-full h-full" />
            <QueueView v-else key="queue" :show-queue="true" class="w-full h-full" />
          </Transition>
        </template>

        <!-- 2-column mode (desktop): side-by-side width transition -->
        <template v-else>
          <div
            class="h-full flex flex-row items-center justify-center transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)] relative w-full"
            :class="showQueue ? 'gap-12 xl:gap-24' : 'gap-0'">
            <PlayerView :show-queue="showQueue" />
            <QueueView :show-queue="showQueue" />
          </div>
        </template>

      </div>
    </div>
  </div>

  <!-- Loading / Connecting / Disconnected state -->
  <div v-else
    class="fixed inset-0 z-50 flex flex-col items-center justify-center gap-4 bg-background">
    <Loader2 class="w-10 h-10 text-primary animate-spin" />
    <p class="text-sm font-medium text-muted-foreground">{{ store.reconnecting ? t('app.reconnecting') : t('app.connecting') }}</p>
  </div>
</template>

<style scoped>
.view-fade-enter-active,
.view-fade-leave-active {
  transition: opacity 0.2s ease;
}
.view-fade-enter-from,
.view-fade-leave-to {
  opacity: 0;
}
</style>
