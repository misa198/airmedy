<script setup lang="ts">
import { Disc, Play, User } from 'lucide-vue-next'
import type { AlbumDTO, Artist } from '../../bindings/airmedy/internal/domain/models'
import { buildArtworkUrl } from '@/lib/utils'
import LazyImg from '@/components/LazyImg.vue'

const props = withDefaults(defineProps<{
  album: AlbumDTO
  showPlay?: boolean
}>(), {
  showPlay: true
})

const emit = defineEmits<{
  'click': [id: string]
  'play': [id: string]
  'artist-click': [id: string]
  'contextmenu': [e: MouseEvent, album: AlbumDTO]
}>()
</script>

<template>
  <div 
    class="group cursor-pointer"
    @click="emit('click', album.id)"
    @contextmenu.prevent="emit('contextmenu', $event, album)"
  >
    <div class="aspect-square bg-foreground/5 rounded-lg ring-1 ring-foreground/[0.06] overflow-hidden relative mb-3 transition-all">
      <div v-if="album.artwork_key" class="w-full h-full">
        <LazyImg
          :src="buildArtworkUrl(album.artwork_key, 'md')"
          :alt="album.title"
          class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
        />
      </div>
      <div v-else class="w-full h-full flex items-center justify-center text-foreground opacity-40 group-hover:scale-105 transition-transform duration-500">
        <Disc class="w-1/3 h-1/3" />
      </div>

      <div v-if="showPlay" class="absolute inset-0 bg-background/20 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
        <button
          @click.stop="emit('play', album.id)"
          class="w-12 h-12 bg-foreground text-background rounded-full shadow-xl flex items-center justify-center transform translate-y-4 group-hover:translate-y-0 transition-all duration-300"
        >
          <Play class="w-6 h-6 fill-current ml-1" />
        </button>
      </div>
    </div>

    <div class="space-y-1 px-1">
      <h3 class="font-medium text-sm truncate group-hover:text-foreground transition-colors">{{ album.title || $t('library.unknown_album') }}</h3>
      <div class="text-xs text-foreground opacity-60 truncate flex items-center gap-1">
        <User class="w-3 h-3 flex-shrink-0" />
        <div class="truncate">
          <template v-if="album.artists && album.artists.length > 0">
            <span v-for="(artist, i) in (album.artists.filter(a => !!a) as Artist[])" :key="artist.id || i">
              <span 
                :class="[artist.id ? 'hover:text-primary cursor-pointer transition-colors' : '']"
                @click.stop="artist.id && emit('artist-click', artist.id)"
              >
                {{ artist.name }}
              </span>
              <span v-if="i < album.artists.filter(a => !!a).length - 1" class="mr-1">,</span>
            </span>
          </template>
          <span v-else>{{ $t('library.unknown_artist') }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
