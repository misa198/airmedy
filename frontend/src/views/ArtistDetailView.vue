<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import type { Artist, AlbumDTO, TrackDTO } from '../../bindings/changeme/internal/domain/models'
import GroupedAlbumList from '../components/GroupedAlbumList.vue'
import { User, Play, Disc, Music } from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'

const route = useRoute()
const router = useRouter()
const playerStore = usePlayerStore()
const artist = ref<Artist | null>(null)
const albums = ref<AlbumDTO[]>([])
const tracks = ref<TrackDTO[]>([])
const isLoading = ref(true)

const loadArtistDetails = async (id: string) => {
  isLoading.value = true
  try {
    const [artistData, albumsData, tracksData] = await Promise.all([
      LibraryService.GetArtistByID(id),
      LibraryService.GetAlbumsByArtistID(id),
      LibraryService.GetTracksByArtistID(id)
    ])
    artist.value = artistData
    albums.value = albumsData.filter((a): a is AlbumDTO => a !== null)
    tracks.value = tracksData.filter((t): t is TrackDTO => t !== null)
  } catch (err) {
    console.error('Failed to load artist details:', err)
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  const id = route.params.id as string
  if (id) loadArtistDetails(id)
})

watch(() => route.params.id, (newId) => {
  if (newId) loadArtistDetails(newId as string)
})
</script>

<template>
  <div class="h-full flex flex-col bg-background overflow-hidden">
    <div v-if="isLoading" class="flex-1 flex items-center justify-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
    </div>

    <div v-else-if="artist" class="flex-1 overflow-y-auto">
      <!-- Artist Hero Section -->
      <div class="p-8 md:p-12 flex flex-col md:flex-row gap-8 items-center bg-gradient-to-b from-dynamic-surface to-transparent border-b border-white/[0.06]">
        <div class="w-40 h-40 md:w-56 md:h-56 rounded-full shadow-2xl overflow-hidden ring-2 ring-white/[0.08] bg-white/5 flex-shrink-0">
          <div class="w-full h-full flex items-center justify-center text-white/10">
            <User class="w-24 h-24" />
          </div>
        </div>

        <div class="flex-1 text-center md:text-left space-y-4">
          <div class="space-y-1">
            <h1 class="text-4xl md:text-7xl font-bold tracking-tight">{{ artist.name || 'Unknown Artist' }}</h1>
            <div class="flex flex-wrap items-center justify-center md:justify-start gap-4 text-white/40">
              <span class="flex items-center gap-1"><Disc class="w-4 h-4" /> {{ albums.length }} albums</span>
              <span class="flex items-center gap-1"><Music class="w-4 h-4" /> {{ tracks.length }} songs</span>
            </div>
          </div>

          <div class="flex items-center justify-center md:justify-start gap-4">
            <button
              class="px-8 py-3 bg-white text-black rounded-full font-bold shadow-lg hover:scale-105 transition-transform flex items-center gap-2"
              @click="playerStore.playTracks(tracks, 0); playerStore.setShuffle(true)"
            >
              <Play class="w-5 h-5 fill-current" />
              Shuffle Play
            </button>
          </div>
        </div>
      </div>

      <div class="p-8">
        <GroupedAlbumList :tracks="tracks" :albums="albums" />
      </div>
    </div>
  </div>
</template>
