<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { Disc, Clock } from 'lucide-vue-next'
import type { AlbumDTO } from '../../bindings/airmedy/internal/domain/models'
import AlbumGrid from '../components/AlbumGrid.vue'

const albums = ref<AlbumDTO[]>([])
const isLoading = ref(true)

const loadRecentlyAdded = async () => {
  isLoading.value = true
  try {
    const result = await LibraryService.GetRecentlyAddedAlbums(50)
    albums.value = result.filter((a): a is AlbumDTO => a !== null)
  } catch (err) {
    console.error('Failed to load recently added albums:', err)
  } finally {
    isLoading.value = false
  }
}

onMounted(loadRecentlyAdded)
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden bg-background">
    <div class="p-6 pb-4 border-b border-foreground/[0.06] select-none">
      <div class="flex items-center justify-between mb-2">
        <h1 class="text-3xl font-bold">{{ $t('library.recently_added') }}</h1>
        <div class="text-sm text-foreground/40 flex items-center gap-2">
          <Clock class="w-4 h-4" />
          Last 50 albums
        </div>
      </div>
      <p class="text-foreground/40 text-sm">{{ $t('library.recently_added_desc', 'Albums recently added to your library.') }}</p>
    </div>

    <div class="flex-1 overflow-hidden px-6 py-8">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>

      <div v-else-if="albums.length === 0" class="h-full flex flex-col items-center justify-center text-foreground/40">
        <Disc class="w-12 h-12 mb-4 opacity-20" />
        <p>{{ $t('library.no_albums') }}</p>
      </div>

      <AlbumGrid v-else :albums="albums" :gap="40" />
    </div>
  </div>
</template>
