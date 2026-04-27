<script setup lang="ts">
import { useRouter } from 'vue-router'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { AlbumDTO, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import VirtualizedGrid from './VirtualizedGrid.vue'
import AlbumCard from './AlbumCard.vue'
import { usePlayerStore } from '@/stores/player'
import { useContextMenu } from '@/composables/useContextMenu'
import { useAlbumContextMenu } from '@/composables/useAlbumContextMenu'
import ContextMenu from './ContextMenu.vue'

defineProps<{
  albums: AlbumDTO[]
  gap?: number
}>()

const router = useRouter()
const playerStore = usePlayerStore()
const contextMenu = useContextMenu()
const { buildMenuItems } = useAlbumContextMenu()

const onContextMenu = (e: MouseEvent, album: AlbumDTO) => {
  contextMenu.open(e, buildMenuItems(album))
}

const navigateToAlbum = (id: string) => {
  router.push(`/albums/${id}`)
}

const navigateToArtist = (id: string) => {
  if (id) router.push(`/artists/${id}`)
}

const playAlbum = async (id: string) => {
  try {
    const tracks = await LibraryService.GetTracksByAlbumID(id)
    if (tracks && tracks.length > 0) {
      playerStore.playTracks(tracks.filter((t): t is TrackDTO => t !== null), 0)
    }
  } catch (err) {
    console.error('Failed to play album:', err)
  }
}
</script>

<template>
  <VirtualizedGrid
    :items="albums"
    :square-items="true"
    :text-area-height="60"
    :min-column-width="180"
    :gap="gap ?? 45"
  >
    <template #default="{ item: album }">
      <AlbumCard
        :album="album"
        @click="navigateToAlbum"
        @artist-click="navigateToArtist"
        @play="playAlbum"
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
