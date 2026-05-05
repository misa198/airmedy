<script setup lang="ts">
import { ref, shallowRef, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import TrackTable from '../components/TrackTable.vue'
import ViewHeader from '../components/ViewHeader.vue'
import { usePlayerStore } from '../stores/player'
import { useLibraryUpdates } from '@/composables/useLibraryUpdates'
import { useLibrarySync } from '@/composables/useLibrarySync'
import { foldUnicode } from '@/lib/utils'

const PAGE_SIZE = 500

const playerStore = usePlayerStore()
const router = useRouter()

const tracks = shallowRef<TrackDTO[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

useLibraryUpdates(tracks)
useLibrarySync(() => { loadTracks() })

const loadTracks = async () => {
  isLoading.value = true
  try {
    const total = await LibraryService.GetTrackCount()
    if (total === 0) return

    // Load first page immediately so the UI is interactive
    const first = await LibraryService.GetTracksPaginated(0, PAGE_SIZE)
    tracks.value = first.filter((t): t is TrackDTO => t !== null)
    isLoading.value = false

    // Load remaining pages in the background
    let offset = PAGE_SIZE
    while (offset < total) {
      const page = await LibraryService.GetTracksPaginated(offset, PAGE_SIZE)
      const valid = page.filter((t): t is TrackDTO => t !== null)
      tracks.value = [...tracks.value, ...valid]
      offset += PAGE_SIZE
    }
  } catch (err) {
    console.error('Failed to load tracks:', err)
  } finally {
    isLoading.value = false
  }
}

const filteredTracks = computed(() => {
  if (!searchQuery.value) return tracks.value
  const query = foldUnicode(searchQuery.value)
  return tracks.value.filter(track =>
    foldUnicode(track.title || '').includes(query) ||
    foldUnicode(track.raw_artist_names || '').includes(query) ||
    foldUnicode(track.album?.title || '').includes(query)
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
      :show-artwork="true"
      @play-track="(_, index, queue) => playerStore.playTracks(queue, index)"
      @navigate-album="id => router.push(`/albums/${id}`)"
      @navigate-artist="id => router.push(`/artists/${id}`)"
    />
  </div>
</template>
