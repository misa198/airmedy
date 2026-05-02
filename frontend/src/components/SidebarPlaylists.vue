<script setup lang="ts">
import {
  Library,
  Plus,
  Upload,
  Music,
  Heart,
  MoreHorizontal,
} from 'lucide-vue-next'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePlaylistsStore } from '@/stores/playlists'
import CreatePlaylistDialog from './CreatePlaylistDialog.vue'
import ConfirmDialog from './ConfirmDialog.vue'
import { useI18n } from 'vue-i18n'
import { useContextMenu } from '@/composables/useContextMenu'
import { usePlaylistContextMenu } from '@/composables/usePlaylistContextMenu'
import ContextMenu from './ContextMenu.vue'
import SidebarItem from './SidebarItem.vue'
import type { Playlist } from '../../bindings/airmedy/internal/domain/models'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'

const { t } = useI18n()
const router = useRouter()
const playlistsStore = usePlaylistsStore()
const contextMenu = useContextMenu()
const { buildMenuItems: buildPlaylistMenuItems } = usePlaylistContextMenu()

const createDialogOpen = ref(false)
const renameDialogOpen = ref(false)
const deleteConfirmOpen = ref(false)
const playlistToDelete = ref<Playlist | null>(null)
const renamingId = ref('')
const renamingName = ref('')

const importDialogOpen = ref(false)
const importFilePath = ref('')
const importPlaylistName = ref('')
const isImporting = ref(false)

function openCreateDialog() {
  createDialogOpen.value = true
}

async function handleImportClick() {
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
    }
  } catch (e) {
    console.error('Failed to import playlist', e)
  } finally {
    isImporting.value = false
    importFilePath.value = ''
    importPlaylistName.value = ''
  }
}

async function handleCreate(name: string) {
  const p = await playlistsStore.create(name)
  if (p) router.push(`/playlists/${p.id}`)
}

function openRenameDialog(id: string, name: string) {
  renamingId.value = id
  renamingName.value = name
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

function openPlaylistContextMenu(playlist: Playlist, e: MouseEvent) {
  contextMenu.open(e, buildPlaylistMenuItems(playlist, {
    onRename: (p) => openRenameDialog(p.id, p.name),
    onDelete: (p) => openDeleteConfirm(p),
  }))
}
</script>

<template>
  <div class="flex-1 overflow-y-auto px-3 pb-2">
    <div class="sticky top-0 z-10 flex items-center justify-between px-3 py-2 bg-sidebar">
      <div class="flex items-center gap-2 text-foreground opacity-80">
        <Library class="w-3.5 h-3.5" />
        <span class="text-xs font-semibold uppercase tracking-widest">{{ t('sidebar.playlists') }}</span>
      </div>
      <div class="flex items-center gap-1">
        <button
          class="w-6 h-6 flex items-center justify-center rounded text-foreground opacity-80 hover:text-foreground hover:bg-foreground/[0.06] transition-colors"
          @click.stop="handleImportClick" :title="t('sidebar.import_playlist')">
          <Upload class="w-3.5 h-3.5" />
        </button>
        <button
          class="w-6 h-6 flex items-center justify-center rounded text-foreground opacity-80 hover:text-foreground hover:bg-foreground/[0.06] transition-colors"
          @click.stop="openCreateDialog" :title="t('sidebar.new_playlist')">
          <Plus class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>

    <!-- Playlist list -->
    <div class="space-y-0.5">
      <SidebarItem
        to="/playlists/favorites"
        :icon="Heart"
        :label="t('sidebar.favorites')"
      />

      <SidebarItem
        v-for="playlist in playlistsStore.playlists"
        :key="playlist.id"
        :to="`/playlists/${playlist.id}`"
        :icon="Music"
        :label="playlist.name"
        @contextmenu="openPlaylistContextMenu(playlist, $event)"
      >
        <template #actions>
          <button
            class="w-6 h-6 flex items-center justify-center rounded text-foreground opacity-0 group-hover:text-foreground opacity-60 hover:!text-foreground hover:bg-foreground/[0.08] transition-colors opacity-0 group-hover:opacity-100"
            @click.stop="(e) => openPlaylistContextMenu(playlist, e)">
            <MoreHorizontal class="w-3.5 h-3.5" />
          </button>
        </template>
      </SidebarItem>
    </div>
  </div>

  <!-- Dialogs -->
  <CreatePlaylistDialog v-model:open="createDialogOpen" @confirm="handleCreate" />
  <CreatePlaylistDialog v-model:open="renameDialogOpen" :initial-name="renamingName" :title="t('sidebar.rename_playlist_title')"
    @confirm="handleRename" />
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

  <ContextMenu
    :visible="contextMenu.visible.value"
    :x="contextMenu.x.value"
    :y="contextMenu.y.value"
    :items="contextMenu.items.value"
    @close="contextMenu.close()"
  />
</template>
