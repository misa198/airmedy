<script setup lang="ts">
import { ref, shallowRef, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { Tag } from 'lucide-vue-next'
import type { Genre, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import EntityExplorerLayout from '../components/EntityExplorerLayout.vue'
import { usePlayerStore } from '@/stores/player'
import { useLibrarySync } from '@/composables/useLibrarySync'

const router = useRouter()
const route = useRoute()
const playerStore = usePlayerStore()
const genres = shallowRef<Genre[]>([])
const isLoading = ref(true)

useLibrarySync(() => { loadGenres() })

const loadGenres = async () => {
  isLoading.value = true
  try {
    const result = await LibraryService.GetAllGenres()
    genres.value = result
      .filter((g): g is Genre => g !== null)
      .sort((a, b) => (a.name || '').localeCompare(b.name || ''))
  } catch (err) {
    console.error('Failed to load genres:', err)
  } finally {
    isLoading.value = false
  }
}

const onSelect = (id: string) => {
  router.push(`/genres/${id}`)
}

const onPlay = async (genre: Genre) => {
  try {
    const tracks = await LibraryService.GetTracksByGenreID(genre.id)
    if (tracks && tracks.length > 0) {
      playerStore.playTracks(tracks.filter((t): t is TrackDTO => t !== null), 0)
    }
  } catch (err) {
    console.error('Failed to play genre:', err)
  }
}

onMounted(loadGenres)
</script>

<template>
  <EntityExplorerLayout
    :title="$t('library.genres')"
    :items="genres"
    :is-loading="isLoading"
    :selected-id="(route.params.id as string)"
    :icon="Tag"
    :search-placeholder="`${$t('sidebar.search')} ${$t('library.genres').toLowerCase()}...`"
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
