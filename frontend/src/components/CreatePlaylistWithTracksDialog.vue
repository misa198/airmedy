<script setup lang="ts">
import CreatePlaylistDialog from './CreatePlaylistDialog.vue'
import { useCreatePlaylistWithTracks } from '@/composables/useCreatePlaylistWithTracks'

const createPlaylistWithTracks = useCreatePlaylistWithTracks()

async function handleConfirm(name: string) {
  await createPlaylistWithTracks.create(name)
}
</script>

<template>
  <CreatePlaylistDialog
    :open="createPlaylistWithTracks.isVisible.value"
    @update:open="(open) => !open && createPlaylistWithTracks.close()"
    @confirm="handleConfirm"
  />
</template>
