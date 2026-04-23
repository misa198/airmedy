<script setup lang="ts">
import { Clock, Disc, MoreVertical, Music, Play, User } from 'lucide-vue-next'
import { nextTick, onActivated, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { Artist, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { formatTime } from '../lib/utils'
import { usePlayerStore } from '../stores/player'

const router = useRouter()
const playerStore = usePlayerStore()

const props = defineProps<{
  tracks: TrackDTO[]
  isLoading?: boolean
  showAlbum?: boolean
  showArtwork?: boolean
  scrollToCurrent?: boolean
}>()

const emit = defineEmits<{
  'play-track': [track: TrackDTO, index: number]
}>()

const scrollerRef = ref<any>(null)
const lastScrollTop = ref(0)

const scrollToCurrentTrack = () => {
  if (!scrollerRef.value || !playerStore.currentTrack || props.tracks.length === 0) return
  const index = props.tracks.findIndex(t => t.id === playerStore.currentTrack?.id)
  if (index !== -1) {
    scrollerRef.value.scrollToItem(index)
  }
}

const handleScroll = (event: Event) => {
  const target = event.target as HTMLElement
  if (target) {
    lastScrollTop.value = target.scrollTop
  }
}

// Watch for track changes or current track changes to scroll if needed
watch([() => props.tracks, () => playerStore.currentTrack], () => {
  if (props.scrollToCurrent) {
    nextTick(() => {
      scrollToCurrentTrack()
    })
  }
}, { deep: false })

onMounted(() => {
  if (props.scrollToCurrent) {
    // Small timeout to allow for transitions and layout calculations
    setTimeout(() => {
      scrollToCurrentTrack()
    }, 100)
  }
})

onActivated(() => {
  if (scrollerRef.value && lastScrollTop.value > 0) {
    setTimeout(() => {
      if (scrollerRef.value && scrollerRef.value.$el) {
        scrollerRef.value.$el.scrollTop = lastScrollTop.value
      }
    }, 0)
  } else if (props.scrollToCurrent) {
    setTimeout(() => {
      scrollToCurrentTrack()
    }, 100)
  }
})

const navigateToAlbum = (id: string) => {
  router.push(`/albums/${id}`)
}

const navigateToArtist = (id: string) => {
  if (id) router.push(`/artists/${id}`)
}

const isCurrentTrack = (trackId: string) => {
  return playerStore.currentTrack?.id === trackId
}

</script>

<template>
  <div class="h-full flex flex-col overflow-hidden @container">
    <!-- Table Header -->
    <div :class="[
      'grid gap-4 px-6 py-2 border-b border-white/[0.06] text-[10px] font-semibold text-white/80 uppercase tracking-widest',
      showAlbum
        ? 'grid-cols-[minmax(0,1fr)_80px] @[450px]:grid-cols-[40px_minmax(0,1fr)_80px_40px] @[700px]:grid-cols-[40px_minmax(0,1fr)_minmax(0,1fr)_80px_40px] @[1000px]:grid-cols-[40px_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_80px_40px]'
        : 'grid-cols-[minmax(0,1fr)_80px] @[450px]:grid-cols-[40px_minmax(0,1fr)_80px_40px] @[650px]:grid-cols-[40px_minmax(0,1fr)_minmax(0,1fr)_80px_40px]'
    ]">
      <div class="text-center hidden @[450px]:block">#</div>
      <div class="min-w-0">Title</div>
      <div class="min-w-0 hidden @[650px]:block">Artist</div>
      <div class="min-w-0 hidden @[1000px]:block" v-if="showAlbum">Album</div>
      <div class="flex items-center gap-1 justify-center">
        <Clock class="w-3 h-3" />
      </div>
      <div class="hidden @[450px]:block"></div>
    </div>

    <!-- Virtualized List -->
    <div class="flex-1 overflow-hidden">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>

      <div v-else-if="tracks.length === 0" class="h-full flex flex-col items-center justify-center text-white/80">
        <Music class="w-12 h-12 mb-4 opacity-20" />
        <p>No tracks found.</p>
      </div>

      <RecycleScroller v-else ref="scrollerRef" class="h-full" :items="tracks" :item-size="56" key-field="id"
        v-slot="{ item, index }" @scroll.passive="handleScroll">
        <div :class="[
          'grid gap-4 px-6 h-[56px] items-center text-sm hover:bg-white/[0.04] group transition-colors',
          showAlbum
            ? 'grid-cols-[minmax(0,1fr)_80px] @[450px]:grid-cols-[40px_minmax(0,1fr)_80px_40px] @[700px]:grid-cols-[40px_minmax(0,1fr)_minmax(0,1fr)_80px_40px] @[1000px]:grid-cols-[40px_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_80px_40px]'
            : 'grid-cols-[minmax(0,1fr)_80px] @[450px]:grid-cols-[40px_minmax(0,1fr)_80px_40px] @[650px]:grid-cols-[40px_minmax(0,1fr)_minmax(0,1fr)_80px_40px]'
        ]">
          <!-- Index / Playing Indicator / Play Button -->
          <div class="hidden @[450px]:flex items-center justify-center h-full">
            <!-- Currently Active Track (Playing or Paused) -->
            <template v-if="isCurrentTrack(item.id)">
              <div class="flex items-end gap-[2px] h-3 w-3">
                <div v-for="i in 3" :key="i"
                  class="w-full h-full bg-primary origin-bottom transition-transform duration-500 ease-in-out" :class="[
                    playerStore.isPlaying ? `animate-playing-bar-${i}` : '',
                  ]" :style="{
                    transform: !playerStore.isPlaying
                      ? (i === 1 ? 'scaleY(0.3)' : i === 2 ? 'scaleY(1.0)' : 'scaleY(0.6)')
                      : undefined
                  }">
                </div>
              </div>
            </template>

            <!-- Other Tracks -->
            <template v-else>
              <div class="text-white/80 group-hover:hidden">{{ index + 1 }}</div>
              <div class="hidden group-hover:block">
                <button @click="emit('play-track', item, index)"
                  class="text-primary hover:scale-110 transition-transform">
                  <Play class="w-4 h-4 fill-current" />
                </button>
              </div>
            </template>
          </div>

          <div class="font-medium truncate flex items-center gap-3 min-w-0">
            <div v-if="showArtwork" class="w-8 h-8 bg-white/5 rounded flex-shrink-0 overflow-hidden">
              <img v-if="item.artwork_key" :src="`/artwork/${item.artwork_key}`" class="w-full h-full object-cover" />
              <div v-else class="w-full h-full flex items-center justify-center text-white/80/30">
                <Music class="w-4 h-4" />
              </div>
            </div>
            <span class="truncate">{{ item.title || 'Unknown Title' }}</span>
          </div>
          <div class="text-white/80 truncate flex items-center gap-2 pr-4 min-w-0 hidden @[650px]:flex">
            <User class="w-3 h-3 opacity-50 flex-shrink-0" />
            <div class="truncate">
              <template v-if="item.artists && item.artists.length > 0">
                <span v-for="(artist, i) in (item.artists.filter(a => !!a) as Artist[])" :key="artist.id || i">
                  <span :class="[artist.id ? 'hover:text-primary cursor-pointer transition-colors' : '']"
                    @click.stop="artist.id && navigateToArtist(artist.id)">
                    {{ artist.name }}
                  </span>
                  <span v-if="i < item.artists.filter(a => !!a).length - 1" class="mr-1">,</span>
                </span>
              </template>
              <span v-else>{{ item.raw_artist_names || 'Unknown Artist' }}</span>
            </div>
          </div>
          <div v-if="showAlbum" class="text-white/80 truncate flex items-center gap-2 min-w-0 hidden @[1000px]:flex">
            <Disc class="w-3 h-3 opacity-50" />
            <span class="truncate group-hover:text-primary transition-colors cursor-pointer"
              @click.stop="item.album?.id && navigateToAlbum(item.album.id)">
              {{ item.album?.title || 'Unknown Album' }}
            </span>
          </div>
          <div class="text-center text-white/80 text-xs">
            {{ formatTime(item.duration) }}
          </div>
          <div class="flex items-center justify-end opacity-0 group-hover:opacity-100 hidden @[450px]:flex">
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

@keyframes playing-bar-1 {

  0%,
  100% {
    transform: scaleY(0.3);
  }

  50% {
    transform: scaleY(0.8);
  }
}

@keyframes playing-bar-2 {

  0%,
  100% {
    transform: scaleY(1.0);
  }

  50% {
    transform: scaleY(0.4);
  }
}

@keyframes playing-bar-3 {

  0%,
  100% {
    transform: scaleY(0.6);
  }

  50% {
    transform: scaleY(0.9);
  }
}

.animate-playing-bar-1 {
  animation: playing-bar-1 0.8s ease-in-out infinite;
}

.animate-playing-bar-2 {
  animation: playing-bar-2 0.6s ease-in-out infinite;
}

.animate-playing-bar-3 {
  animation: playing-bar-3 0.7s ease-in-out infinite;
}
</style>
