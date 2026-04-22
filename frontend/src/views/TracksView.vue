<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import { Music, Clock, User, Disc, MoreVertical, Play } from 'lucide-vue-next'
import type { TrackDTO } from '../../bindings/changeme/internal/domain/models'

const tracks = ref<TrackDTO[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

const loadTracks = async () => {
  isLoading.value = true
  try {
    const result = await LibraryService.GetAllTracks()
    tracks.value = result.filter((t): t is TrackDTO => t !== null)
  } catch (err) {
    console.error('Failed to load tracks:', err)
  } finally {
    isLoading.value = false
  }
}

const filteredTracks = computed(() => {
  if (!searchQuery.value) return tracks.value
  const query = searchQuery.value.toLowerCase()
  return tracks.value.filter(track => 
    (track.title || '').toLowerCase().includes(query) || 
    (track.raw_artist_names || '').toLowerCase().includes(query) || 
    (track.album?.title || '').toLowerCase().includes(query)
  )
})

const formatDuration = (seconds: number) => {
  if (!seconds) return '0:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

onMounted(loadTracks)
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden bg-background">
    <!-- Header -->
    <div class="p-6 pb-4 border-b">
      <div class="flex items-center justify-between mb-4">
        <h1 class="text-3xl font-bold">Tracks</h1>
        <div class="text-sm text-muted-foreground">{{ filteredTracks.length }} tracks</div>
      </div>
      
      <div class="flex items-center gap-4">
        <div class="relative flex-1 max-w-sm">
          <input 
            v-model="searchQuery"
            type="text" 
            placeholder="Filter tracks..." 
            class="w-full bg-accent/50 border rounded-md px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
        </div>
      </div>
    </div>

    <!-- Table Header (Sticky) -->
    <div class="grid grid-cols-[48px_1fr_1fr_1fr_80px_48px] gap-4 px-6 py-2 border-b text-xs font-medium text-muted-foreground uppercase tracking-wider bg-muted/30">
      <div class="text-center">#</div>
      <div>Title</div>
      <div>Artist</div>
      <div>Album</div>
      <div class="flex items-center gap-1 justify-center"><Clock class="w-3 h-3" /></div>
      <div></div>
    </div>

    <!-- Virtualized List -->
    <div class="flex-1 overflow-hidden">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
      
      <div v-else-if="filteredTracks.length === 0" class="h-full flex flex-col items-center justify-center text-muted-foreground">
        <Music class="w-12 h-12 mb-4 opacity-20" />
        <p>No tracks found in your library.</p>
      </div>

      <RecycleScroller
        v-else
        class="h-full"
        :items="filteredTracks"
        :item-size="56"
        key-field="id"
        v-slot="{ item, index }"
      >
        <div class="grid grid-cols-[48px_1fr_1fr_1fr_80px_48px] gap-4 px-6 h-[56px] items-center text-sm hover:bg-accent/50 group transition-colors border-b border-transparent hover:border-accent">
          <div class="text-center text-muted-foreground group-hover:hidden">{{ index + 1 }}</div>
          <div class="hidden group-hover:flex items-center justify-center">
            <button class="text-primary hover:scale-110 transition-transform">
              <Play class="w-4 h-4 fill-current" />
            </button>
          </div>
          
          <div class="font-medium truncate flex items-center gap-3">
            <div class="w-8 h-8 bg-muted rounded flex-shrink-0 overflow-hidden border">
              <img v-if="item.artwork_key" :src="`/artwork/${item.artwork_key}`" class="w-full h-full object-cover" />
              <div v-else class="w-full h-full flex items-center justify-center text-muted-foreground/30">
                <Music class="w-4 h-4" />
              </div>
            </div>
            <span class="truncate">{{ item.title || 'Unknown Title' }}</span>
          </div>
          <div class="text-muted-foreground truncate flex items-center gap-2">
            <User class="w-3 h-3 opacity-50" />
            {{ item.artists && item.artists.length > 0 ? item.artists.map(a => a?.name).filter(Boolean).join(', ') : (item.raw_artist_names || 'Unknown Artist') }}
          </div>
          <div class="text-muted-foreground truncate flex items-center gap-2">
            <Disc class="w-3 h-3 opacity-50" />
            {{ item.album?.title || 'Unknown Album' }}
          </div>
          <div class="text-center text-muted-foreground font-mono text-xs">
            {{ formatDuration(item.duration) }}
          </div>
          <div class="flex items-center justify-end opacity-0 group-hover:opacity-100">
            <button class="p-2 hover:bg-accent rounded-full text-muted-foreground hover:text-foreground">
              <MoreVertical class="w-4 h-4" />
            </button>
          </div>
        </div>
      </RecycleScroller>
    </div>
  </div>
</template>

<style scoped>
/* Scroller styling */
.vue-recycle-scroller {
  scrollbar-width: thin;
}
</style>
