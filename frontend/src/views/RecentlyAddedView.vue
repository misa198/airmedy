<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import { Disc, Play, User, Clock } from 'lucide-vue-next'
import type { AlbumDTO, Artist } from '../../bindings/changeme/internal/domain/models'

const router = useRouter()
const albums = ref<AlbumDTO[]>([])
const isLoading = ref(true)

const loadRecentlyAdded = async () => {
  isLoading.value = true
  try {
    // Get last 50 added albums
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

    <div class="flex-1 overflow-y-auto px-6 py-8">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
      
      <div v-else-if="albums.length === 0" class="h-full flex flex-col items-center justify-center text-muted-foreground">
        <Disc class="w-12 h-12 mb-4 opacity-20" />
        <p>No albums found in your library.</p>
      </div>

      <div v-else class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-6">
        <div 
          v-for="album in albums" 
          :key="album.id"
          class="group cursor-pointer"
          @click="navigateToAlbum(album.id)"
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
            <h3 class="font-medium text-sm truncate group-hover:text-primary transition-colors cursor-pointer" @click.stop="navigateToAlbum(album.id)">{{ album.title || 'Unknown Album' }}</h3>
            <div class="text-xs text-muted-foreground truncate flex items-center gap-1">
              <User class="w-3 h-3 flex-shrink-0" />
              <div class="truncate">
                <template v-if="album.artists && album.artists.length > 0">
                  <span v-for="(artist, i) in (album.artists.filter(a => !!a) as Artist[])" :key="artist.id || i">
                    <span 
                      :class="[artist.id ? 'hover:text-primary cursor-pointer transition-colors' : '']"
                      @click.stop="artist.id && navigateToArtist(artist.id)"
                    >
                      {{ artist.name }}
                    </span>
                    <span v-if="i < album.artists.filter(a => !!a).length - 1" class="mr-1">,</span>
                  </span>
                </template>
                <span v-else>Unknown Artist</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
