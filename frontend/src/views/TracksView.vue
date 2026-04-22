<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import type { TrackDTO } from '../../bindings/changeme/internal/domain/models'
import TrackTable from '../components/TrackTable.vue'
import { usePlayerStore } from '../stores/player'

const playerStore = usePlayerStore()

const tracks = ref<TrackDTO[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

const loadTracks = async () => {
  isLoading.value = true
  try {
    const result = await LibraryService.GetAllTracks()
    tracks.value = result.filter((t): t is TrackDTO => t !== null)
  } catch (err) {
    console.error('Failed to load tracks:', err)
  } finally {
    isLoading.value = false
  }
}

const filteredTracks = computed(() => {
  if (!searchQuery.value) return tracks.value
  const query = searchQuery.value.toLowerCase()
  return tracks.value.filter(track => 
    (track.title || '').toLowerCase().includes(query) || 
    (track.raw_artist_names || '').toLowerCase().includes(query) || 
    (track.album?.title || '').toLowerCase().includes(query)
  )
})

onMounted(loadTracks)
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden bg-background">
    <!-- Header -->
    <div class="p-6 pb-4 border-b">
      <div class="flex items-center justify-between mb-4">
        <h1 class="text-3xl font-bold">Tracks</h1>
        <div class="text-sm text-muted-foreground">{{ filteredTracks.length }} tracks</div>
      </div>
      
      <div class="flex items-center gap-4">
        <div class="relative flex-1 max-w-sm">
          <input 
            v-model="searchQuery"
            type="text" 
            placeholder="Filter tracks..." 
            class="w-full bg-accent/50 border rounded-md px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
        </div>
      </div>
    </div>

    <!-- Reusable Track Table -->
    <TrackTable
      :tracks="filteredTracks"
      :is-loading="isLoading"
      :show-album="true"
      :show-artwork="true"
      @play-track="(_, index) => playerStore.playTracks(filteredTracks, index)"
    />
  </div>
</template>

