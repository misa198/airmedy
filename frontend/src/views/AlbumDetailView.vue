<script setup lang="ts">
import { ref, shallowRef, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import LazyImg from '@/components/LazyImg.vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { AlbumDTO, TrackDTO, ThemeColors } from '../../bindings/airmedy/internal/domain/models'
import TrackTable from '@/components/TrackTable.vue'
import { Disc, User, Play, Clock, Calendar, MoreVertical, Music, Shuffle } from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { formatTotalDuration, buildArtworkUrl } from '../lib/utils'
import { useContextMenu } from '@/composables/useContextMenu'
import { useAlbumContextMenu } from '@/composables/useAlbumContextMenu'
import { useRestoreScroll } from '@/composables/useRestoreScroll'
import ContextMenu from '@/components/ContextMenu.vue'
import DetailsButton from '@/components/ui/DetailsButton.vue'
import DetailHero from '@/components/DetailHero.vue'
import { useLibraryUpdates } from '@/composables/useLibraryUpdates'

const playerStore = usePlayerStore()
const { t } = useI18n()

const route = useRoute()
const router = useRouter()
const album = ref<AlbumDTO | null>(null)
const tracks = shallowRef<TrackDTO[]>([])
const isLoading = ref(true)

useLibraryUpdates(tracks)
const albumTheme = ref<ThemeColors | null>(null)

const { scrollContainerRef, handleScroll } = useRestoreScroll()

const contextMenu = useContextMenu()
const { buildMenuItems } = useAlbumContextMenu()

function openContextMenu(e: MouseEvent) {
  if (album.value) {
    contextMenu.open(e, buildMenuItems(album.value, tracks.value, { hidePlayShuffle: true }))
  }
}

const loadAlbumDetails = async (id: string) => {
  isLoading.value = true
  try {
    const [albumData, tracksData] = await Promise.all([
      LibraryService.GetAlbumByID(id),
      LibraryService.GetTracksByAlbumID(id)
    ])
    album.value = albumData
    tracks.value = tracksData.filter((t): t is TrackDTO => t !== null)

    // Fetch album colors for local theme
    try {
      const colors = await LibraryService.GetAlbumColors(id)
      albumTheme.value = colors
    } catch (e) {
      console.warn('Failed to fetch album colors', e)
    }
  } catch (err) {
    console.error('Failed to load album details:', err)
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  const id = route.params.id as string
  if (id) loadAlbumDetails(id)
})

watch(() => route.params.id, (newId) => {
  if (newId) loadAlbumDetails(newId as string)
})

const getTotalDuration = (tracks: TrackDTO[]) => {
  const totalSeconds = tracks.reduce((acc, t) => acc + (t.duration || 0), 0)
  return formatTotalDuration(totalSeconds, t)
}
</script>

<template>
  <div class="h-full flex flex-col bg-background overflow-hidden">
    <div v-if="isLoading" class="flex-1 flex items-center justify-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
    </div>

    <div v-else-if="album" ref="scrollContainerRef" class="flex-1 overflow-y-auto" @scroll.passive="handleScroll">
      <DetailHero 
        :theme="albumTheme" 
        :title="album.title || $t('library.unknown_album')"
        @contextmenu.prevent="openContextMenu"
      >
        <template #artwork>
          <div class="w-48 h-48 rounded-lg shadow-2xl overflow-hidden ring-1 ring-foreground/[0.08] bg-foreground/5 flex-shrink-0">
            <LazyImg v-if="album.artwork_key" :src="buildArtworkUrl(album.artwork_key, 'md')" class="w-full h-full object-cover" />
            <div v-else class="w-full h-full flex items-center justify-center text-foreground opacity-30">
              <Disc class="w-24 h-24" />
            </div>
          </div>
        </template>

        <template #metadata>
          <div class="flex items-center gap-2 text-foreground font-semibold min-w-0">
            <User class="w-4 h-4 flex-shrink-0" />
            <span class="line-clamp-1">{{album.artists?.map(a => a?.name).join(', ') ||
              $t('library.unknown_artist')}}</span>
          </div>
          <div class="flex gap-2 text-sm items-end flex-wrap">
            <div v-if="album.year" class="flex items-center gap-2">
              <Calendar class="w-4 h-4" />
              <span>{{ album.year }}</span>
            </div>
            <div class="flex items-center gap-2">
              <Music class="w-4 h-4" />
              <span>{{ tracks.length }} {{ $t('library.songs') }}</span>
            </div>
            <div class="flex items-center gap-2">
              <Clock class="w-4 h-4" />
              <span>{{ getTotalDuration(tracks) }}</span>
            </div>
          </div>
        </template>

        <template #actions>
          <DetailsButton :icon="Play" :label="$t('common.play')"
            @click="playerStore.playTracks(tracks, 0)" />
          <div class="flex gap-2">
            <DetailsButton :icon="Shuffle" variant="outline" @click="playerStore.shuffleTracks(tracks)" />
            <DetailsButton :icon="MoreVertical" variant="outline" @click="openContextMenu" />
          </div>
        </template>
      </DetailHero>

      <!-- Track List -->
      <div class="px-2 pb-12">
        <TrackTable
          :tracks="tracks"
          :show-artwork="false"
          :simple-mode="true"
          @play-track="(_, index, queue) => playerStore.playTracks(queue, index)"
          @navigate-album="id => router.push(`/albums/${id}`)"
          @navigate-artist="id => router.push(`/artists/${id}`)"
        />
      </div>

      <!-- Album Footer Metadata -->
      <div v-if="album.copyright"
        class="px-8 pb-12 text-sm text-foreground opacity-50 border-t border-foreground/[0.06] pt-8 mt-4">
        {{ album.copyright }}
      </div>
    </div>

    <ContextMenu :visible="contextMenu.visible.value" :x="contextMenu.x.value" :y="contextMenu.y.value"
      :items="contextMenu.items.value" @close="contextMenu.close()" />
  </div>
</template>
