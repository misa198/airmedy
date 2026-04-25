<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Play, Shuffle, MoreVertical, Clock, Music, ListMusic, X, Heart } from 'lucide-vue-next'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { Playlist, TrackDTO, ThemeColors } from '../../bindings/airmedy/internal/domain/models'
import TrackTable from '@/components/TrackTable.vue'
import { usePlayerStore } from '@/stores/player'
import { useFavoritesStore } from '@/stores/favorites'
import { formatTotalDuration } from '@/lib/utils'
import DetailsButton from '@/components/ui/DetailsButton.vue'
import { useContextMenu } from '@/composables/useContextMenu'
import { useGroupContextMenu } from '@/composables/useGroupContextMenu'
import { useRestoreScroll } from '@/composables/useRestoreScroll'
import ContextMenu from '@/components/ContextMenu.vue'
import DetailHero from '@/components/DetailHero.vue'
import { useLibraryUpdates } from '@/composables/useLibraryUpdates'

const route = useRoute()
const { t } = useI18n()
const playerStore = usePlayerStore()
const favoritesStore = useFavoritesStore()

const playlist = ref<Playlist | null>(null)
const tracks = ref<TrackDTO[]>([])
const isLoading = ref(true)

useLibraryUpdates(tracks)
const playlistTheme = ref<ThemeColors | null>(null)

const { scrollContainerRef, handleScroll } = useRestoreScroll()

const contextMenu = useContextMenu()
const { buildMenuItems } = useGroupContextMenu()

function openContextMenu(e: MouseEvent) {
  contextMenu.open(e, buildMenuItems(tracks.value))
}

async function load(silent = false) {
  const id = route.params.id as string
  if (!id) return

  if (!silent) isLoading.value = true

  // Handle favorites virtual playlist
  if (id === 'favorites') {
    playlist.value = {
      id: 'favorites',
      name: t('sidebar.favorites'),
      description: '',
      artwork_key: null,
    } as Playlist

    try {
      const result = await LibraryService.GetFavoriteTracks()
      tracks.value = result.filter((t): t is TrackDTO => t !== null)
      await loadTheme()
    } catch (e) {
      console.error('Failed to load favorite tracks', e)
    } finally {
      if (!silent) isLoading.value = false
    }
    return
  }

  try {
    const [p, t] = await Promise.all([
      PlaylistService.GetPlaylistByID(id),
      PlaylistService.GetPlaylistTracks(id),
    ])
    playlist.value = p
    tracks.value = t.filter((t): t is TrackDTO => t !== null)

    // Load theme
    await loadTheme()
  } catch (e) {
    console.error('Failed to load playlist', e)
  } finally {
    if (!silent) isLoading.value = false
  }
}

async function loadTheme() {
  if (!playlist.value) return
  
  try {
    // 1. Try playlist custom theme
    let colors: ThemeColors | null = null
    if (playlist.value.id !== 'favorites') {
      colors = await PlaylistService.GetPlaylistColors(playlist.value.id)
    }
    
    // 2. Fallback to first track's album theme if no custom artwork
    if (!colors && tracks.value.length > 0) {
      const firstTrack = tracks.value[0]
      if (firstTrack.album_id) {
        colors = await LibraryService.GetAlbumColors(firstTrack.album_id)
      }
    }
    
    playlistTheme.value = colors
  } catch (e) {
    console.warn('Failed to load playlist theme', e)
  }
}

watch(() => route.params.id, () => load())
watch(() => favoritesStore.version, () => {
  if (route.params.id === 'favorites') load(true)
})
onMounted(load)

const totalDurationFormatted = computed(() => {
  const totalSeconds = tracks.value.reduce((acc, t) => acc + (t.duration || 0), 0)
  return formatTotalDuration(totalSeconds, t)
})

const playlistArtworks = computed(() => {
  if (playlist.value?.artwork_key) {
    return [playlist.value.artwork_key]
  }

  const keys = new Set<string>()
  for (const track of tracks.value) {
    const key = track.artwork_key || track.album?.artwork_key
    if (key) {
      keys.add(key)
      if (keys.size >= 4) break
    }
  }
  return Array.from(keys)
})

async function handleSetArtwork() {
  if (!playlist.value || playlist.value.id === 'favorites') return
  try {
    const key = await PlaylistService.SelectAndSetPlaylistArtwork(playlist.value.id)
    if (key) {
      load(true) // Silent reload to get new artwork and theme
    }
  } catch (e) {
    console.error('Failed to set playlist artwork', e)
  }
}

