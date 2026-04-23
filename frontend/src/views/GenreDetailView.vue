<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { Genre, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import GroupedAlbumList from '../components/GroupedAlbumList.vue'
import { Tag, Music, Play } from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'

const route = useRoute()
const playerStore = usePlayerStore()
const genre = ref<Genre | null>(null)
const tracks = ref<TrackDTO[]>([])
const isLoading = ref(true)

const loadGenreDetails = async (id: string) => {
  isLoading.value = true
  try {
    const [genreData, tracksData] = await Promise.all([
      LibraryService.GetGenreByID(id),
      LibraryService.GetTracksByGenreID(id)
    ])
    genre.value = genreData
    tracks.value = tracksData.filter((t): t is TrackDTO => t !== null)
  } catch (err) {
    console.error('Failed to load genre details:', err)
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  const id = route.params.id as string
  if (id) loadGenreDetails(id)
})

watch(() => route.params.id, (newId) => {
  if (newId) loadGenreDetails(newId as string)
})
</script>

<template>
  <div class="h-full flex flex-col bg-background overflow-hidden animate-in fade-in slide-in-from-right-4 duration-300">
    <div v-if="isLoading" class="flex-1 flex items-center justify-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
    </div>

    <div v-else-if="genre" class="flex-1 flex flex-col overflow-hidden">
      <!-- Genre Header -->
      <div class="p-8 border-b border-white/[0.06] bg-gradient-to-b from-dynamic-surface to-transparent flex items-end gap-6 flex-shrink-0">
        <div class="w-24 h-24 rounded-2xl bg-white/5 flex items-center justify-center ring-1 ring-white/[0.08] flex-shrink-0">
          <Tag class="w-12 h-12 text-white/50" />
        </div>
        <div class="flex-1 space-y-2">
          <h1 class="text-4xl font-bold tracking-tight">{{ genre.name || 'Unknown Genre' }}</h1>
          <div class="flex items-center gap-4 text-white/40">
            <span class="flex items-center gap-1"><Music class="w-4 h-4" /> {{ tracks.length }} tracks</span>
          </div>
          <div class="pt-2">
            <button
              class="px-6 py-2 bg-white text-black rounded-full font-bold shadow-lg hover:scale-105 transition-transform flex items-center gap-2"
              @click="playerStore.playTracks(tracks, 0)"
            >
              <Play class="w-4 h-4 fill-current" />
              Play All
            </button>
          </div>
        </div>
      </div>

      <!-- Grouped Albums -->
      <div class="flex-1 overflow-y-auto p-8">
        <GroupedAlbumList :tracks="tracks" />
      </div>
    </div>
  </div>
</template>
