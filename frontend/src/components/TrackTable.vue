<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Music, Clock, User, Disc, MoreVertical, Play } from 'lucide-vue-next'
import type { TrackDTO, Artist } from '../../bindings/changeme/internal/domain/models'
import { formatTime } from '../lib/utils'

const router = useRouter()
const props = defineProps<{
  tracks: TrackDTO[]
  isLoading?: boolean
  showAlbum?: boolean
  showArtwork?: boolean
}>()

const emit = defineEmits<{
  'play-track': [track: TrackDTO, index: number]
}>()

const navigateToAlbum = (id: string) => {
  router.push(`/albums/${id}`)
}

const navigateToArtist = (id: string) => {
  if (id) router.push(`/artists/${id}`)
}


</script>

<template>
  <div class="h-full flex flex-col overflow-hidden">
    <!-- Table Header -->
    <div :class="[
      'grid gap-4 px-6 py-2 border-b border-white/[0.06] text-[10px] font-semibold text-white/30 uppercase tracking-widest',
      showAlbum ? 'grid-cols-[48px_1fr_1fr_1fr_80px_48px]' : 'grid-cols-[48px_1fr_1fr_80px_48px]'
    ]">
      <div class="text-center">#</div>
      <div>Title</div>
      <div>Artist</div>
      <div v-if="showAlbum">Album</div>
      <div class="flex items-center gap-1 justify-center"><Clock class="w-3 h-3" /></div>
      <div></div>
    </div>

    <!-- Virtualized List -->
    <div class="flex-1 overflow-hidden">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
      
      <div v-else-if="tracks.length === 0" class="h-full flex flex-col items-center justify-center text-muted-foreground">
        <Music class="w-12 h-12 mb-4 opacity-20" />
        <p>No tracks found.</p>
      </div>

      <RecycleScroller
        v-else
        class="h-full"
        :items="tracks"
        :item-size="56"
        key-field="id"
        v-slot="{ item, index }"
      >
        <div :class="[
          'grid gap-4 px-6 h-[56px] items-center text-sm hover:bg-white/[0.04] group transition-colors',
          showAlbum ? 'grid-cols-[48px_1fr_1fr_1fr_80px_48px]' : 'grid-cols-[48px_1fr_1fr_80px_48px]'
        ]">
          <div class="text-center text-muted-foreground group-hover:hidden">{{ index + 1 }}</div>
          <div class="hidden group-hover:flex items-center justify-center">
            <button @click="emit('play-track', item, index)" class="text-primary hover:scale-110 transition-transform">
              <Play class="w-4 h-4 fill-current" />
            </button>
          </div>
          
          <div class="font-medium truncate flex items-center gap-3">
            <div v-if="showArtwork" class="w-8 h-8 bg-white/5 rounded flex-shrink-0 overflow-hidden">
              <img v-if="item.artwork_key" :src="`/artwork/${item.artwork_key}`" class="w-full h-full object-cover" />
              <div v-else class="w-full h-full flex items-center justify-center text-muted-foreground/30">
                <Music class="w-4 h-4" />
              </div>
            </div>
            <span class="truncate">{{ item.title || 'Unknown Title' }}</span>
          </div>
          <div class="text-muted-foreground truncate flex items-center gap-2 pr-4">
            <User class="w-3 h-3 opacity-50 flex-shrink-0" />
            <div class="truncate">
              <template v-if="item.artists && item.artists.length > 0">
                <span v-for="(artist, i) in (item.artists.filter(a => !!a) as Artist[])" :key="artist.id || i">
                  <span 
                    :class="[artist.id ? 'hover:text-primary cursor-pointer transition-colors' : '']"
                    @click.stop="artist.id && navigateToArtist(artist.id)"
                  >
                    {{ artist.name }}
                  </span>
                  <span v-if="i < item.artists.filter(a => !!a).length - 1" class="mr-1">,</span>
                </span>
              </template>
              <span v-else>{{ item.raw_artist_names || 'Unknown Artist' }}</span>
            </div>
          </div>
          <div v-if="showAlbum" class="text-muted-foreground truncate flex items-center gap-2">
            <Disc class="w-3 h-3 opacity-50" />
            <span 
              class="truncate group-hover:text-primary transition-colors cursor-pointer" 
              @click.stop="item.album?.id && navigateToAlbum(item.album.id)"
            >
              {{ item.album?.title || 'Unknown Album' }}
            </span>
          </div>
          <div class="text-center text-muted-foreground font-mono text-xs">
            {{ formatTime(item.duration) }}
          </div>
          <div class="flex items-center justify-end opacity-0 group-hover:opacity-100">
            <button class="p-2 hover:bg-white/8 rounded-full text-white/30 hover:text-white/70 transition-colors">
              <MoreVertical class="w-4 h-4" />
            </button>
          </div>
        </div>
      </RecycleScroller>
    </div>
  </div>
</template>

<style scoped>
.vue-recycle-scroller {
  scrollbar-width: thin;
}
</style>
