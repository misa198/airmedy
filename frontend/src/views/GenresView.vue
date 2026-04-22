<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import { Tag } from 'lucide-vue-next'
import type { Genre } from '../../bindings/changeme/internal/domain/models'
import EntityExplorerLayout from '../components/EntityExplorerLayout.vue'

const router = useRouter()
const route = useRoute()
const genres = ref<Genre[]>([])
const isLoading = ref(true)

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

onMounted(loadGenres)
</script>

<template>
  <EntityExplorerLayout
    title="Genres"
    :items="genres"
    :is-loading="isLoading"
    :selected-id="(route.params.id as string)"
    :icon="Tag"
    search-placeholder="Search genres..."
    @select="onSelect"
  >
    <router-view v-slot="{ Component }">
      <component :is="Component" :key="route.params.id" />
    </router-view>
  </EntityExplorerLayout>
</template>
