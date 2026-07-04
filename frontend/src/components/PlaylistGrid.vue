<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { Playlist, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import VirtualizedGrid from './VirtualizedGrid.vue'
import PlaylistCard from './PlaylistCard.vue'
import { useContextMenu } from '@/composables/useContextMenu'
import { usePlaylistContextMenu } from '@/composables/usePlaylistContextMenu'
import ContextMenu from './ContextMenu.vue'

defineProps<{
  playlists: Playlist[]
  tracksByPlaylist?: Record<string, TrackDTO[]>
  gap?: number
}>()

const emit = defineEmits<{
  'rename': [playlist: Playlist]
  'delete': [playlist: Playlist]
}>()

const router = useRouter()
const contextMenu = useContextMenu()
const { buildMenuItems } = usePlaylistContextMenu()

const onContextMenu = (e: MouseEvent, playlist: Playlist) => {
  contextMenu.open(e, buildMenuItems(playlist, {
    onRename: (p) => emit('rename', p),
    onDelete: (p) => emit('delete', p),
  }))
}

const navigateToPlaylist = (id: string) => router.push(`/playlists/${id}`)
</script>

<template>
  <VirtualizedGrid
    :items="playlists"
    :square-items="true"
    :text-area-height="50"
    :min-column-width="180"
    :gap="gap ?? 45"
  >
    <template #default="{ item: playlist }">
      <PlaylistCard
        :playlist="playlist"
        :tracks="tracksByPlaylist?.[playlist.id]"
        @click="navigateToPlaylist"
        @contextmenu="onContextMenu"
      />
    </template>
  </VirtualizedGrid>

  <ContextMenu
    :visible="contextMenu.visible.value"
    :x="contextMenu.x.value"
    :y="contextMenu.y.value"
    :items="contextMenu.items.value"
    @close="contextMenu.close()"
  />
</template>
