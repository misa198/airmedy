<script setup lang="ts">
import { ref, shallowRef, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useDetailRouteLoader } from '@/composables/useDetailRouteLoader'
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
import { useLibrarySync } from '@/composables/useLibrarySync'
import { useTrackTableSettings } from '@/composables/useTrackTableSettings'
import { Events } from '@wailsio/runtime'

const TABLE_HEADER_HEIGHT = 41
const settings = useTrackTableSettings()

const playerStore = usePlayerStore()
const { t } = useI18n()

const router = useRouter()
const album = ref<AlbumDTO | null>(null)
const tracks = shallowRef<TrackDTO[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

// An album-name edit normally creates a new normalized album ID. Move the
// detail route with the edited track instead of attempting to render the
// deleted predecessor as an empty page. Register before useLibraryUpdates so
// this observes membership before that composable removes the old track.
const handleTrackUpdated = (event: Events.WailsEvent) => {
  const updated = event.data as TrackDTO
  if (!updated?.id || !updated.album?.id || updated.album.id === album.value?.id) return
  if (tracks.value.some(track => track.id === updated.id)) {
    router.replace(`/albums/${updated.album.id}`)
  }
}
let offTrackUpdated: (() => void) | null = null
onMounted(() => {
  offTrackUpdated = Events.On('library:track-updated', handleTrackUpdated)
})
onUnmounted(() => offTrackUpdated?.())

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
  belongs: t => t.album?.id === album.value?.id,
})
const albumTheme = ref<ThemeColors | null>(null)

const contextMenu = useContextMenu()
const { buildMenuItems } = useAlbumContextMenu()

function openContextMenu(e: MouseEvent) {
  if (album.value) {
    contextMenu.open(e, buildMenuItems(album.value, tracks.value, { hidePlayShuffle: false }))
  }
}

// Synchronous token marking the most recently requested album, set before
// any await so a fast-resolving load can't be mistaken for stale (comparing
// against vue-router's reactive route.params.id instead is racy: it may not
// have finished updating yet by the time a quick fetch resolves).
let currentAlbumId: string | null = null
const isStale = (id: string) => currentAlbumId !== id

const loadAlbumDetails = async (id: string, silent = false) => {
  currentAlbumId = id
  if (!silent) isLoading.value = true
  try {
    const [albumData, tracksData] = await Promise.all([
      LibraryService.GetAlbumByID(id),
      LibraryService.GetTracksByAlbumID(id)
    ])
    if (isStale(id)) return
    album.value = albumData
    tracks.value = tracksData.filter((t): t is TrackDTO => t !== null)

    // Fetch album colors for local theme
    try {
      const colors = await LibraryService.GetAlbumColors(id)
      if (isStale(id)) return
      albumTheme.value = colors
    } catch (e) {
      console.warn('Failed to fetch album colors', e)
    }
  } catch (err) {
    console.error('Failed to load album details:', err)
  } finally {
    if (!silent && !isStale(id)) isLoading.value = false
  }
}

useDetailRouteLoader(loadAlbumDetails)

// An edit can replace this album's entity (for example, a title change creates
// a new normalized album ID). Reload the header and membership rather than
// retaining the now-stale album DTO.
useLibrarySync(() => {
  const id = album.value?.id
  if (id) void loadAlbumDetails(id, true)
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
