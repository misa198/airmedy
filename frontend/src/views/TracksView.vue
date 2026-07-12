<script setup lang="ts">
import { ref, shallowRef, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import TrackTable from '../components/TrackTable.vue'
import TrackTableFilter from '../components/TrackTableFilter.vue'
import ViewHeader from '../components/ViewHeader.vue'
import { usePlayerStore } from '../stores/player'
import { useLibraryUpdates } from '@/composables/useLibraryUpdates'
import { useLibrarySync } from '@/composables/useLibrarySync'
import { foldUnicode } from '@airmedy/utils'

const PAGE_SIZE = 4000

const playerStore = usePlayerStore()
const router = useRouter()

const trackTableRef = ref<InstanceType<typeof TrackTable>>()
const tracks = shallowRef<TrackDTO[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

useLibraryUpdates(tracks)
// Event-driven reloads are silent: keep the current list visible, swap in new
// data when it arrives, so background refreshes don't flash the spinner.
useLibrarySync(() => { loadTracks(true) })

const loadTracks = async (silent = false) => {
  if (!silent) isLoading.value = true
  try {
    const total = await LibraryService.GetTrackCount()
    if (total === 0) {
      tracks.value = []
      return
    }

    // Load first page immediately so the UI is interactive
    const first = await LibraryService.GetTracksPaginated(0, PAGE_SIZE)
    tracks.value = first.filter((t): t is TrackDTO => t !== null)
    if (!silent) isLoading.value = false

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
    if (!silent) isLoading.value = false
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
    >
      <template #actions>
        <div class="relative z-[99]">
          <TrackTableFilter
            :optional-columns="trackTableRef?.optionalColumns ?? []"
            :sort-column="trackTableRef?.sortColumn ?? null"
            :sort-dir="trackTableRef?.sortDir ?? null"
            @select-sort="key => trackTableRef?.cycleSort(key)"
          />
        </div>
      </template>
    </ViewHeader>

    <TrackTable
      ref="trackTableRef"
      :tracks="filteredTracks"
      :is-loading="isLoading"
      :show-artwork="true"
      storage-key="airmedy:tracks-sort"
      @play-track="(_, index, queue) => playerStore.playTracks(queue, index)"
      @navigate-album="id => router.push(`/albums/${id}`)"
      @navigate-artist="id => router.push(`/artists/${id}`)"
    />
  </div>
</template>
