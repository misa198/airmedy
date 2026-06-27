<script setup lang="ts">
import ArtistCard from '@/components/ArtistCard.vue'
import { useContextMenu } from '@/composables/useContextMenu'
import { useGroupContextMenu } from '@/composables/useGroupContextMenu'
import { sortTracksGrouped } from '@/lib/trackSort'
import { DetailsButton } from '@airmedy/ui'
import { hexToRgba } from '@airmedy/utils'
import { Disc, ImagePlus, MoreVertical, Music, Play, Shuffle, Trash2 } from 'lucide-vue-next'
import { Events } from '@wailsio/runtime'
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import type { AlbumDTO, Artist, ThemeColors, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import ContextMenu from '../components/ContextMenu.vue'
import GroupedAlbumList from '../components/GroupedAlbumList.vue'
import { usePlayerStore } from '../stores/player'
import { useLibrarySync } from '@/composables/useLibrarySync'

const { t } = useI18n()

const route = useRoute()
const router = useRouter()
const playerStore = usePlayerStore()
const artist = ref<Artist | null>(null)
const albums = shallowRef<AlbumDTO[]>([])
const tracks = shallowRef<TrackDTO[]>([])
const artistTheme = shallowRef<ThemeColors | null>(null)
const isLoading = ref(true)

const contextMenu = useContextMenu()
const { buildMenuItems } = useGroupContextMenu()
const sortedTracks = computed(() => sortTracksGrouped(tracks.value, albums.value))

function openContextMenu(e: MouseEvent) {
  contextMenu.open(e, buildMenuItems(tracks.value))
}

const loadTheme = async (id: string) => {
  artistTheme.value = null
  try {
    artistTheme.value = await LibraryService.GetArtistColors(id)
  } catch (err) {
    console.error('Failed to load artist colors:', err)
  }
}

// silent: event-driven reload that keeps the current view visible (no spinner /
// theme flash) and swaps data in when it arrives.
const loadArtistDetails = async (id: string, silent = false) => {
  if (!silent) {
    isLoading.value = true
    loadTheme(id)
  }
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
    if (!silent) isLoading.value = false
  }
}

// Track edits can move tracks/albums in or out of this artist; refresh silently.
useLibrarySync(() => {
  const id = route.params.id as string
  if (id) loadArtistDetails(id, true)
})

const refreshArtist = async (id: string) => {
  try {
    artist.value = await LibraryService.GetArtistByID(id)
  } catch (err) {
    console.error('Failed to refresh artist:', err)
  }
}

async function handleSetArtistImage() {
  if (!artist.value) return
  try {
    const url = await LibraryService.SelectAndSetArtistArtwork(artist.value.id)
    if (url) refreshArtist(artist.value.id)
  } catch (err) {
    console.error('Failed to set artist image:', err)
  }
}

async function handleRemoveArtistImage() {
  if (!artist.value) return
  try {
    await LibraryService.RemoveArtistArtwork(artist.value.id)
    refreshArtist(artist.value.id)
  } catch (err) {
    console.error('Failed to remove artist image:', err)
  }
}

function openArtworkMenu(e: MouseEvent) {
  if (!artist.value) return
  contextMenu.open(e, [
    { label: t('artist.set_custom_image'), icon: ImagePlus, action: handleSetArtistImage },
    {
      label: t('artist.remove_custom_image'),
      icon: Trash2,
      danger: true,
      disabled: !artist.value.artwork_key_manual,
      action: handleRemoveArtistImage,
    },
  ])
}

let offArtworkUpdated: (() => void) | null = null

onMounted(() => {
  const id = route.params.id as string
  if (id) loadArtistDetails(id)

  // Artwork can change asynchronously (e.g. Deezer online fetch completes);
  // re-extract colors so the hero tint follows the new image.
  offArtworkUpdated = Events.On('artist-artwork-updated', (ev) => {
    const data = ev.data as { artist_id?: string } | undefined
    if (data?.artist_id && data.artist_id === artist.value?.id) {
      loadTheme(data.artist_id)
    }
  })
})

onUnmounted(() => {
  if (offArtworkUpdated) offArtworkUpdated()
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
        class="p-8 md:p-12 flex flex-col md:flex-row gap-8 items-center bg-gradient-to-b from-dynamic-surface to-transparent border-b border-foreground/[0.06]"
        :style="{ '--dynamic-surface': artistTheme ? hexToRgba(artistTheme.dominant, 0.15) : 'var(--bg-glass)' }">
        <div
          class="group relative w-32 h-32 xl:w-42 xl:h-42 rounded-full shadow-2xl overflow-hidden ring-2 ring-foreground/[0.08] bg-foreground/5 flex-shrink-0"
          @contextmenu="openArtworkMenu">
          <ArtistCard :artist="artist" variant="avatar" />
          <button type="button"
            class="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
            :title="t('artist.edit_image')" @click.stop="openArtworkMenu">
            <span
              class="text-white text-xs font-medium px-2 py-1 bg-black/20 rounded-full backdrop-blur-sm">{{ t('artist.edit_image') }}</span>
          </button>
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
