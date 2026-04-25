<script setup lang="ts">
import {
  Library,
  Plus,
  Music,
  Heart,
  MoreHorizontal,
  Pencil,
  Trash2,
} from 'lucide-vue-next'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePlaylistsStore } from '@/stores/playlists'
import CreatePlaylistDialog from './CreatePlaylistDialog.vue'
import { useI18n } from 'vue-i18n'
import { useContextMenu } from '@/composables/useContextMenu'
import ContextMenu from './ContextMenu.vue'
import SidebarItem from './SidebarItem.vue'
import type { Playlist } from '../../bindings/airmedy/internal/domain/models'

const { t } = useI18n()
const router = useRouter()
const playlistsStore = usePlaylistsStore()
const contextMenu = useContextMenu()

const createDialogOpen = ref(false)
const renameDialogOpen = ref(false)
const renamingId = ref('')
const renamingName = ref('')

function openCreateDialog() {
  createDialogOpen.value = true
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

async function deletePlaylist(id: string) {
  await playlistsStore.deletePlaylist(id)
}

function openPlaylistContextMenu(playlist: Playlist, e: MouseEvent) {
  contextMenu.open(e, [
    {
      label: t('sidebar.rename'),
      icon: Pencil,
      action: () => openRenameDialog(playlist.id, playlist.name),
    },
    {
      label: t('sidebar.delete'),
      icon: Trash2,
      danger: true,
      action: () => deletePlaylist(playlist.id),
    },
  ])
}
</script>

<template>
  <div class="flex-1 overflow-y-auto px-3 pb-2">
    <div class="sticky top-0 z-10 flex items-center justify-between px-3 py-2 bg-sidebar">
      <div class="flex items-center gap-2 text-foreground/60">
        <Library class="w-3.5 h-3.5" />
        <span class="text-xs font-semibold uppercase tracking-widest">{{ t('sidebar.playlists') }}</span>
      </div>
      <button
        class="w-6 h-6 flex items-center justify-center rounded text-foreground/60 hover:text-foreground hover:bg-foreground/[0.06] transition-colors"
        @click.stop="openCreateDialog" :title="t('sidebar.new_playlist')">
        <Plus class="w-3.5 h-3.5" />
      </button>
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
            class="w-6 h-6 flex items-center justify-center rounded text-foreground/0 group-hover:text-foreground/40 hover:!text-foreground hover:bg-foreground/[0.08] transition-colors opacity-0 group-hover:opacity-100"
            @click.stop="(e) => openPlaylistContextMenu(playlist, e)">
            <MoreHorizontal class="w-3.5 h-3.5" />
          </button>
        </template>
      </SidebarItem>

      <!-- Empty state -->
      <p v-if="playlistsStore.playlists.length === 0" class="px-3 py-2 text-xs text-foreground/30">
        {{ t('sidebar.no_playlists') }}
      </p>
    </div>
  </div>

  <!-- Dialogs -->
  <CreatePlaylistDialog v-model:open="createDialogOpen" @confirm="handleCreate" />
  <CreatePlaylistDialog v-model:open="renameDialogOpen" :initial-name="renamingName" :title="t('sidebar.rename_playlist_title')"
    @confirm="handleRename" />

  <ContextMenu
    :visible="contextMenu.visible.value"
    :x="contextMenu.x.value"
    :y="contextMenu.y.value"
    :items="contextMenu.items.value"
    @close="contextMenu.close()"
  />
</template>
