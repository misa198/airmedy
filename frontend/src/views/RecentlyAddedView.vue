<script setup lang="ts">
import { ref, shallowRef, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { Disc } from 'lucide-vue-next'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import VirtualizedGrid from '../components/VirtualizedGrid.vue'
import TrackCard from '../components/TrackCard.vue'
import TrackContextMenu from '../components/TrackContextMenu.vue'
import ViewHeader from '../components/ViewHeader.vue'
import { usePlayerStore } from '../stores/player'
import { useLibrarySync } from '../composables/useLibrarySync'

const router = useRouter()
const playerStore = usePlayerStore()
const trackContextMenu = ref<InstanceType<typeof TrackContextMenu> | null>(null)

const tracks = shallowRef<TrackDTO[]>([])
const isLoading = ref(true)

const loadRecentlyAdded = async (silent = false) => {
  if (!silent) isLoading.value = true
  try {
    const result = await LibraryService.GetRecentlyAddedTracks(50)
    tracks.value = result.filter((t): t is TrackDTO => t !== null)
  } catch (err) {
    console.error('Failed to load recently added tracks:', err)
  } finally {
    if (!silent) isLoading.value = false
  }
}

const playTrack = (track: TrackDTO) => {
  const index = tracks.value.indexOf(track)
  playerStore.playTracks(tracks.value, index < 0 ? 0 : index)
}

const navigateToTrack = (track: TrackDTO) => {
  if (track.album) router.push(`/albums/${track.album.id}`)
}

const navigateToArtist = (id: string) => {
  if (id) router.push(`/artists/${id}`)
}

const navigateToAlbum = (id: string) => {
  if (id) router.push(`/albums/${id}`)
}

const onTrackContextMenu = (e: MouseEvent, track: TrackDTO) => {
  trackContextMenu.value?.open(e, track)
}

onMounted(() => loadRecentlyAdded())
// Event-driven reloads are silent so background refreshes don't flash the spinner.
useLibrarySync(() => loadRecentlyAdded(true))
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden bg-background">
    <ViewHeader :title="$t('library.recently_added')" />

    <div class="flex-1 overflow-hidden px-6 py-8">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>

      <div v-else-if="tracks.length === 0" class="h-full flex flex-col items-center justify-center text-foreground opacity-60">
        <Disc class="w-12 h-12 mb-4 opacity-20" />
        <p>{{ $t('library.no_tracks') }}</p>
      </div>

      <VirtualizedGrid
        v-else
        :items="tracks"
        :square-items="true"
        :text-area-height="60"
        :min-column-width="180"
        :gap="40"
      >
        <template #default="{ item: track }">
          <TrackCard
            :track="track"
            @play="playTrack"
            @click="navigateToTrack"
            @artist-click="navigateToArtist"
            @album-click="navigateToAlbum"
            @contextmenu="onTrackContextMenu"
          />
        </template>
      </VirtualizedGrid>
    </div>

    <TrackContextMenu ref="trackContextMenu" />
  </div>
</template>
