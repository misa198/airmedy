<script setup lang="ts">
import { ref, shallowRef, onMounted, computed, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Play, Shuffle, MoreVertical, Clock, Music, X, Search } from 'lucide-vue-next'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { Playlist, TrackDTO, ThemeColors } from '../../bindings/airmedy/internal/domain/models'
import TrackTable from '@/components/TrackTable.vue'
import { usePlayerStore } from '@/stores/player'
import { useFavoritesStore } from '@/stores/favorites'
import { formatTotalDuration } from '@/lib/utils'
import DetailsButton from '@/components/ui/DetailsButton.vue'
import { useContextMenu } from '@/composables/useContextMenu'
import { usePlaylistContextMenu } from '@/composables/usePlaylistContextMenu'
import { useRestoreScroll } from '@/composables/useRestoreScroll'
import ContextMenu from '@/components/ContextMenu.vue'
import DetailHero from '@/components/DetailHero.vue'
import PlaylistArtwork from '@/components/PlaylistArtwork.vue'
import CreatePlaylistDialog from '@/components/CreatePlaylistDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { Input } from '@/components/ui/input'
import { useLibraryUpdates } from '@/composables/useLibraryUpdates'
import { usePlaylistsStore } from '@/stores/playlists'
import { Events } from '@wailsio/runtime'
import { foldUnicode } from '@/lib/utils'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const playerStore = usePlayerStore()
const favoritesStore = useFavoritesStore()
const playlistsStore = usePlaylistsStore()

const playlist = ref<Playlist | null>(null)
const tracks = shallowRef<TrackDTO[]>([])
const isLoading = ref(true)
const searchQuery = ref('')

const filteredTracks = computed(() => {
  if (!searchQuery.value) return tracks.value
  const q = foldUnicode(searchQuery.value)
  return tracks.value.filter(t => 
    foldUnicode(t.title || '').includes(q) || 
    foldUnicode(t.raw_artist_names || '').includes(q) ||
    foldUnicode(t.album?.title || '').includes(q)
  )
})

useLibraryUpdates(tracks)
const playlistTheme = ref<ThemeColors | null>(null)

const { scrollContainerRef, handleScroll } = useRestoreScroll()

const contextMenu = useContextMenu()
const { buildMenuItems: buildPlaylistMenuItems } = usePlaylistContextMenu()

const renameDialogOpen = ref(false)
const renamingName = ref('')
const deleteConfirmOpen = ref(false)

function openRenameDialog() {
  if (playlist.value) {
    renamingName.value = playlist.value.name
    renameDialogOpen.value = true
  }
}

async function handleRename(name: string) {
  if (playlist.value) {
    await playlistsStore.rename(playlist.value.id, name)
    playlist.value.name = name
  }
}

async function handleDelete() {
  if (playlist.value) {
    const id = playlist.value.id
    await playlistsStore.deletePlaylist(id)
    router.push('/')
  }
}

function openContextMenu(e: MouseEvent) {
  if (!playlist.value) return
  contextMenu.open(e, buildPlaylistMenuItems(playlist.value, {
    includePlayNext: true,
    includePlaylistMenu: false,
    includeExport: true,
    onRename: () => openRenameDialog(),
    onDelete: () => deleteConfirmOpen.value = true,
  }))
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
      const trackWithAlbum = tracks.value.find(t => t.album_id)
      if (trackWithAlbum?.album_id) {
        colors = await LibraryService.GetAlbumColors(trackWithAlbum.album_id)
      }
    }
    
    playlistTheme.value = colors
  } catch (e) {
    console.warn('Failed to load playlist theme', e)
  }
}

watch(tracks, () => loadTheme())
watch(() => route.params.id, () => load())
watch(() => favoritesStore.version, () => {
  if (route.params.id === 'favorites') load(true)
})

const sessionId = Math.random().toString(36).substring(2, 15)

const handlePlaylistChange = (ev: Events.WailsEvent) => {
  const data = ev.data as { playlist_id: string, sender_id: string }
  if (data.sender_id === sessionId) return
  if (data.playlist_id === route.params.id) {
    load(true)
  }
}

const handlePlaylistDeleted = (ev: Events.WailsEvent) => {
  const deletedId = ev.data as string
  if (deletedId === route.params.id) {
    router.push('/')
  }
}

let offPlaylistChange: (() => void) | null = null
let offPlaylistDeleted: (() => void) | null = null

onMounted(() => {
  load()
  offPlaylistChange = Events.On('playlist:tracks-changed', handlePlaylistChange)
  offPlaylistDeleted = Events.On('playlist:deleted', handlePlaylistDeleted)
})

