<script setup lang="ts">
import { ref, shallowRef, onMounted, computed, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Play, Shuffle, MoreVertical, Clock, Music, X, Search, Sparkles } from '@lucide/vue'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { Playlist, TrackDTO, ThemeColors } from '../../bindings/airmedy/internal/domain/models'
import TrackTable from '@/components/TrackTable.vue'
import { usePlayerStore } from '@/stores/player'
import { useFavoritesStore } from '@/stores/favorites'
import { formatTotalDuration, foldUnicode } from '@airmedy/utils'
import { DetailsButton } from '@airmedy/ui'
import { useContextMenu } from '@/composables/useContextMenu'
import { usePlaylistContextMenu } from '@/composables/usePlaylistContextMenu'
import ContextMenu from '@/components/ContextMenu.vue'
import DetailPageLayout from '@/components/DetailPageLayout.vue'
import PlaylistArtwork from '@/components/PlaylistArtwork.vue'
import CreatePlaylistDialog from '@/components/CreatePlaylistDialog.vue'
import SmartPlaylistDialog from '@/components/SmartPlaylistDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { emptyConfig, type SmartPlaylistConfig } from '@/lib/smartPlaylistFields'
import { Input } from '@airmedy/ui'
import { useLibraryUpdates } from '@/composables/useLibraryUpdates'
import { useDetailRouteLoader } from '@/composables/useDetailRouteLoader'
import { usePlaylistsStore } from '@/stores/playlists'
import { Events } from '@wailsio/runtime'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const playerStore = usePlayerStore()
const favoritesStore = useFavoritesStore()
const playlistsStore = usePlaylistsStore()

const playlist = ref<Playlist | null>(null)
const tracks = shallowRef<TrackDTO[]>([])
const isLoading = ref(true)
const tracksLoading = ref(false)
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

const contextMenu = useContextMenu()
const { buildMenuItems: buildPlaylistMenuItems } = usePlaylistContextMenu()

const renameDialogOpen = ref(false)
const renamingName = ref('')
const smartEditDialogOpen = ref(false)
const deleteConfirmOpen = ref(false)

function openRenameDialog() {
  if (!playlist.value) return
  renamingName.value = playlist.value.name
  renameDialogOpen.value = true
}

async function handleRename(name: string) {
  if (playlist.value) {
    await playlistsStore.rename(playlist.value.id, name)
    playlist.value.name = name
  }
}

const smartEditConfig = computed<SmartPlaylistConfig>(() => {
  if (!playlist.value?.rules) return emptyConfig()
  try {
    return JSON.parse(playlist.value.rules)
  } catch {
    return emptyConfig()
  }
})

async function handleSmartEdit(payload: { name: string; description: string; config: SmartPlaylistConfig }) {
  if (!playlist.value) return
  const id = playlist.value.id
  if (payload.name !== playlist.value.name) {
    await playlistsStore.rename(id, payload.name)
    playlist.value.name = payload.name
  }
  await playlistsStore.updateSmartRules(id, payload.config, sessionId)
  load(true)
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
    includePlaylistMenu: false,
    includeExport: true,
    onRename: () => openRenameDialog(),
    onEditSmartRules: () => smartEditDialogOpen.value = true,
    onDelete: () => deleteConfirmOpen.value = true,
  }))
}

// Synchronous token marking the most recently requested playlist, set before
// any await so a fast-resolving load can't be mistaken for stale (comparing
// against vue-router's reactive route.params.id instead is racy: it may not
// have finished updating yet by the time a quick fetch resolves).
let currentPlaylistId: string | null = null
const isStale = (id: string) => currentPlaylistId !== id

async function load(silent = false, idOverride?: string) {
  const id = idOverride ?? (route.params.id as string)
  if (!id) return
  currentPlaylistId = id

  // Switching to a different playlist: drop the old track list so a
  // track-derived artwork mosaic (no custom artwork_key) doesn't flash the
  // previous playlist's covers while the new tracks are still loading.
  if (playlist.value?.id !== id) {
    tracks.value = []
    playlistTheme.value = null
  }

  if (!silent) isLoading.value = true

  // Favorites has a real playlist row (for artwork/theme) but its track list
  // stays virtual, derived from Track.IsFavorite rather than playlist_tracks.
  if (id === 'favorites') {
    try {
      const [p, result] = await Promise.all([
        PlaylistService.GetPlaylistByID(id),
        LibraryService.GetFavoriteTracks(),
      ])
      if (isStale(id)) return
      playlist.value = p ?? ({ id: 'favorites', name: t('sidebar.favorites'), description: '', artwork_key: null } as Playlist)
      if (playlist.value.name === 'Favorites') {
        playlist.value = { ...playlist.value, name: t('sidebar.favorites') }
      }
      tracks.value = result.filter((t): t is TrackDTO => t !== null)
      await loadTheme()
    } catch (e) {
      console.error('Failed to load favorite tracks', e)
    } finally {
      if (!silent && !isStale(id)) isLoading.value = false
    }
    return
  }

  try {
    const p = await PlaylistService.GetPlaylistByID(id)
    if (isStale(id)) return
    playlist.value = p
  } catch (e) {
    console.error('Failed to load playlist', e)
  } finally {
    if (!silent && !isStale(id)) isLoading.value = false
  }

  if (isStale(id)) return

  tracksLoading.value = true
  try {
    const t = await PlaylistService.GetPlaylistTracks(id)
    if (isStale(id)) return
    tracks.value = t.filter((t): t is TrackDTO => t !== null)
  } catch (e) {
    console.error('Failed to load playlist tracks', e)
  } finally {
    if (!isStale(id)) tracksLoading.value = false
  }
}

