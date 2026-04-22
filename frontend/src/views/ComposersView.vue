<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import { UserCircle } from 'lucide-vue-next'
import type { Composer } from '../../bindings/changeme/internal/domain/models'
import EntityExplorerLayout from '../components/EntityExplorerLayout.vue'

const router = useRouter()
const route = useRoute()
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
  >
    <router-view v-slot="{ Component }">
      <component :is="Component" :key="route.params.id" />
    </router-view>
  </EntityExplorerLayout>
</template>
