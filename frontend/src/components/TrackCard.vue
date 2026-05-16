<script setup lang="ts">
import { Music, Play, User, Disc } from 'lucide-vue-next'
import type { TrackDTO, Artist } from '../../bindings/airmedy/internal/domain/models'
import { buildArtworkUrl, getTrackDisplayTitle } from '@/lib/utils'
import LazyImg from '@/components/LazyImg.vue'

const props = defineProps<{
  track: TrackDTO
}>()

const emit = defineEmits<{
  'click': [track: TrackDTO]
  'play': [track: TrackDTO]
  'artist-click': [id: string]
  'album-click': [id: string]
  'contextmenu': [e: MouseEvent, track: TrackDTO]
}>()

const artistNames = (artists: (Artist | null)[] | undefined) => {
  if (!artists) return ''
  return artists.filter(a => !!a).map(a => a!.name).join(', ')
}
</script>

<template>
  <div 
    class="group cursor-pointer w-full"
    @click="emit('click', track)"
    @contextmenu.prevent="emit('contextmenu', $event, track)"
  >
    <div class="aspect-square bg-foreground/5 rounded-lg ring-1 ring-foreground/[0.06] overflow-hidden relative mb-3 transition-all">
      <div v-if="track.artwork_key || (track.album && track.album.artwork_key)" class="w-full h-full">
        <LazyImg
          :src="buildArtworkUrl(track.artwork_key || track.album?.artwork_key, 'md')"
          :alt="track.title"
          class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
        />
      </div>
      <div v-else class="w-full h-full flex items-center justify-center text-foreground opacity-40 group-hover:scale-105 transition-transform duration-500">
        <Music class="w-1/3 h-1/3" />
      </div>

      <div class="absolute inset-0 bg-background/20 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
        <button
          @click.stop="emit('play', track)"
          class="w-10 h-10 bg-foreground text-background rounded-full shadow-xl flex items-center justify-center transform translate-y-4 group-hover:translate-y-0 transition-all duration-300"
        >
          <Play class="w-5 h-5 fill-current ml-1" />
        </button>
      </div>
    </div>

    <div class="space-y-1 px-1">
      <h3 class="font-medium text-sm truncate group-hover:text-foreground transition-colors">{{ getTrackDisplayTitle(track) || $t('library.unknown_title') }}</h3>
      <div class="text-xs text-foreground opacity-60 truncate flex flex-col gap-0.5">
        <div class="flex items-center gap-1 truncate">
          <User class="w-3 h-3 flex-shrink-0" />
          <div class="truncate">
            <template v-if="track.artists && track.artists.length > 0">
              <span v-for="(artist, i) in (track.artists.filter(a => !!a) as Artist[])" :key="artist.id || i">
                <span 
                  :class="[artist.id ? 'hover:text-primary cursor-pointer transition-colors' : '']"
                  @click.stop="artist.id && emit('artist-click', artist.id)"
                >
                  {{ artist.name }}
                </span>
                <span v-if="i < track.artists.filter(a => !!a).length - 1" class="mr-1">,</span>
              </span>
            </template>
            <span v-else>{{ $t('library.unknown_artist') }}</span>
          </div>
        </div>
        <div v-if="track.album" class="flex items-center gap-1 truncate opacity-80">
          <Disc class="w-3 h-3 flex-shrink-0" />
          <span 
            class="truncate hover:text-primary cursor-pointer transition-colors"
            @click.stop="emit('album-click', track.album!.id)"
          >
            {{ track.album.title }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
