<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { UserCircle } from 'lucide-vue-next'
import type { Composer, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import EntityExplorerLayout from '../components/EntityExplorerLayout.vue'
import { usePlayerStore } from '@/stores/player'

const router = useRouter()
const route = useRoute()
const playerStore = usePlayerStore()
const composers = ref<Composer[]>([])
const isLoading = ref(true)

const loadComposers = async () => {
  isLoading.value = true
  try {
    const result = await LibraryService.GetAllComposers()
    composers.value = result
      .filter((c): c is Composer => c !== null)
      .sort((a, b) => (a.name || '').localeCompare(b.name || ''))
  } catch (err) {
    console.error('Failed to load composers:', err)
  } finally {
    isLoading.value = false
  }
}

const onSelect = (id: string) => {
  router.push(`/composers/${id}`)
}

const onPlay = async (composer: Composer) => {
  try {
    const tracks = await LibraryService.GetTracksByComposerID(composer.id)
    if (tracks && tracks.length > 0) {
      playerStore.playTracks(tracks.filter((t): t is TrackDTO => t !== null), 0)
    }
  } catch (err) {
    console.error('Failed to play composer:', err)
  }
}

onMounted(loadComposers)
</script>

<template>
  <EntityExplorerLayout
    title="Composers"
    :items="composers"
    :is-loading="isLoading"
    :selected-id="(route.params.id as string)"
    :icon="UserCircle"
    search-placeholder="Search composers..."
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
