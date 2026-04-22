<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import { User } from 'lucide-vue-next'
import type { Artist } from '../../bindings/changeme/internal/domain/models'
import EntityExplorerLayout from '../components/EntityExplorerLayout.vue'

const router = useRouter()
const route = useRoute()
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
  >
    <router-view v-slot="{ Component }">
      <component :is="Component" :key="route.params.id" />
    </router-view>
  </EntityExplorerLayout>
</template>
