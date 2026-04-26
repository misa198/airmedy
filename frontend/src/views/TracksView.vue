<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import TrackTable from '../components/TrackTable.vue'
import ViewHeader from '../components/ViewHeader.vue'
import { usePlayerStore } from '../stores/player'
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
    <ViewHeader
      v-model="searchQuery"
      :title="$t('library.tracks')"
      :search-placeholder="`${$t('sidebar.search')} ${$t('library.tracks').toLowerCase()}...`"
    />

    <TrackTable
      :tracks="filteredTracks"
      :is-loading="isLoading"
      :show-album="true"
      :show-artwork="true"
      @play-track="(_, index) => playerStore.playTracks(filteredTracks, index)"
    />
  </div>
</template>
