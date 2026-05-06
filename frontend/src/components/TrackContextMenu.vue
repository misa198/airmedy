<script setup lang="ts">
import { ref } from 'vue'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { useContextMenu } from '@/composables/useContextMenu'
import { useTrackContextMenu, type TrackContextMenuOptions } from '@/composables/useTrackContextMenu'
import ContextMenu from './ContextMenu.vue'
import MetadataEditDialog from './MetadataEditDialog.vue'

const contextMenu = useContextMenu()
const editingTrack = ref<TrackDTO | null>(null)
const metadataOpen = ref(false)

const { buildMenuItems, buildMultiSelectMenuItems } = useTrackContextMenu((track) => {
  editingTrack.value = track
  metadataOpen.value = true
})

function open(e: MouseEvent, track: TrackDTO, options?: TrackContextMenuOptions) {
  contextMenu.open(e, buildMenuItems(track, options))
}

function openMulti(e: MouseEvent, tracks: TrackDTO[], options?: TrackContextMenuOptions) {
  contextMenu.open(e, buildMultiSelectMenuItems(tracks, options))
}

function close() {
  contextMenu.close()
}

defineExpose({ open, openMulti, close })
</script>

<template>
  <ContextMenu
    :visible="contextMenu.visible.value"
    :x="contextMenu.x.value"
    :y="contextMenu.y.value"
    :items="contextMenu.items.value"
    @close="contextMenu.close()"
  />
  <MetadataEditDialog v-model:open="metadataOpen" :track="editingTrack" />
</template>
