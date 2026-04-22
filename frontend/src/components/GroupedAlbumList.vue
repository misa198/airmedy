<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Play, Disc, Clock, MoreVertical, Music, User } from 'lucide-vue-next'
import type { TrackDTO, AlbumDTO, Artist } from '../../bindings/changeme/internal/domain/models'

const router = useRouter()
const props = defineProps<{
  tracks: TrackDTO[]
  albums?: AlbumDTO[] // Optional explicit albums (for sorting/completeness)
}>()

const emit = defineEmits<{
  'play': [track: TrackDTO]
  'play-album': [album: AlbumDTO]
}>()

const navigateToArtist = (id: string) => {
  if (id) router.push(`/artists/${id}`)
}

// Group tracks by album
const groupedAlbums = computed(() => {
  const groups: Record<string, { album: AlbumDTO | null, tracks: TrackDTO[] }> = {}
  
  // If albums are explicitly provided, initialize them in order
  if (props.albums) {
    for (const album of props.albums) {
      groups[album.id] = { album, tracks: [] }
    }
  }

  // Group tracks
  const unknownAlbumId = 'unknown'
  for (const track of props.tracks) {
    const albumId = track.album?.id || unknownAlbumId
    if (!groups[albumId]) {
      groups[albumId] = { album: track.album || null, tracks: [] }
    }
    groups[albumId].tracks.push(track)
  }

  // Convert to array and filter out empty explicit albums (optional, but usually good)
  // Or just return the array. Let's sort by year desc if album exists.
  const result = Object.values(groups).filter(g => g.tracks.length > 0)
  
  result.sort((a, b) => {
    if (a.album?.id === unknownAlbumId) return 1;
    if (b.album?.id === unknownAlbumId) return -1;
    const yearA = a.album?.year || 0
    const yearB = b.album?.year || 0
    if (yearA !== yearB) return yearB - yearA // Descending year
    return (a.album?.title || '').localeCompare(b.album?.title || '')
  })

  // Sort tracks within each album by disc/track number
  for (const group of result) {
    group.tracks.sort((t1, t2) => {
      const d1 = t1.disc_number || 1
      const d2 = t2.disc_number || 1
      if (d1 !== d2) return d1 - d2
      const n1 = t1.track_number || 0
      const n2 = t2.track_number || 0
      return n1 - n2
    })
  }

  return result
})

const formatDuration = (seconds: number) => {
  if (!seconds) return '0:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
}
</script>

<template>
  <div class="space-y-12 pb-12">
    <div v-for="group in groupedAlbums" :key="group.album?.id || 'unknown'" class="space-y-4">
      <!-- Album Header -->
      <div class="flex items-end gap-6 px-2">
        <div class="w-32 h-32 md:w-40 md:h-40 rounded-lg shadow-md overflow-hidden border bg-muted flex-shrink-0 group relative">
          <img v-if="group.album?.artwork_key" :src="`/artwork/${group.album.artwork_key}`" class="w-full h-full object-cover" />
          <div v-else class="w-full h-full flex items-center justify-center text-muted-foreground/20">
            <Disc class="w-16 h-16" />
          </div>
          <div v-if="group.album" class="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
            <button @click="emit('play-album', group.album)" class="w-12 h-12 bg-primary text-primary-foreground rounded-full shadow-xl flex items-center justify-center transform scale-90 group-hover:scale-100 transition-all duration-300">
              <Play class="w-6 h-6 fill-current ml-1" />
            </button>
          </div>
        </div>

        <div class="flex-1 pb-2">
          <h2 class="text-2xl md:text-3xl font-bold tracking-tight mb-1">{{ group.album?.title || 'Unknown Album' }}</h2>
          <div class="flex items-center gap-3 text-sm text-muted-foreground">
            <span v-if="group.album?.year" class="font-medium">{{ group.album.year }}</span>
            <span v-if="group.album?.year">•</span>
            <span>{{ group.tracks.length }} tracks</span>
          </div>
        </div>
      </div>

      <!-- Tracks Table (Non-virtualized for single album) -->
      <div class="border rounded-xl bg-card overflow-hidden shadow-sm">
        <!-- Table Header -->
        <div class="grid grid-cols-[48px_1fr_1fr_80px_48px] gap-4 px-6 py-3 border-b text-xs font-medium text-muted-foreground uppercase tracking-wider bg-muted/30">
          <div class="text-center">#</div>
          <div>Title</div>
          <div>Artist</div>
          <div class="flex items-center gap-1 justify-center"><Clock class="w-3 h-3" /></div>
          <div></div>
        </div>

        <!-- Tracks -->
        <div class="divide-y">
          <div 
            v-for="(track, index) in group.tracks" 
            :key="track.id"
            class="grid grid-cols-[48px_1fr_1fr_80px_48px] gap-4 px-6 h-[56px] items-center text-sm hover:bg-accent/50 group/track transition-colors"
          >
            <div class="text-center text-muted-foreground group-hover/track:hidden">{{ track.track_number || index + 1 }}</div>
            <div class="hidden group-hover/track:flex items-center justify-center">
              <button @click="emit('play', track)" class="text-primary hover:scale-110 transition-transform">
                <Play class="w-4 h-4 fill-current" />
              </button>
            </div>
            
            <div class="font-medium truncate pr-4">
              {{ track.title || 'Unknown Title' }}
            </div>
            
            <div class="text-muted-foreground truncate flex items-center gap-2 pr-4">
              <User class="w-3 h-3 opacity-50 flex-shrink-0" />
              <div class="truncate">
                <template v-if="track.artists && track.artists.length > 0">
                  <span v-for="(artist, i) in (track.artists.filter(a => !!a) as Artist[])" :key="artist.id || i">
                    <span 
                      :class="[artist.id ? 'hover:text-primary cursor-pointer transition-colors' : '']"
                      @click.stop="artist.id && navigateToArtist(artist.id)"
                    >
                      {{ artist.name }}
                    </span>
                    <span v-if="i < track.artists.filter(a => !!a).length - 1" class="mr-1">,</span>
                  </span>
                </template>
                <span v-else>{{ track.raw_artist_names || 'Unknown Artist' }}</span>
              </div>
            </div>
            
            <div class="text-center text-muted-foreground font-mono text-xs">
              {{ formatDuration(track.duration) }}
            </div>
            
            <div class="flex items-center justify-end opacity-0 group-hover/track:opacity-100">
              <button class="p-2 hover:bg-accent rounded-full text-muted-foreground hover:text-foreground">
                <MoreVertical class="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
