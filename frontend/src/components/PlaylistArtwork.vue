<script setup lang="ts">
import { computed } from 'vue'
import { ListMusic, Heart } from 'lucide-vue-next'
import type { Playlist, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { buildArtworkUrl } from '@/lib/utils'
import LazyImg from '@/components/LazyImg.vue'

const props = defineProps<{
  playlist: Playlist
  tracks?: TrackDTO[]
}>()

const playlistArtworks = computed(() => {
  if (props.playlist.artwork_key) {
    return [props.playlist.artwork_key]
  }

  if (!props.tracks || props.tracks.length === 0) return []

  const keys = new Set<string>()
  for (const track of props.tracks) {
    const key = track.artwork_key || track.album?.artwork_key
    if (key) {
      keys.add(key)
      if (keys.size >= 4) break
    }
  }
  return Array.from(keys)
})
</script>

<template>
  <div class="w-full h-full relative overflow-hidden flex-shrink-0 flex items-center justify-center bg-foreground/[0.03]">
    <!-- Custom or Single Fallback -->
    <template v-if="playlistArtworks.length === 1 || (playlistArtworks.length > 1 && playlistArtworks.length < 4)">
      <LazyImg :src="buildArtworkUrl(playlistArtworks[0], 'md')" class="w-full h-full object-cover" />
    </template>
    
    <!-- 4-Grid Fallback -->
    <template v-else-if="playlistArtworks.length >= 4">
      <div class="grid grid-cols-2 grid-rows-2 w-full h-full">
        <LazyImg v-for="key in playlistArtworks.slice(0, 4)" :key="key" :src="buildArtworkUrl(key, 'md')" class="w-full h-full object-cover" />
      </div>
    </template>

    <!-- Default Icon -->
    <div v-else class="w-full h-full flex items-center justify-center text-foreground opacity-30">
      <Heart v-if="playlist.id === 'favorites'" class="w-1/2 h-1/2" />
      <ListMusic v-else class="w-1/2 h-1/2" />
    </div>

    <!-- Slot for hover overlays etc -->
    <slot></slot>
  </div>
</template>
