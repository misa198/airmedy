<script setup lang="ts">
import { ref, shallowRef, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { Artist, AlbumDTO, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import GroupedAlbumList from '../components/GroupedAlbumList.vue'
import ArtistCard from '@/components/ArtistCard.vue'
import { User, Shuffle, Disc, Music, Play, MoreVertical } from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { useI18n } from 'vue-i18n'
import { useContextMenu } from '@/composables/useContextMenu'
import { useGroupContextMenu } from '@/composables/useGroupContextMenu'
import ContextMenu from '../components/ContextMenu.vue'
import DetailsButton from '@/components/ui/DetailsButton.vue'
import { sortTracksGrouped } from '@/lib/trackSort'

const { t } = useI18n()

const route = useRoute()
const router = useRouter()
const playerStore = usePlayerStore()
const artist = ref<Artist | null>(null)
const albums = shallowRef<AlbumDTO[]>([])
const tracks = shallowRef<TrackDTO[]>([])
const isLoading = ref(true)

const contextMenu = useContextMenu()
const { buildMenuItems } = useGroupContextMenu()
const sortedTracks = computed(() => sortTracksGrouped(tracks.value, albums.value))

function openContextMenu(e: MouseEvent) {
  contextMenu.open(e, buildMenuItems(tracks.value))
}

const loadArtistDetails = async (id: string) => {
  isLoading.value = true
  try {
    const [artistData, albumsData, tracksData] = await Promise.all([
      LibraryService.GetArtistByID(id),
      LibraryService.GetAlbumsByArtistID(id),
      LibraryService.GetTracksByArtistID(id)
    ])
    artist.value = artistData
    albums.value = albumsData.filter((a): a is AlbumDTO => a !== null)
    tracks.value = tracksData.filter((t): t is TrackDTO => t !== null)
  } catch (err) {
    console.error('Failed to load artist details:', err)
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  const id = route.params.id as string
  if (id) loadArtistDetails(id)
})

watch(() => route.params.id, (newId) => {
  if (newId) loadArtistDetails(newId as string)
})
</script>

<template>
  <div class="h-full flex flex-col bg-background overflow-hidden">
    <div v-if="isLoading" class="flex-1 flex items-center justify-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
    </div>

    <div v-else-if="artist" class="flex-1 overflow-y-auto">
      <!-- Artist Hero Section -->
      <div
        class="p-8 md:p-12 flex flex-col md:flex-row gap-8 items-center bg-gradient-to-b from-dynamic-surface to-transparent border-b border-foreground/[0.06]">
        <div
          class="w-32 h-32 xl:w-42 xl:h-42 rounded-full shadow-2xl overflow-hidden ring-2 ring-foreground/[0.08] bg-foreground/5 flex-shrink-0">
          <ArtistCard :artist="artist" variant="avatar" />
        </div>

        <div class="flex-1 text-center md:text-left space-y-4 @container min-w-0">
          <div class="space-y-1">
            <h1
              class="text-3xl @sm:text-4xl @md:text-5xl @lg:text-7xl font-bold tracking-tight line-clamp-2 hyphens-auto leading-snug text-foreground">
              {{
                artist.name || t('library.unknown_artist') }}</h1>
            <div
              class="text-sm flex flex-wrap items-center justify-center md:justify-start gap-4 text-foreground opacity-60">
              <span class="flex items-center gap-1">
                <Disc class="w-4 h-4" /> {{ t('artist.albums_count', { count: albums.length }) }}
              </span>
              <span class="flex items-center gap-1">
                <Music class="w-4 h-4" /> {{ t('artist.songs_count', { count: tracks.length }) }}
              </span>
            </div>
          </div>

          <div class="flex items-center justify-center md:justify-start gap-4 flex-wrap">
            <DetailsButton :icon="Play" :label="t('common.play')"
              @click="playerStore.playTracks(sortedTracks, 0)" />
            <div class="flex gap-2">
              <DetailsButton :icon="Shuffle" variant="outline" @click="playerStore.shuffleTracks(tracks)" />
              <DetailsButton :icon="MoreVertical" variant="outline" @click="openContextMenu" />
            </div>
          </div>
        </div>
      </div>

      <div class="p-8">
        <GroupedAlbumList :tracks="tracks" :albums="albums" />
      </div>
    </div>

    <ContextMenu :visible="contextMenu.visible.value" :x="contextMenu.x.value" :y="contextMenu.y.value"
      :items="contextMenu.items.value" @close="contextMenu.close()" />
  </div>
</template>
