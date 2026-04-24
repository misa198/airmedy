<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import TrackTable from '../components/TrackTable.vue'
import { usePlayerStore } from '../stores/player'
import { Heart } from 'lucide-vue-next'

const playerStore = usePlayerStore()
const tracks = ref<TrackDTO[]>([])
const isLoading = ref(true)

const loadTracks = async () => {
  isLoading.value = true
  try {
    const result = await LibraryService.GetFavoriteTracks()
    tracks.value = result.filter((t): t is TrackDTO => t !== null)
  } catch (err) {
    console.error('Failed to load favorite tracks:', err)
  } finally {
    isLoading.value = false
  }
}

onMounted(loadTracks)
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden bg-background">
    <div class="p-6 pb-4 border-b border-white/[0.06]">
      <div class="flex items-center justify-between mb-1">
        <div class="flex items-center gap-3">
          <Heart class="w-7 h-7 text-primary fill-primary" />
          <h1 class="text-3xl font-bold">Favorites</h1>
        </div>
        <div class="text-sm text-white/40">{{ tracks.length }} tracks</div>
      </div>
    </div>

    <TrackTable
      :tracks="tracks"
      :is-loading="isLoading"
      :show-album="true"
      :show-artwork="true"
      @play-track="(_, index) => playerStore.playTracks(tracks, index)"
    />
  </div>
</template>
