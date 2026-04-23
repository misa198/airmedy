<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import type { AlbumDTO, TrackDTO } from '../../bindings/changeme/internal/domain/models'
import TrackTable from '../components/TrackTable.vue'
import { Disc, User, Play, Clock, Calendar, ArrowLeft, MoreVertical, Music } from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'

const playerStore = usePlayerStore()

const route = useRoute()
const router = useRouter()
const album = ref<AlbumDTO | null>(null)
const tracks = ref<TrackDTO[]>([])
const isLoading = ref(true)

const loadAlbumDetails = async (id: string) => {
  isLoading.value = true
  try {
    const [albumData, tracksData] = await Promise.all([
      LibraryService.GetAlbumByID(id),
      LibraryService.GetTracksByAlbumID(id)
    ])
    album.value = albumData
    tracks.value = tracksData.filter((t): t is TrackDTO => t !== null)
  } catch (err) {
    console.error('Failed to load album details:', err)
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  const id = route.params.id as string
  if (id) loadAlbumDetails(id)
})

watch(() => route.params.id, (newId) => {
  if (newId) loadAlbumDetails(newId as string)
})

const formatTotalDuration = (tracks: TrackDTO[]) => {
  const totalSeconds = tracks.reduce((acc, t) => acc + (t.duration || 0), 0)
  const hours = Math.floor(totalSeconds / 3600)
  const mins = Math.floor((totalSeconds % 3600) / 60)
  if (hours > 0) return `${hours} hr ${mins} min`
  return `${mins} min`
}
</script>

<template>
  <div class="h-full flex flex-col bg-background overflow-hidden">
    <!-- Header/Back Button -->
    <div class="p-4 border-b border-white/[0.06] flex items-center gap-4">
      <button @click="router.back()" class="p-2 hover:bg-white/[0.06] rounded-full transition-colors">
        <ArrowLeft class="w-5 h-5" />
      </button>
      <div class="font-medium">Album Details</div>
    </div>

    <div v-if="isLoading" class="flex-1 flex items-center justify-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
    </div>

    <div v-else-if="album" class="flex-1 overflow-y-auto">
      <!-- Album Hero Section -->
      <div class="p-8 md:p-12 flex flex-col md:flex-row gap-8 items-end bg-gradient-to-b from-dynamic-surface to-transparent">
        <div class="w-48 h-48 md:w-64 md:h-64 rounded-lg shadow-2xl overflow-hidden ring-1 ring-white/[0.08] bg-white/5 flex-shrink-0">
          <img v-if="album.artwork_key" :src="`/artwork/${album.artwork_key}`" class="w-full h-full object-cover" />
          <div v-else class="w-full h-full flex items-center justify-center text-white/10">
            <Disc class="w-24 h-24" />
          </div>
        </div>

        <div class="flex-1 space-y-4">
          <div class="space-y-2">
            <h1 class="text-4xl md:text-6xl font-bold tracking-tight">{{ album.title || 'Unknown Album' }}</h1>
            <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-white/40">
              <div class="flex items-center gap-2 text-white font-semibold">
                <User class="w-4 h-4" />
                <span>{{ album.artists?.map(a => a?.name).join(', ') || 'Unknown Artist' }}</span>
              </div>
              <div v-if="album.year" class="flex items-center gap-2">
                <Calendar class="w-4 h-4" />
                <span>{{ album.year }}</span>
              </div>
              <div class="flex items-center gap-2">
                <Music class="w-4 h-4" />
                <span>{{ tracks.length }} songs</span>
              </div>
              <div class="flex items-center gap-2">
                <Clock class="w-4 h-4" />
                <span>{{ formatTotalDuration(tracks) }}</span>
              </div>
            </div>
          </div>

          <div class="flex items-center gap-4 pt-2">
            <button
              class="px-8 py-3 bg-white text-black rounded-full font-bold shadow-lg hover:scale-105 transition-transform flex items-center gap-2"
              @click="playerStore.playTracks(tracks, 0)"
            >
              <Play class="w-5 h-5 fill-current" />
              Play
            </button>
            <button class="p-3 ring-1 ring-white/[0.08] rounded-full hover:bg-white/[0.06] transition-colors">
              <MoreVertical class="w-5 h-5" />
            </button>
          </div>
        </div>
      </div>

      <!-- Track List -->
      <div class="px-2 pb-12">
        <TrackTable
          :tracks="tracks"
          :show-artwork="false"
          :show-album="false"
          @play-track="(_, index) => playerStore.playTracks(tracks, index)"
        />
      </div>

      <!-- Album Footer Metadata -->
      <div v-if="album.copyright" class="px-8 pb-12 text-sm text-white/30 border-t border-white/[0.06] pt-8 mt-4">
        <div class="flex items-center gap-2">
          <span class="font-semibold">Copyright:</span>
          <span>{{ album.copyright }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
