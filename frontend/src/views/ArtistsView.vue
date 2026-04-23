<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import { User } from 'lucide-vue-next'
import type { Artist, TrackDTO } from '../../bindings/changeme/internal/domain/models'
import EntityExplorerLayout from '../components/EntityExplorerLayout.vue'
import { usePlayerStore } from '@/stores/player'

const router = useRouter()
const route = useRoute()
const playerStore = usePlayerStore()
const artists = ref<Artist[]>([])
const isLoading = ref(true)

const loadArtists = async () => {
  isLoading.value = true
  try {
    const result = await LibraryService.GetAllArtists()
    artists.value = result
      .filter((a): a is Artist => a !== null)
      .sort((a, b) => (a.name || '').localeCompare(b.name || ''))
  } catch (err) {
    console.error('Failed to load artists:', err)
  } finally {
    isLoading.value = false
  }
}

const onSelect = (id: string) => {
  router.push(`/artists/${id}`)
}

const onPlay = async (artist: Artist) => {
  try {
    const tracks = await LibraryService.GetTracksByArtistID(artist.id)
    if (tracks && tracks.length > 0) {
      playerStore.playTracks(tracks.filter((t): t is TrackDTO => t !== null), 0)
    }
  } catch (err) {
    console.error('Failed to play artist:', err)
  }
}

onMounted(loadArtists)
</script>

<template>
  <EntityExplorerLayout
    title="Artists"
    :items="artists"
    :is-loading="isLoading"
    :selected-id="(route.params.id as string)"
    :icon="User"
    search-placeholder="Search artists..."
    @select="onSelect"
    @play="onPlay"
  >
    <router-view v-slot="{ Component }">
      <KeepAlive :max="5">
        <component :is="Component" :key="route.params.id" />
      </KeepAlive>
    </router-view>
  </EntityExplorerLayout>
</template>
