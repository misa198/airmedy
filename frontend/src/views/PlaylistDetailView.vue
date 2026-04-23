<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ListMusic } from 'lucide-vue-next'
import * as PlaylistService from '../../bindings/changeme/internal/infra/wails/playlistservice'
import type { Playlist, TrackDTO } from '../../bindings/changeme/internal/domain/models'
import TrackTable from '@/components/TrackTable.vue'
import { usePlayerStore } from '@/stores/player'

const route = useRoute()
const router = useRouter()
const playerStore = usePlayerStore()

const playlist = ref<Playlist | null>(null)
const tracks = ref<TrackDTO[]>([])
const isLoading = ref(true)

async function load() {
  isLoading.value = true
  try {
    const id = route.params.id as string
    const [p, t] = await Promise.all([
      PlaylistService.GetPlaylistByID(id),
      PlaylistService.GetPlaylistTracks(id),
    ])
    playlist.value = p
    tracks.value = t.filter(Boolean) as TrackDTO[]
  } catch (e) {
    console.error('Failed to load playlist', e)
  } finally {
    isLoading.value = false
  }
}

onMounted(load)

const totalDuration = computed(() => {
  const secs = tracks.value.reduce((acc, t) => acc + (t.duration ?? 0), 0)
  const h = Math.floor(secs / 3600)
  const m = Math.floor((secs % 3600) / 60)
  return h > 0 ? `${h}h ${m}m` : `${m} min`
})

function playTrack(_track: TrackDTO, index: number) {
  playerStore.playTracks(tracks.value, index)
}
</script>

<template>
  <div class="flex flex-col h-full overflow-hidden">
    <!-- Header -->
    <div class="px-6 pt-6 pb-4 flex-shrink-0">
      <div v-if="isLoading" class="space-y-2">
        <div class="h-7 w-48 bg-white/[0.06] rounded animate-pulse" />
        <div class="h-4 w-32 bg-white/[0.04] rounded animate-pulse" />
      </div>
      <template v-else-if="playlist">
        <div class="flex items-center gap-3 mb-1">
          <ListMusic class="w-6 h-6 text-white/30 flex-shrink-0" />
          <h1 class="text-2xl font-bold text-white">{{ playlist.name }}</h1>
        </div>
        <p class="text-sm text-white/30 pl-9">
          {{ tracks.length }} tracks · {{ totalDuration }}
        </p>
      </template>
    </div>

    <!-- Track table -->
    <div class="flex-1 overflow-hidden">
      <TrackTable
        :tracks="tracks"
        :is-loading="isLoading"
        :show-album="true"
        :show-artwork="true"
        :scroll-to-current="false"
        @play-track="playTrack"
      />
    </div>
  </div>
</template>
