<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import { Disc, Clock } from 'lucide-vue-next'
import type { AlbumDTO } from '../../bindings/changeme/internal/domain/models'
import VirtualizedGrid from '../components/VirtualizedGrid.vue'
import AlbumCard from '../components/AlbumCard.vue'

const router = useRouter()
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

const navigateToAlbum = (id: string) => {
  router.push(`/albums/${id}`)
}

const navigateToArtist = (id: string) => {
  if (id) router.push(`/artists/${id}`)
}

onMounted(loadRecentlyAdded)
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden bg-background">
    <div class="p-6 pb-4 border-b">
      <div class="flex items-center justify-between mb-2">
        <h1 class="text-3xl font-bold">Recently Added</h1>
        <div class="text-sm text-muted-foreground flex items-center gap-2">
          <Clock class="w-4 h-4" />
          Last 50 albums
        </div>
      </div>
      <p class="text-muted-foreground text-sm">Albums recently added to your library.</p>
    </div>

    <div class="flex-1 overflow-hidden px-6 py-8">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
      
      <div v-else-if="albums.length === 0" class="h-full flex flex-col items-center justify-center text-muted-foreground">
        <Disc class="w-12 h-12 mb-4 opacity-20" />
        <p>No albums found in your library.</p>
      </div>

      <VirtualizedGrid 
        v-else 
        :items="albums" 
        :item-height="250" 
        :min-column-width="180"
      >
        <template #default="{ item: album }">
          <AlbumCard 
            :album="album" 
            @click="navigateToAlbum"
            @artist-click="navigateToArtist"
          />
        </template>
      </VirtualizedGrid>
    </div>
  </div>
</template>
