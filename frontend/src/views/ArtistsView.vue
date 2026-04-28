<script setup lang="ts">
import { ref, shallowRef, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { User } from 'lucide-vue-next'
import type { Artist, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import EntityExplorerLayout from '../components/EntityExplorerLayout.vue'
import { usePlayerStore } from '@/stores/player'
import { useLibrarySync } from '@/composables/useLibrarySync'

const router = useRouter()
const route = useRoute()
const playerStore = usePlayerStore()
const artists = shallowRef<Artist[]>([])
const isLoading = ref(true)

useLibrarySync(() => { loadArtists() })

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
    :title="$t('library.artists')"
    :items="artists"
    :is-loading="isLoading"
    :selected-id="(route.params.id as string)"
    :icon="User"
    :search-placeholder="`${$t('sidebar.search')} ${$t('library.artists').toLowerCase()}...`"
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
