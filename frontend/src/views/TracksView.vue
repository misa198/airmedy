<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import TrackTable from '../components/TrackTable.vue'
import { usePlayerStore } from '../stores/player'
import { Input } from '@/components/ui/input'
import { useLibraryUpdates } from '@/composables/useLibraryUpdates'

const playerStore = usePlayerStore()

const tracks = ref<TrackDTO[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

useLibraryUpdates(tracks)

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
    <div class="p-6 pb-4 border-b border-foreground/[0.06] select-none">
      <div class="flex items-center justify-between mb-4">
        <h1 class="text-3xl font-bold">{{ $t('library.tracks') }}</h1>
        <div class="text-sm text-foreground/40">{{ filteredTracks.length }} {{ $t('library.tracks').toLowerCase() }}</div>
      </div>
      
      <div class="flex items-center gap-4">
        <div class="relative flex-1 max-w-sm">
          <Input
            v-model="searchQuery"
            type="text"
            :placeholder="`${$t('sidebar.search')} ${$t('library.tracks').toLowerCase()}...`"
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

