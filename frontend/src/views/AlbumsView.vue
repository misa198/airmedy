<script setup lang="ts">
import { ref, shallowRef, onMounted, computed } from 'vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { Disc } from 'lucide-vue-next'
import type { AlbumDTO } from '../../bindings/airmedy/internal/domain/models'
import AlbumGrid from '../components/AlbumGrid.vue'
import ViewHeader from '../components/ViewHeader.vue'
import { useLibrarySync } from '@/composables/useLibrarySync'
import { foldUnicode } from '@/lib/utils'

const albums = shallowRef<AlbumDTO[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

useLibrarySync(() => { loadAlbums() })

const loadAlbums = async () => {
  isLoading.value = true
  try {
    const result = await LibraryService.GetAllAlbums()
    albums.value = result.filter((a): a is AlbumDTO => a !== null).sort((a, b) =>
      (a.title || '').localeCompare(b.title || '')
    )
  } catch (err) {
    console.error('Failed to load albums:', err)
  } finally {
    isLoading.value = false
  }
}

const filteredAlbums = computed(() => {
  if (!searchQuery.value) return albums.value
  const query = foldUnicode(searchQuery.value)
  return albums.value.filter(album =>
    foldUnicode(album.title).includes(query) ||
    (album.artists && album.artists.some(a => foldUnicode(a?.name || '').includes(query)))
  )
})

onMounted(loadAlbums)
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden bg-background">
    <ViewHeader
      v-model="searchQuery"
      :title="$t('library.albums')"
      :search-placeholder="`${$t('sidebar.search')} ${$t('library.albums').toLowerCase()}...`"
    />

    <div class="flex-1 overflow-hidden px-6 py-8">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>

      <div v-else-if="filteredAlbums.length === 0" class="h-full flex flex-col items-center justify-center text-foreground opacity-60">
        <Disc class="w-12 h-12 mb-4 opacity-20" />
        <p>{{ $t('library.no_albums') }}</p>
      </div>

      <AlbumGrid v-else :albums="filteredAlbums" :gap="45" />
    </div>
  </div>
</template>
