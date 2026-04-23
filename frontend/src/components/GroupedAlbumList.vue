<script setup lang="ts">
import { computed } from 'vue'
import { Play, Disc } from 'lucide-vue-next'
import type { TrackDTO, AlbumDTO } from '../../bindings/changeme/internal/domain/models'
import { usePlayerStore } from '../stores/player'
import TrackTable from './TrackTable.vue'

const playerStore = usePlayerStore()
const props = defineProps<{
  tracks: TrackDTO[]
  albums?: AlbumDTO[]
}>()

const TRACK_ROW_HEIGHT = 56
const TABLE_HEADER_HEIGHT = 41

const groupedAlbums = computed(() => {
  const groups: Record<string, { album: AlbumDTO | null, tracks: TrackDTO[] }> = {}

  if (props.albums) {
    for (const album of props.albums) {
      groups[album.id] = { album, tracks: [] }
    }
  }

  const unknownAlbumId = 'unknown'
  for (const track of props.tracks) {
    const albumId = track.album?.id || unknownAlbumId
    if (!groups[albumId]) {
      groups[albumId] = { album: track.album || null, tracks: [] }
    }
    groups[albumId].tracks.push(track)
  }

  const result = Object.values(groups).filter(g => g.tracks.length > 0)

  result.sort((a, b) => {
    if (a.album?.id === unknownAlbumId) return 1
    if (b.album?.id === unknownAlbumId) return -1
    const yearA = a.album?.year || 0
    const yearB = b.album?.year || 0
    if (yearA !== yearB) return yearB - yearA
    return (a.album?.title || '').localeCompare(b.album?.title || '')
  })

  for (const group of result) {
    group.tracks.sort((t1, t2) => {
      const d1 = t1.disc_number || 1
      const d2 = t2.disc_number || 1
      if (d1 !== d2) return d1 - d2
      return (t1.track_number || 0) - (t2.track_number || 0)
    })
  }

  return result
})

function tableHeight(trackCount: number): string {
  return `${trackCount * TRACK_ROW_HEIGHT + TABLE_HEADER_HEIGHT}px`
}
</script>

<template>
  <div class="space-y-12 pb-12">
    <div v-for="group in groupedAlbums" :key="group.album?.id || 'unknown'" class="space-y-4">
      <!-- Album Header -->
      <div class="flex items-end gap-6 px-2">
        <div class="w-32 h-32 md:w-40 md:h-40 rounded-xl shadow-xl overflow-hidden ring-1 ring-white/8 bg-white/5 flex-shrink-0 group relative">
          <img v-if="group.album?.artwork_key" :src="`/artwork/${group.album.artwork_key}`" class="w-full h-full object-cover" />
          <div v-else class="w-full h-full flex items-center justify-center text-white/10">
            <Disc class="w-16 h-16" />
          </div>
          <div v-if="group.album" class="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
            <button
              class="w-12 h-12 bg-white text-black rounded-full shadow-xl flex items-center justify-center transform scale-90 group-hover:scale-100 transition-all duration-300"
              @click="playerStore.playTracks(group.tracks, 0)"
            >
              <Play class="w-6 h-6 fill-current ml-1" />
            </button>
          </div>
        </div>

        <div class="flex-1 pb-2">
          <h2 class="text-2xl md:text-3xl font-bold tracking-tight mb-1">{{ group.album?.title || 'Unknown Album' }}</h2>
          <div class="flex items-center gap-3 text-sm text-white/40">
            <span v-if="group.album?.year" class="font-medium">{{ group.album.year }}</span>
            <span v-if="group.album?.year">•</span>
            <span>{{ group.tracks.length }} tracks</span>
          </div>
        </div>
      </div>

      <!-- Virtualized Track Table -->
      <div class="rounded-xl overflow-hidden ring-1 ring-white/[0.06]" :style="{ height: tableHeight(group.tracks.length) }">
        <TrackTable
          :tracks="group.tracks"
          @play-track="(_, index) => playerStore.playTracks(group.tracks, index)"
        />
      </div>
    </div>
  </div>
</template>
