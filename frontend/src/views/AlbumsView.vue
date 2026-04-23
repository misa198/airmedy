<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import { Disc, Search } from 'lucide-vue-next'
import type { AlbumDTO } from '../../bindings/changeme/internal/domain/models'
import VirtualizedGrid from '../components/VirtualizedGrid.vue'
import AlbumCard from '../components/AlbumCard.vue'
import { Input } from '@/components/ui/input'

const router = useRouter()
const albums = ref<AlbumDTO[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

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

const navigateToAlbum = (id: string) => {
  router.push(`/albums/${id}`)
}

const navigateToArtist = (id: string) => {
  if (id) router.push(`/artists/${id}`)
}

const filteredAlbums = computed(() => {
  if (!searchQuery.value) return albums.value
  const query = searchQuery.value.toLowerCase()
  return albums.value.filter(album => 
    album.title.toLowerCase().includes(query) || 
    (album.artists && album.artists.some(a => a?.name?.toLowerCase().includes(query)))
  )
})

onMounted(loadAlbums)
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden bg-background">
    <div class="p-6 pb-4 border-b border-white/[0.06]">
      <div class="flex items-center justify-between mb-4">
        <h1 class="text-3xl font-bold">Albums</h1>
        <div class="text-sm text-white/40">{{ filteredAlbums.length }} albums</div>
      </div>
      
      <div class="relative max-w-sm">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/40" />
        <Input
          v-model="searchQuery"
          type="text"
          placeholder="Search albums..."
          class="pl-10 pr-4"
        />
      </div>
    </div>

    <div class="flex-1 overflow-hidden px-6 py-8">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
      
      <div v-else-if="filteredAlbums.length === 0" class="h-full flex flex-col items-center justify-center text-white/40">
        <Disc class="w-12 h-12 mb-4 opacity-20" />
        <p>No albums found in your library.</p>
      </div>

      <VirtualizedGrid 
        v-else 
        :items="filteredAlbums" 
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