async function loadTheme() {
  if (!playlist.value) return
  const id = playlist.value.id

  try {
    // 1. Try playlist custom theme
    let colors: ThemeColors | null = await PlaylistService.GetPlaylistColors(id)

    // 2. Fallback to first track's album theme if no custom artwork
    if (!colors && tracks.value.length > 0) {
      const trackWithAlbum = tracks.value.find(t => t.album_id)
      if (trackWithAlbum?.album_id) {
        colors = await LibraryService.GetAlbumColors(trackWithAlbum.album_id)
      }
    }

    if (playlist.value?.id !== id) return
    playlistTheme.value = colors
  } catch (e) {
    console.warn('Failed to load playlist theme', e)
  }
}

watch(tracks, () => loadTheme())
useDetailRouteLoader((id) => load(false, id))
watch(() => favoritesStore.version, () => {
  if (playlist.value?.id === 'favorites') load(true, 'favorites')
})

const sessionId = Math.random().toString(36).substring(2, 15)

const handlePlaylistChange = (ev: Events.WailsEvent) => {
  const data = ev.data as { playlist_id: string, sender_id: string }
  if (data.sender_id === sessionId) return
  // This listener stays registered while KeepAlive backgrounds this instance
  // on an unrelated route, so `id` must come from our own state, not the
  // (possibly unrelated) global route.params.id.
  if (data.playlist_id === playlist.value?.id) {
    load(true, playlist.value.id)
  }
}

const handlePlaylistDeleted = (ev: Events.WailsEvent) => {
  const deletedId = ev.data as string
  if (deletedId === playlist.value?.id) {
    router.push('/')
  }
}

let offPlaylistChange: (() => void) | null = null
let offPlaylistDeleted: (() => void) | null = null

onMounted(() => {
  offPlaylistChange = Events.On('playlist:tracks-changed', handlePlaylistChange)
  offPlaylistDeleted = Events.On('playlist:deleted', handlePlaylistDeleted)
})

onUnmounted(() => {
  offPlaylistChange?.()
  offPlaylistDeleted?.()
})

const totalDurationFormatted = computed(() => {
  if (tracksLoading.value) return '--'
  const totalSeconds = tracks.value.reduce((acc, t) => acc + (t.duration || 0), 0)
  return formatTotalDuration(totalSeconds, t)
})

async function handleSetArtwork() {
  if (!playlist.value) return
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
  if (!playlist.value) return
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
  <DetailPageLayout
    v-if="playlist"
    :loading="isLoading"
    :theme="playlistTheme"
    :title="playlist.name"
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
      <div
        @click="handleSetArtwork"
        class="w-48 h-48 rounded-lg shadow-2xl overflow-hidden ring-1 ring-foreground/[0.08] bg-foreground/5 flex-shrink-0 cursor-pointer group relative">

        <PlaylistArtwork :playlist="playlist" :tracks="tracks">
          <!-- Hover Overlay -->
          <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex flex-col items-center justify-center gap-2">
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
          <span>{{ tracksLoading ? '--' : tracks.length }} {{ $t('library.songs') }}</span>
        </div>
        <div class="flex items-center gap-2">
          <Clock class="w-4 h-4" />
          <span>{{ totalDurationFormatted }}</span>
        </div>
      </div>
    </template>

    <template #actions>
      <DetailsButton :icon="Play" :label="$t('common.play')" :filled-icon="true" @click="playPlaylist" />
      <div class="flex gap-2">
        <DetailsButton :icon="Shuffle" variant="outline" @click="shufflePlaylist" />
        <DetailsButton
          v-if="playlist.is_smart"
          :icon="Sparkles"
          variant="outline"
          :title="t('context_menu.edit_rules')"
          @click="smartEditDialogOpen = true"
        />
        <DetailsButton :icon="MoreVertical" variant="outline" @click="openContextMenu" />
      </div>
    </template>

    <template #body>
      <TrackTable
        :tracks="filteredTracks"
        :is-loading="tracksLoading"
        :show-artwork="true"
        :simple-mode="true"
        :allow-dnd="playlist.id !== 'favorites' && !playlist.is_smart"
        :context-menu-options="{ playlistId: playlist.id, isSmartPlaylist: playlist.is_smart }"
        @play-track="(_, index, queue) => playerStore.playTracks(queue, index)"
        @reorder="handleReorder"
        @navigate-album="id => router.push(`/albums/${id}`)"
        @navigate-artist="id => router.push(`/artists/${id}`)"
      />
    </template>
  </DetailPageLayout>
  <div v-else-if="isLoading" class="h-full flex items-center justify-center">
    <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
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

  <SmartPlaylistDialog
    v-if="playlist"
    v-model:open="smartEditDialogOpen"
    :initial-name="playlist.name"
    :initial-description="playlist.description"
    :initial-config="smartEditConfig"
    :title="t('playlists.smart.edit_smart_playlist')"
    :confirm-label="t('common.save')"
    @confirm="handleSmartEdit"
  />

  <ConfirmDialog
    v-model:open="deleteConfirmOpen"
    :title="t('sidebar.delete_playlist_title')"
    :message="t('sidebar.delete_playlist_message')"
    :confirm-label="t('sidebar.delete')"
    danger
    @confirm="handleDelete"
  />
</template>
