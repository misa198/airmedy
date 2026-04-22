<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import { User, Search, Play } from 'lucide-vue-next'
import type { Artist } from '../../bindings/changeme/internal/domain/models'

const artists = ref<Artist[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

const loadArtists = async () => {
  isLoading.value = true
  try {
    const result = await LibraryService.GetAllArtists()
    artists.value = result.filter((a): a is Artist => a !== null).sort((a, b) => 
      (a.name || '').localeCompare(b.name || '')
    )
  } catch (err) {
    console.error('Failed to load artists:', err)
  } finally {
    isLoading.value = false
  }
}

const filteredArtists = computed(() => {
  if (!searchQuery.value) return artists.value
  const query = searchQuery.value.toLowerCase()
  return artists.value.filter(artist => 
    artist.name.toLowerCase().includes(query)
  )
})

onMounted(loadArtists)
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden bg-background">
    <div class="p-6 pb-4 border-b">
      <div class="flex items-center justify-between mb-4">
        <h1 class="text-3xl font-bold">Artists</h1>
        <div class="text-sm text-muted-foreground">{{ filteredArtists.length }} artists</div>
      </div>
      
      <div class="relative max-w-sm">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
        <input 
          v-model="searchQuery"
          type="text" 
          placeholder="Search artists..." 
          class="w-full bg-accent/50 border rounded-md pl-10 pr-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
        />
      </div>
    </div>

    <div class="flex-1 overflow-hidden">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
      
      <div v-else-if="filteredArtists.length === 0" class="h-full flex flex-col items-center justify-center text-muted-foreground">
        <User class="w-12 h-12 mb-4 opacity-20" />
        <p>No artists found in your library.</p>
      </div>

      <RecycleScroller
        v-else
        class="h-full px-6 py-4"
        :items="filteredArtists"
        :item-size="64"
        key-field="id"
        v-slot="{ item }"
      >
        <div class="flex items-center gap-4 p-2 rounded-lg hover:bg-accent/50 group transition-colors cursor-pointer">
          <div class="w-10 h-10 rounded-full bg-muted flex items-center justify-center overflow-hidden border group-hover:border-primary/50 transition-colors">
            <User class="w-5 h-5 text-muted-foreground group-hover:text-primary transition-colors" />
          </div>
          <div class="flex-1 truncate">
            <div class="font-medium group-hover:text-primary transition-colors">{{ item.name || 'Unknown Artist' }}</div>
          </div>
          <button class="p-2 opacity-0 group-hover:opacity-100 bg-primary text-primary-foreground rounded-full shadow-lg transition-all scale-90 group-hover:scale-100">
            <Play class="w-4 h-4 fill-current" />
          </button>
        </div>
      </RecycleScroller>
    </div>
  </div>
</template>