async function handleRemoveArtwork(e: MouseEvent) {
  e.stopPropagation()
  if (!playlist.value || playlist.value.id === 'favorites') return
  try {
    await PlaylistService.RemovePlaylistArtwork(playlist.value.id)
    load(true) // Silent reload
  } catch (e) {
    console.error('Failed to remove playlist artwork', e)
  }
}

function playPlaylist() {
  if (tracks.value.length > 0) {
    playerStore.playTracks(tracks.value, 0)
    playerStore.setShuffle(false)
  }
}

function shufflePlaylist() {
  if (tracks.value.length > 0) {
    playerStore.shuffleTracks(tracks.value)
  }
}
</script>

<template>
  <div class="h-full flex flex-col bg-background overflow-hidden">
    <div v-if="isLoading" class="flex-1 flex items-center justify-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
    </div>

    <div v-else-if="playlist" ref="scrollContainerRef" class="flex-1 overflow-y-auto" @scroll.passive="handleScroll">
      <DetailHero 
        :theme="playlistTheme" 
        :title="playlist.name"
      >
        <template #artwork>
          <div 
            @click="handleSetArtwork"
            class="w-48 h-48 rounded-lg shadow-2xl overflow-hidden ring-1 ring-foreground/[0.08] bg-foreground/5 flex-shrink-0 cursor-pointer group relative">
            
            <!-- Custom or Single Fallback -->
            <template v-if="playlistArtworks.length === 1 || (playlistArtworks.length > 1 && playlistArtworks.length < 4)">
              <img :src="'/artwork/' + playlistArtworks[0]" class="w-full h-full object-cover" />
            </template>
            
            <!-- 4-Grid Fallback -->
            <template v-else-if="playlistArtworks.length >= 4">
              <div class="grid grid-cols-2 grid-rows-2 w-full h-full">
                <img v-for="key in playlistArtworks.slice(0, 4)" :key="key" :src="'/artwork/' + key" class="w-full h-full object-cover" />
              </div>
            </template>

            <!-- Default Icon -->
            <div v-else class="w-full h-full flex items-center justify-center text-foreground/10">
              <Heart v-if="playlist.id === 'favorites'" class="w-24 h-24" />
              <ListMusic v-else class="w-24 h-24" />
            </div>

            <!-- Hover Overlay -->
            <div v-if="playlist.id !== 'favorites'" class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex flex-col items-center justify-center gap-2">
              <span class="text-white text-xs font-medium px-2 py-1 bg-black/20 rounded-full backdrop-blur-sm">Edit Cover</span>
              <button 
                v-if="playlist.artwork_key"
                @click="handleRemoveArtwork"
                class="p-1.5 bg-red-500/80 hover:bg-red-500 text-white rounded-full transition-colors backdrop-blur-sm"
                title="Remove custom cover"
              >
                <X class="w-4 h-4" />
              </button>
            </div>
          </div>
        </template>

        <template #metadata>
          <div class="flex gap-2 text-sm items-end flex-wrap">
            <div class="flex items-center gap-2">
              <Music class="w-4 h-4" />
              <span>{{ tracks.length }} {{ $t('library.songs') }}</span>
            </div>
            <div class="flex items-center gap-2">
              <Clock class="w-4 h-4" />
              <span>{{ totalDurationFormatted }}</span>
            </div>
          </div>
        </template>

        <template #actions>
          <DetailsButton :icon="Play" :label="$t('common.play')" @click="playPlaylist" />
          <div class="flex gap-2">
            <DetailsButton :icon="Shuffle" variant="outline" @click="shufflePlaylist" />
            <DetailsButton :icon="MoreVertical" variant="outline" @click="openContextMenu" />
          </div>
        </template>
      </DetailHero>

      <!-- Track List -->
      <div class="px-2 pb-12">
        <TrackTable 
          :tracks="tracks" 
          :show-artwork="true" 
          :show-album="true"
          @play-track="(_, index) => playerStore.playTracks(tracks, index)" 
        />
      </div>
    </div>

    <ContextMenu 
      :visible="contextMenu.visible.value" 
      :x="contextMenu.x.value" 
      :y="contextMenu.y.value"
      :items="contextMenu.items.value" 
      @close="contextMenu.close()" 
    />
  </div>
</template>