onUnmounted(() => {
  offPlaylistChange?.()
  offPlaylistDeleted?.()
})

const totalDurationFormatted = computed(() => {
  const totalSeconds = tracks.value.reduce((acc, t) => acc + (t.duration || 0), 0)
  return formatTotalDuration(totalSeconds, t)
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
  if (filteredTracks.value.length > 0) {
    playerStore.playTracks(filteredTracks.value, 0)
  }
}

function shufflePlaylist() {
  if (filteredTracks.value.length > 0) {
    playerStore.shuffleTracks(filteredTracks.value)
  }
}

async function handleReorder(newTracks: TrackDTO[]) {
  if (!playlist.value || playlist.value.id === 'favorites') return
  
  const oldTracks = [...tracks.value]
  if (oldTracks.length !== newTracks.length) return

  // A more robust way to find the single moved item in a drag-and-drop:
  // The moved item is the one that, when removed from both lists, leaves identical lists.
  const movedItem = newTracks.find(t => {
    const oldWithout = oldTracks.filter(ot => ot.id !== t.id)
    const newWithout = newTracks.filter(nt => nt.id !== t.id)
    return oldWithout.every((ot, idx) => ot.id === newWithout[idx].id)
  })

  if (!movedItem) return

  const movedTrackId = movedItem.id
  const newIdx = newTracks.findIndex(t => t.id === movedTrackId)
  const prevTrackId = newIdx > 0 ? newTracks[newIdx - 1].id : ''
  const nextTrackId = newIdx < newTracks.length - 1 ? newTracks[newIdx + 1].id : ''

  // Optimistic update
  tracks.value = newTracks

  try {
    // @ts-ignore
    if (PlaylistService.MoveTrack) {
      // @ts-ignore
      await PlaylistService.MoveTrack(playlist.value.id, movedTrackId, prevTrackId, nextTrackId, sessionId)
    } else {
      console.warn('PlaylistService.MoveTrack not found in bindings. Please regenerate bindings.')
    }
  } catch (e) {
    console.error('Failed to update playlist track order', e)
    load(true) // Revert on failure
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
        <template #top-right>
          <div class="relative max-w-sm w-full">
            <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground opacity-60" />
            <Input 
              v-model="searchQuery"
              type="text"
              :placeholder="$t('sidebar.search')"
              class="pl-10 pr-4"
            />
          </div>
        </template>
        <template #artwork>
          <div 
            @click="handleSetArtwork"
            class="w-48 h-48 rounded-lg shadow-2xl overflow-hidden ring-1 ring-foreground/[0.08] bg-foreground/5 flex-shrink-0 cursor-pointer group relative">
            
            <PlaylistArtwork :playlist="playlist" :tracks="tracks">
              <!-- Hover Overlay -->
              <div v-if="playlist.id !== 'favorites'" class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex flex-col items-center justify-center gap-2">
                <span class="text-white text-xs font-medium px-2 py-1 bg-black/20 rounded-full backdrop-blur-sm">{{ $t('playlist.edit_cover') }}</span>
                <button 
                  v-if="playlist.artwork_key"
                  @click="handleRemoveArtwork"
                  class="p-1.5 bg-red-500/80 hover:bg-red-500 text-white rounded-full transition-colors backdrop-blur-sm"
                  :title="$t('playlist.remove_cover')"
                >
                  <X class="w-4 h-4" />
                </button>
              </div>
            </PlaylistArtwork>
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
      <div class="top-0 h-[calc(100vh-390px)]">
        <TrackTable
          :tracks="filteredTracks"
          :show-artwork="true"
          :simple-mode="true"
          :allow-dnd="playlist.id !== 'favorites'"
          :context-menu-options="{ playlistId: playlist.id }"
          @play-track="(_, index, queue) => playerStore.playTracks(queue, index)"
          @reorder="handleReorder"
          @navigate-album="id => router.push(`/albums/${id}`)"
          @navigate-artist="id => router.push(`/artists/${id}`)"
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

    <CreatePlaylistDialog v-model:open="renameDialogOpen" :initial-name="renamingName" :title="t('sidebar.rename_playlist_title')"
      @confirm="handleRename" />

    <ConfirmDialog
      v-model:open="deleteConfirmOpen"
      :title="t('sidebar.delete_playlist_title')"
      :message="t('sidebar.delete_playlist_message')"
      :confirm-label="t('sidebar.delete')"
      danger
      @confirm="handleDelete"
    />
  </div>
</template>
