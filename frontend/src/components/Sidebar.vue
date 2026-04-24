<script setup lang="ts">
import {
  Home,
  Clock,
  Users,
  Disc,
  Music,
  ListMusic,
  PenTool,
  Search,
  Settings,
  Plus,
  Library,
  MoreHorizontal,
  Pencil,
  Trash2,
  Heart,
} from 'lucide-vue-next'
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { usePlaylistsStore } from '@/stores/playlists'
import CreatePlaylistDialog from './CreatePlaylistDialog.vue'

const router = useRouter()
const playlistsStore = usePlaylistsStore()

const navItems = [
  { name: 'Home', icon: Home, to: '/' },
  { name: 'Recently Added', icon: Clock, to: '/recently-added' },
  { name: 'Artists', icon: Users, to: '/artists' },
  { name: 'Albums', icon: Disc, to: '/albums' },
  { name: 'Tracks', icon: Music, to: '/tracks' },
  { name: 'Favorites', icon: Heart, to: '/favorites' },
  { name: 'Genres', icon: ListMusic, to: '/genres' },
  { name: 'Composers', icon: PenTool, to: '/composers' },
  { name: 'Search', icon: Search, to: '/search' },
  { name: 'Settings', icon: Settings, to: '/settings' },
]

const createDialogOpen = ref(false)
const renameDialogOpen = ref(false)
const renamingId = ref('')
const renamingName = ref('')
const contextMenuId = ref<string | null>(null)

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
  contextMenuId.value = null
}

async function handleRename(name: string) {
  if (renamingId.value) await playlistsStore.rename(renamingId.value, name)
}

async function deletePlaylist(id: string) {
  await playlistsStore.deletePlaylist(id)
  contextMenuId.value = null
}

function toggleContextMenu(id: string, e: MouseEvent) {
  e.preventDefault()
  e.stopPropagation()
  contextMenuId.value = contextMenuId.value === id ? null : id
}
</script>

<template>
  <div class="flex flex-col h-full bg-background w-full" @click="contextMenuId = null">
    <!-- Main nav -->
    <nav class="px-3 py-2 space-y-0.5">
      <RouterLink v-for="item in navItems" :key="item.name" :to="item.to"
        class="flex items-center gap-3 px-3 py-2 rounded-lg transition-colors text-white/50 hover:text-white hover:bg-white/[0.05]"
        active-class="bg-white/[0.08] !text-primary font-medium">
        <component :is="item.icon" class="w-4 h-4 flex-shrink-0" />
        <span class="text-sm">{{ item.name }}</span>
      </RouterLink>
    </nav>

    <!-- Divider -->
    <div class="mx-3 border-t border-white/[0.06] my-1" />

    <!-- Playlists section -->
    <div class="flex-1 overflow-y-auto px-3 pb-2">
      <div class="flex items-center justify-between px-3 py-2">
        <div class="flex items-center gap-2 text-white/30">
          <Library class="w-3.5 h-3.5" />
          <span class="text-xs font-semibold uppercase tracking-widest">Playlists</span>
        </div>
        <button
          class="w-6 h-6 flex items-center justify-center rounded text-white/30 hover:text-white hover:bg-white/[0.06] transition-colors"
          @click.stop="openCreateDialog" title="New playlist">
          <Plus class="w-3.5 h-3.5" />
        </button>
      </div>

      <!-- Playlist list -->
      <div class="space-y-0.5">
        <div v-for="playlist in playlistsStore.playlists" :key="playlist.id" class="relative group">
          <RouterLink :to="`/playlists/${playlist.id}`"
            class="flex items-center gap-3 px-3 py-2 rounded-lg transition-colors text-white/50 hover:text-white hover:bg-white/[0.05] pr-8"
            active-class="bg-white/[0.08] text-white font-medium">
            <Music class="w-4 h-4 flex-shrink-0" />
            <span class="text-sm truncate">{{ playlist.name }}</span>
          </RouterLink>

          <!-- Context menu trigger -->
          <button
            class="absolute right-2 top-1/2 -translate-y-1/2 w-6 h-6 flex items-center justify-center rounded text-white/0 group-hover:text-white/40 hover:!text-white hover:bg-white/[0.08] transition-colors opacity-0 group-hover:opacity-100"
            @click.stop="(e) => toggleContextMenu(playlist.id, e)">
            <MoreHorizontal class="w-3.5 h-3.5" />
          </button>

          <!-- Context menu -->
          <div v-if="contextMenuId === playlist.id"
            class="absolute right-0 top-full mt-1 z-50 w-40 rounded-lg bg-[#1A1A1A] ring-1 ring-white/[0.08] shadow-xl py-1"
            @click.stop>
            <button
              class="flex items-center gap-2.5 w-full px-3 py-2 text-sm text-white/70 hover:text-white hover:bg-white/[0.06] transition-colors"
              @click="openRenameDialog(playlist.id, playlist.name)">
              <Pencil class="w-3.5 h-3.5" />Rename
            </button>
            <button
              class="flex items-center gap-2.5 w-full px-3 py-2 text-sm text-red-400/80 hover:text-red-400 hover:bg-white/[0.06] transition-colors"
              @click="deletePlaylist(playlist.id)">
              <Trash2 class="w-3.5 h-3.5" />Delete
            </button>
          </div>
        </div>

        <!-- Empty state -->
        <p v-if="playlistsStore.playlists.length === 0" class="px-3 py-2 text-xs text-white/20">
          No playlists yet
        </p>
      </div>
    </div>

    <!-- Dialogs -->
    <CreatePlaylistDialog v-model:open="createDialogOpen" @confirm="handleCreate" />
    <CreatePlaylistDialog v-model:open="renameDialogOpen" :initial-name="renamingName" title="Rename Playlist"
      @confirm="handleRename" />
  </div>
</template>
