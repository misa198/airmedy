<script setup lang="ts">
import { ListMusic } from 'lucide-vue-next'
import PlaylistArtwork from '@/components/PlaylistArtwork.vue'
import type { Playlist, TrackDTO } from '../../bindings/airmedy/internal/domain/models'

const props = defineProps<{
  playlist: Playlist
  tracks?: TrackDTO[]
}>()

const emit = defineEmits<{
  'click': [id: string]
}>()
</script>

<template>
  <div 
    class="group cursor-pointer"
    @click="emit('click', playlist.id)"
  >
    <div class="aspect-square bg-foreground/5 rounded-lg ring-1 ring-foreground/[0.06] overflow-hidden relative mb-3 transition-all flex items-center justify-center">
      <PlaylistArtwork 
        :playlist="playlist" 
        :tracks="tracks" 
        class="group-hover:scale-105 transition-transform duration-500"
      />
    </div>

    <div class="space-y-1 px-1">
      <h3 class="font-medium text-sm truncate group-hover:text-foreground transition-colors">{{ playlist.name || $t('library.unknown_playlist') }}</h3>
      <p class="text-xs text-foreground opacity-60">{{ $t('library.playlist') }}</p>
    </div>
  </div>
</template>

