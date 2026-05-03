<script setup lang="ts">
import { ref, shallowRef, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { 
  Music, Settings as SettingsIcon,
  Sparkles, History, Ghost
} from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { usePlayerStore } from '@/stores/player'
import { Events } from '@wailsio/runtime'
import TrackCard from '@/components/TrackCard.vue'
import HomeSection from '@/components/HomeSection.vue'
import TrackContextMenu from '@/components/TrackContextMenu.vue'

const { t } = useI18n()
const router = useRouter()
const playerStore = usePlayerStore()

const loading = ref(true)
const recentlyPlayed = shallowRef<TrackDTO[]>([])
const mostListened = shallowRef<TrackDTO[]>([])
const leastListened = shallowRef<TrackDTO[]>([])
const hasTracks = ref(false)
const trackContextMenu = ref<InstanceType<typeof TrackContextMenu> | null>(null)

const randomGreeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return t('home.greeting.morning')
  if (hour < 17) return t('home.greeting.afternoon')
  if (hour < 21) return t('home.greeting.evening')
  return t('home.greeting.night')
})

const welcomePhrase = computed(() => {
  const phrases = [
    t('home.greeting.welcome'),
    t('home.greeting.ready'),
    t('home.greeting.discover')
  ]
  // We use a seed based on the current hour or similar to keep it somewhat stable 
  // but still "random" per session/hour
  const seed = new Date().getHours()
  return phrases[seed % phrases.length]
})

const fetchData = async () => {
  loading.value = true
  try {
    const count = await LibraryService.GetTrackCount()
    hasTracks.value = count > 0

    if (hasTracks.value) {
      const [recent, most, least] = await Promise.all([
        LibraryService.GetRecentlyPlayedTracks(28),
        LibraryService.GetMostListenedTracks(28),
        LibraryService.GetLeastListenedTracks(28)
      ])

      recentlyPlayed.value = (recent || []).filter((t): t is TrackDTO => t !== null)
      mostListened.value = (most || []).filter((t): t is TrackDTO => t !== null)
      leastListened.value = (least || []).filter((t): t is TrackDTO => t !== null)
    }
  } catch (err) {
    console.error('Failed to fetch home data:', err)
  } finally {
    loading.value = false
  }
}

let offSyncFinished: (() => void) | null = null

onMounted(() => {
  fetchData()
  offSyncFinished = Events.On('library:sync-finished', () => {
    fetchData()
  })
})

onUnmounted(() => {
  offSyncFinished?.()
})

const playTrack = (track: TrackDTO) => {
  playerStore.playTracks([track], 0)
}

const playAll = (tracks: TrackDTO[]) => {
  if (tracks.length > 0) {
    playerStore.playTracks(tracks, 0)
  }
}

const navigateToSettings = () => {
  router.push('/settings/library')
}

const navigateToTrack = (track: TrackDTO) => {
  if (track.album) {
    router.push(`/albums/${track.album.id}`)
  }
}

const navigateToArtist = (id: string) => {
  router.push(`/artists/${id}`)
}

const navigateToAlbum = (id: string) => {
  router.push(`/albums/${id}`)
}

const onTrackContextMenu = (e: MouseEvent, track: TrackDTO) => {
  trackContextMenu.value?.open(e, track)
}
</script>

<template>
  <div class="p-8 h-full overflow-y-auto custom-scrollbar">
    <div v-if="loading" class="h-full flex items-center justify-center">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
    </div>

    <div v-else-if="!hasTracks" class="h-full flex flex-col items-center justify-center text-center animate-in fade-in zoom-in duration-700">
      <div class="w-24 h-24 bg-foreground/5 rounded-3xl flex items-center justify-center mb-6 ring-1 ring-foreground/[0.06]">
        <Music class="w-12 h-12 text-foreground opacity-40" />
      </div>
      <h2 class="text-3xl font-bold mb-3">{{ t('home.empty.title') }}</h2>
      <p class="text-foreground opacity-60 max-w-md mb-8">{{ t('home.empty.description') }}</p>
      <button 
        @click="navigateToSettings"
        class="flex items-center gap-2 px-6 py-3 bg-primary text-primary-foreground rounded-lg font-medium hover:scale-105 transition-transform shadow-lg shadow-primary/20"
      >
        <SettingsIcon class="w-4 h-4" />
        {{ t('home.empty.action') }}
      </button>
    </div>

    <div v-else class="space-y-16 pb-12 animate-in fade-in duration-700">
      <!-- Greeting -->
      <header class="select-none">
        <h1 class="text-4xl font-bold tracking-tight mb-2">{{ randomGreeting }}</h1>
        <p class="text-xl text-foreground opacity-60">{{ welcomePhrase }}</p>
      </header>

      <!-- Keep Listening Carousel -->
      <HomeSection 
        :title="t('home.keep_listening')" 
        :icon="History" 
        :items="recentlyPlayed" 
        id="carousel-recent" 
        @play-all="playAll(recentlyPlayed)"
      >
        <template #default="{ item: track }">
          <TrackCard 
            :track="track" 
            @play="playTrack"
            @click="navigateToTrack"
            @artist-click="navigateToArtist"
            @album-click="navigateToAlbum"
            @contextmenu="onTrackContextMenu"
          />
        </template>
      </HomeSection>

      <!-- Smart Mix -->
      <HomeSection 
        :title="t('home.smart_mix')" 
        :icon="Sparkles" 
        :items="mostListened" 
        id="carousel-most" 
        @play-all="playAll(mostListened)"
      >
        <template #default="{ item: track }">
          <TrackCard 
            :track="track" 
            @play="playTrack"
            @click="navigateToTrack"
            @artist-click="navigateToArtist"
            @album-click="navigateToAlbum"
            @contextmenu="onTrackContextMenu"
          />
        </template>
      </HomeSection>

      <!-- Forgotten -->
      <HomeSection 
        :title="t('home.forgotten')" 
        :icon="Ghost" 
        :items="leastListened" 
        id="carousel-least" 
        @play-all="playAll(leastListened)"
      >
        <template #default="{ item: track }">
          <TrackCard 
            :track="track" 
            @play="playTrack"
            @click="navigateToTrack"
            @artist-click="navigateToArtist"
            @album-click="navigateToAlbum"
            @contextmenu="onTrackContextMenu"
          />
        </template>
      </HomeSection>
    </div>
  </div>

  <TrackContextMenu ref="trackContextMenu" />
</template>

<style scoped>
</style>
