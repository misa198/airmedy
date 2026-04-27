<script setup lang="ts">
import { ref, shallowRef, onMounted } from 'vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { Disc } from 'lucide-vue-next'
import type { AlbumDTO } from '../../bindings/airmedy/internal/domain/models'
import AlbumGrid from '../components/AlbumGrid.vue'
import ViewHeader from '../components/ViewHeader.vue'

const albums = shallowRef<AlbumDTO[]>([])
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
    <ViewHeader :title="$t('library.recently_added')" />

    <div class="flex-1 overflow-hidden px-6 py-8">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>

      <div v-else-if="albums.length === 0" class="h-full flex flex-col items-center justify-center text-foreground opacity-60">
        <Disc class="w-12 h-12 mb-4 opacity-20" />
        <p>{{ $t('library.no_albums') }}</p>
      </div>

      <AlbumGrid v-else :albums="albums" :gap="40" />
    </div>
  </div>
</template>
