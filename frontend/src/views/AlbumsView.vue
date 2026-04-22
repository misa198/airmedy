<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import { Disc, Search, Play, User } from 'lucide-vue-next'
import type { AlbumDTO } from '../../bindings/changeme/internal/domain/models'

const albums = ref<AlbumDTO[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

const loadAlbums = async () => {
  isLoading.value = true
  try {
    const result = await LibraryService.GetAllAlbums()
    // result is (AlbumDTO | null)[]
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
    <div class="p-6 pb-4 border-b">
      <div class="flex items-center justify-between mb-4">
        <h1 class="text-3xl font-bold">Albums</h1>
        <div class="text-sm text-muted-foreground">{{ filteredAlbums.length }} albums</div>
      </div>
      
      <div class="relative max-w-sm">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
        <input 
          v-model="searchQuery"
          type="text" 
          placeholder="Search albums..." 
          class="w-full bg-accent/50 border rounded-md pl-10 pr-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
        />
      </div>
    </div>

    <div class="flex-1 overflow-y-auto px-6 py-8">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
      
      <div v-else-if="filteredAlbums.length === 0" class="h-full flex flex-col items-center justify-center text-muted-foreground">
        <Disc class="w-12 h-12 mb-4 opacity-20" />
        <p>No albums found in your library.</p>
      </div>

      <div v-else class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-6">
        <div 
          v-for="album in filteredAlbums" 
          :key="album.id"
          class="group cursor-pointer"
        >
          <div class="aspect-square bg-muted rounded-lg border overflow-hidden relative mb-3 shadow-sm group-hover:shadow-md transition-all">
            <div v-if="album.artwork_key" class="w-full h-full">
              <img 
                :src="`/artwork/${album.artwork_key}`" 
                :alt="album.title"
                class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
              />
            </div>
            <div v-else class="w-full h-full flex items-center justify-center text-muted-foreground/40 group-hover:scale-105 transition-transform duration-500">
              <Disc class="w-1/3 h-1/3" />
            </div>
            
            <div class="absolute inset-0 bg-black/20 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
              <button class="w-12 h-12 bg-primary text-primary-foreground rounded-full shadow-xl flex items-center justify-center transform translate-y-4 group-hover:translate-y-0 transition-all duration-300">
                <Play class="w-6 h-6 fill-current ml-1" />
              </button>
            </div>
          </div>
          
          <div class="space-y-1 px-1">
            <h3 class="font-medium text-sm truncate group-hover:text-primary transition-colors">{{ album.title || 'Unknown Album' }}</h3>
            <p class="text-xs text-muted-foreground truncate flex items-center gap-1">
              <User class="w-3 h-3" />
              {{ album.artists && album.artists.length > 0 ? album.artists.map(a => a?.name).filter(Boolean).join(', ') : 'Unknown Artist' }}
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
