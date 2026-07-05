<script setup lang="ts">
import { ref, shallowRef, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import LazyImg from '@/components/LazyImg.vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { AlbumDTO, TrackDTO, ThemeColors } from '../../bindings/airmedy/internal/domain/models'
import TrackTable from '@/components/TrackTable.vue'
import { Disc, User, Play, Clock, Calendar, MoreVertical, Music, Shuffle, Search } from '@lucide/vue'
import { usePlayerStore } from '../stores/player'
import { formatTotalDuration, buildArtworkUrl, foldUnicode } from '@airmedy/utils'
import { useContextMenu } from '@/composables/useContextMenu'
import { useAlbumContextMenu } from '@/composables/useAlbumContextMenu'
import ContextMenu from '@/components/ContextMenu.vue'
import { DetailsButton, Input } from '@airmedy/ui'
import DetailPageLayout from '@/components/DetailPageLayout.vue'
import { useLibraryUpdates } from '@/composables/useLibraryUpdates'
import { useTrackTableSettings } from '@/composables/useTrackTableSettings'

const TABLE_HEADER_HEIGHT = 41
const settings = useTrackTableSettings()

const playerStore = usePlayerStore()
const { t } = useI18n()

const route = useRoute()
const router = useRouter()
const album = ref<AlbumDTO | null>(null)
const tracks = shallowRef<TrackDTO[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

const filteredTracks = computed(() => {
  if (!searchQuery.value) return tracks.value
  const q = foldUnicode(searchQuery.value)
  return tracks.value.filter(t =>
    foldUnicode(t.title || '').includes(q) ||
    foldUnicode(t.raw_artist_names || '').includes(q)
  )
})

const tableHeight = computed(() => {
  const rowHeight = settings.collapsedMode.value ? 36 : 56
  return `${filteredTracks.value.length * rowHeight + TABLE_HEADER_HEIGHT}px`
})

// Drop a track from this view if an edit moved it to a different album.
useLibraryUpdates(tracks, {
  belongs: t => t.album?.id === route.params.id,
})
const albumTheme = ref<ThemeColors | null>(null)

const contextMenu = useContextMenu()
const { buildMenuItems } = useAlbumContextMenu()

function openContextMenu(e: MouseEvent) {
  if (album.value) {
    contextMenu.open(e, buildMenuItems(album.value, tracks.value, { hidePlayShuffle: false }))
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
  <DetailPageLayout
    v-if="album"
    :loading="isLoading"
    :theme="albumTheme"
    :title="album.title || $t('library.unknown_album')"
    body-class="px-2"
    :body-fill-height="false"
    @hero-contextmenu="openContextMenu"
  >
    <template #top-right>
      <div class="relative w-64">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground opacity-60" />
        <Input
          v-model="searchQuery"
          type="text"
          :placeholder="$t('sidebar.search')"
          class="pl-10 pr-4"
          clearable
        />
      </div>
    </template>

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
      <DetailsButton :icon="Play" :label="$t('common.play')" :filled-icon="true"
        @click="playerStore.playTracks(filteredTracks, 0)" />
      <div class="flex gap-2">
        <DetailsButton :icon="Shuffle" variant="outline" @click="playerStore.shuffleTracks(filteredTracks)" />
        <DetailsButton :icon="MoreVertical" variant="outline" @click="openContextMenu" />
      </div>
    </template>

    <template #body>
      <div :style="{ height: tableHeight }">
        <TrackTable
          :tracks="filteredTracks"
          :show-artwork="false"
          :simple-mode="true"
          :virtual-scroll="false"
          @play-track="(_, index, queue) => playerStore.playTracks(queue, index)"
          @navigate-album="id => router.push(`/albums/${id}`)"
          @navigate-artist="id => router.push(`/artists/${id}`)"
        />
      </div>
    </template>

    <template #footer>
      <div v-if="album.copyright"
        class="px-8 pb-12 text-sm text-foreground opacity-50 border-t border-foreground/[0.06] pt-8 mt-4">
        {{ album.copyright }}
      </div>
    </template>
  </DetailPageLayout>
  <div v-else-if="isLoading" class="h-full flex items-center justify-center">
    <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
  </div>

  <ContextMenu :visible="contextMenu.visible.value" :x="contextMenu.x.value" :y="contextMenu.y.value"
    :items="contextMenu.items.value" @close="contextMenu.close()" />
</template>
