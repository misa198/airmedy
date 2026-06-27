<script setup lang="ts">
import { ref, shallowRef, onMounted, computed, watch } from 'vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { Disc } from 'lucide-vue-next'
import type { AlbumDTO, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import AlbumGrid from '../components/AlbumGrid.vue'
import AlbumListView from '../components/AlbumListView.vue'
import AlbumViewFilter from '../components/AlbumViewFilter.vue'
import ViewHeader from '../components/ViewHeader.vue'
import { useLibrarySync } from '@/composables/useLibrarySync'
import { foldUnicode } from '@airmedy/utils'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '@/stores/player'

type SortCol = 'title' | 'artist' | 'year' | null
type SortDir = 'asc' | 'desc'

const albums = shallowRef<AlbumDTO[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

const viewMode = ref<'grid' | 'list'>(
  (localStorage.getItem('airmedy:albums-view-mode') as 'grid' | 'list') || 'grid'
)
const sortColumn = ref<SortCol>(
  (localStorage.getItem('airmedy:albums-sort-col') as SortCol) || 'title'
)
const sortDir = ref<SortDir>(
  (localStorage.getItem('airmedy:albums-sort-dir') as SortDir) || 'asc'
)
watch(viewMode, (v) => localStorage.setItem('airmedy:albums-view-mode', v))
watch(sortColumn, (v) => localStorage.setItem('airmedy:albums-sort-col', v ?? ''))
watch(sortDir, (v) => localStorage.setItem('airmedy:albums-sort-dir', v))

const router = useRouter()
const playerStore = usePlayerStore()

// Event-driven reloads are silent: keep the current grid visible and swap in the
// new data when it arrives, so background refreshes don't flash the spinner.
useLibrarySync(() => { loadAlbums(true) })

const loadAlbums = async (silent = false) => {
  if (!silent) isLoading.value = true
  try {
    const result = await LibraryService.GetAllAlbums()
    albums.value = result.filter((a): a is AlbumDTO => a !== null).sort((a, b) =>
      (a.title || '').localeCompare(b.title || '')
    )
  } catch (err) {
    console.error('Failed to load albums:', err)
  } finally {
    if (!silent) isLoading.value = false
  }
}

const processedAlbums = computed(() => {
  // 1. Search filter
  let result = albums.value
  if (searchQuery.value) {
    const q = foldUnicode(searchQuery.value)
    result = result.filter(a =>
      foldUnicode(a.title || '').includes(q) ||
      (a.artists && a.artists.some(ar => foldUnicode(ar?.name || '').includes(q)))
    )
  }

  // 2. Sort
  if (sortColumn.value) {
    const col = sortColumn.value
    const dir = sortDir.value
    result = [...result].sort((a, b) => {
      if (col === 'year') {
        const diff = (a.year ?? 0) - (b.year ?? 0)
        return dir === 'asc' ? diff : -diff
      }
      const aVal = col === 'title' ? (a.title || '') : (a.artists?.[0]?.name || '')
      const bVal = col === 'title' ? (b.title || '') : (b.artists?.[0]?.name || '')
      const cmp = aVal.localeCompare(bVal)
      return dir === 'asc' ? cmp : -cmp
    })
  }

  return result
})

const navigateToAlbum = (id: string) => router.push(`/albums/${id}`)
const navigateToArtist = (id: string) => { if (id) router.push(`/artists/${id}`) }

const playAlbum = async (id: string) => {
  try {
    const tracks = await LibraryService.GetTracksByAlbumID(id)
    if (tracks && tracks.length > 0) {
      playerStore.playTracks(tracks.filter((t): t is TrackDTO => t !== null), 0)
    }
  } catch (err) {
    console.error('Failed to play album:', err)
  }
}

onMounted(loadAlbums)
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden bg-background">
    <ViewHeader v-model="searchQuery" :title="$t('library.albums')"
      :search-placeholder="`${$t('sidebar.search')} ${$t('library.albums').toLowerCase()}...`">
      <template #actions>
        <div class="relative z-[99]">
          <AlbumViewFilter v-model:view-mode="viewMode" v-model:sort-column="sortColumn" v-model:sort-dir="sortDir" />
        </div>
      </template>
    </ViewHeader>

    <div class="flex-1 overflow-hidden" :class="viewMode === 'grid' ? 'px-6 py-8' : ''">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
      </div>

      <template v-else-if="processedAlbums.length === 0">
        <div class="h-full flex flex-col items-center justify-center text-foreground opacity-60">
          <Disc class="w-12 h-12 mb-4 opacity-20" />
          <p>{{ $t('library.no_albums') }}</p>
        </div>
      </template>

      <template v-else>
        <AlbumGrid v-if="viewMode === 'grid'" :albums="processedAlbums" :gap="45" />
        <AlbumListView v-else :albums="processedAlbums"
          :sort-column="sortColumn ?? 'title'" :sort-dir="sortDir"
          @click="navigateToAlbum" @play="playAlbum" @artist-click="navigateToArtist"
          @update:sort-column="sortColumn = $event" @update:sort-dir="sortDir = $event" />
      </template>
    </div>
  </div>
</template>
