<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Library, Plus, ListPlus, Sparkles, Upload } from '@lucide/vue'
import { IconButton } from '@airmedy/ui'
import { useRouter } from 'vue-router'
import PlaylistGrid from '../components/PlaylistGrid.vue'
import ViewHeader from '../components/ViewHeader.vue'
import CreatePlaylistDialog from '../components/CreatePlaylistDialog.vue'
import SmartPlaylistDialog from '../components/SmartPlaylistDialog.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import FilterDropdown from '../components/FilterDropdown.vue'
import { emptyConfig, type SmartPlaylistConfig } from '@/lib/smartPlaylistFields'
import { usePlaylistsStore } from '@/stores/playlists'
import type { Playlist, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { foldUnicode } from '@airmedy/utils'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const router = useRouter()
const playlistsStore = usePlaylistsStore()
const isLoading = ref(true)
const searchQuery = ref('')
const tracksByPlaylist = ref<Record<string, TrackDTO[]>>({})

const renameDialogOpen = ref(false)
const renamingId = ref('')
const renamingName = ref('')

const deleteConfirmOpen = ref(false)
const playlistToDelete = ref<Playlist | null>(null)

const createDialogOpen = ref(false)
const createSmartDialogOpen = ref(false)
const importDialogOpen = ref(false)
const importFilePath = ref('')
const importPlaylistName = ref('')
const isImporting = ref(false)
const newPlaylistDropdown = ref<InstanceType<typeof FilterDropdown> | null>(null)

const smartEditDialogOpen = ref(false)
const smartEditPlaylist = ref<Playlist | null>(null)
const sessionId = Math.random().toString(36).substring(2, 15)

const smartEditConfig = computed<SmartPlaylistConfig>(() => {
  if (!smartEditPlaylist.value?.rules) return emptyConfig()
  try {
    return JSON.parse(smartEditPlaylist.value.rules)
  } catch {
    return emptyConfig()
  }
})

const favoritesPlaylist = computed<Playlist>(() => ({
  id: 'favorites',
  name: t('sidebar.favorites'),
  description: '',
  artwork_key: null,
  pinned_at: null,
} as Playlist))

const processedPlaylists = computed(() => {
  const q = searchQuery.value ? foldUnicode(searchQuery.value) : ''

  let result = playlistsStore.playlists
  if (q) {
    result = result.filter((p) => foldUnicode(p.name || '').includes(q))
  }
  result = [...result].sort((a, b) => (a.name || '').localeCompare(b.name || ''))

  const showFavorites = !q || foldUnicode(favoritesPlaylist.value.name).includes(q)
  return showFavorites ? [favoritesPlaylist.value, ...result] : result
})

function openRenameDialog(playlist: Playlist) {
  renamingId.value = playlist.id
  renamingName.value = playlist.name
  renameDialogOpen.value = true
}

async function handleRename(name: string) {
  if (renamingId.value) await playlistsStore.rename(renamingId.value, name)
}

function openDeleteConfirm(playlist: Playlist) {
  playlistToDelete.value = playlist
  deleteConfirmOpen.value = true
}

async function handleDelete() {
  if (playlistToDelete.value) {
    await playlistsStore.deletePlaylist(playlistToDelete.value.id)
    playlistToDelete.value = null
  }
}

function openCreateDialog() {
  newPlaylistDropdown.value?.close()
  createDialogOpen.value = true
}

async function handleCreate(name: string) {
  const p = await playlistsStore.create(name)
  if (p) {
    router.push(`/playlists/${p.id}`)
    loadArtworkTracks()
  }
}

function openCreateSmartDialog() {
  newPlaylistDropdown.value?.close()
  createSmartDialogOpen.value = true
}

async function handleCreateSmart(payload: { name: string; description: string; config: SmartPlaylistConfig }) {
  const p = await playlistsStore.createSmart(payload.name, payload.description, payload.config)
  if (p) {
    router.push(`/playlists/${p.id}`)
    loadArtworkTracks()
  }
}

function openSmartEditDialog(playlist: Playlist) {
  smartEditPlaylist.value = playlist
  smartEditDialogOpen.value = true
}

async function handleSmartEdit(payload: { name: string; description: string; config: SmartPlaylistConfig }) {
  const p = smartEditPlaylist.value
  if (!p) return
  if (payload.name !== p.name) await playlistsStore.rename(p.id, payload.name)
  await playlistsStore.updateSmartRules(p.id, payload.config, sessionId)
}

async function handleImportClick() {
  newPlaylistDropdown.value?.close()
  try {
    const preview = await PlaylistService.SelectAndParseM3U8()
    if (!preview) return
    importFilePath.value = preview.file_path
    importPlaylistName.value = preview.playlist_name
    importDialogOpen.value = true
  } catch (e) {
    console.error('Failed to parse M3U8 file', e)
  }
}

async function handleImportConfirm(name: string) {
  if (!importFilePath.value || isImporting.value) return
  isImporting.value = true
  try {
    const result = await PlaylistService.ImportM3U8Playlist(importFilePath.value, name)
    if (result) {
      await playlistsStore.loadAll()
      router.push(`/playlists/${result.playlist_id}`)
      loadArtworkTracks()
    }
  } catch (e) {
    console.error('Failed to import playlist', e)
  } finally {
    isImporting.value = false
    importFilePath.value = ''
    importPlaylistName.value = ''
  }
}

async function loadArtworkTracks() {
  const map: Record<string, TrackDTO[]> = {}

  try {
    const favTracks = await LibraryService.GetFavoriteTracks()
    map.favorites = favTracks.filter((t): t is TrackDTO => t !== null)
  } catch {
    map.favorites = []
  }

  await Promise.all(
    playlistsStore.playlists
      .filter((p) => !p.artwork_key)
      .map(async (p) => {
        try {
          const tracks = await PlaylistService.GetPlaylistTracks(p.id)
          map[p.id] = tracks.filter((t): t is TrackDTO => t !== null)
        } catch {
          map[p.id] = []
        }
      })
  )

  tracksByPlaylist.value = map
}

onMounted(async () => {
  isLoading.value = true
  await playlistsStore.loadAll()
  isLoading.value = false
  loadArtworkTracks()
})
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden bg-background">
    <ViewHeader v-model="searchQuery" :title="$t('library.playlists')"
      :search-placeholder="`${$t('sidebar.search')} ${$t('library.playlists').toLowerCase()}...`">
      <template #actions>
        <FilterDropdown ref="newPlaylistDropdown" :panel-width="200" :panel-offset-y="4">
          <template #trigger="{ open }">
            <IconButton variant="outlined" :active="open" :title="t('sidebar.new_playlist')">
              <Plus class="w-3.5 h-3.5" />
            </IconButton>
          </template>

          <div class="flex flex-col gap-0.5">
            <div
              class="flex items-center gap-2.5 px-1.5 py-1.5 rounded-lg hover:bg-foreground/[0.06] cursor-pointer transition-colors"
              @click="openCreateDialog"
            >
              <ListPlus class="w-4 h-4 text-foreground/70" />
              <span class="text-sm text-foreground opacity-90">{{ t('sidebar.new_playlist') }}</span>
            </div>
            <div
              class="flex items-center gap-2.5 px-1.5 py-1.5 rounded-lg hover:bg-foreground/[0.06] cursor-pointer transition-colors"
              @click="openCreateSmartDialog"
            >
              <Sparkles class="w-4 h-4 text-foreground/70" />
              <span class="text-sm text-foreground opacity-90">{{ t('playlists.smart.new_smart_playlist') }}</span>
            </div>
            <div
              class="flex items-center gap-2.5 px-1.5 py-1.5 rounded-lg hover:bg-foreground/[0.06] cursor-pointer transition-colors"
              @click="handleImportClick"
            >
              <Upload class="w-4 h-4 text-foreground/70" />
              <span class="text-sm text-foreground opacity-90">{{ t('sidebar.import_playlist') }}</span>
            </div>
          </div>
        </FilterDropdown>
      </template>
    </ViewHeader>

    <div class="flex-1 overflow-hidden">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
      </div>

      <template v-else-if="processedPlaylists.length === 0">
        <div class="h-full flex flex-col items-center justify-center text-foreground opacity-60">
          <Library class="w-12 h-12 mb-4 opacity-20" />
          <p>{{ $t('library.no_playlists') }}</p>
        </div>
      </template>

      <PlaylistGrid v-else :playlists="processedPlaylists" :tracks-by-playlist="tracksByPlaylist" :gap="45"
        @rename="openRenameDialog" @delete="openDeleteConfirm" @edit-smart-rules="openSmartEditDialog" />
    </div>
  </div>

  <CreatePlaylistDialog v-model:open="renameDialogOpen" :initial-name="renamingName"
    :title="t('sidebar.rename_playlist_title')" @confirm="handleRename" />

  <CreatePlaylistDialog v-model:open="createDialogOpen" @confirm="handleCreate" />
  <SmartPlaylistDialog v-model:open="createSmartDialogOpen" @confirm="handleCreateSmart" />
  <SmartPlaylistDialog
    v-model:open="smartEditDialogOpen"
    :initial-name="smartEditPlaylist?.name ?? ''"
    :initial-description="smartEditPlaylist?.description ?? ''"
    :initial-config="smartEditConfig"
    :title="t('playlists.smart.edit_smart_playlist')"
    :confirm-label="t('common.save')"
    @confirm="handleSmartEdit"
  />
  <CreatePlaylistDialog
    v-model:open="importDialogOpen"
    :initial-name="importPlaylistName"
    :title="t('sidebar.import_playlist_title')"
    :confirm-label="t('sidebar.import')"
    @confirm="handleImportConfirm" />

  <ConfirmDialog
    v-model:open="deleteConfirmOpen"
    :title="t('sidebar.delete_playlist_title')"
    :message="t('sidebar.delete_playlist_message')"
    :confirm-label="t('sidebar.delete')"
    danger
    @confirm="handleDelete"
  />
</template>
