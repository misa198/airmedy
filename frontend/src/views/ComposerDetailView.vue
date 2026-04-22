<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import type { Composer, TrackDTO } from '../../bindings/changeme/internal/domain/models'
import GroupedAlbumList from '../components/GroupedAlbumList.vue'
import { UserCircle, Music, Play } from 'lucide-vue-next'

const route = useRoute()
const composer = ref<Composer | null>(null)
const tracks = ref<TrackDTO[]>([])
const isLoading = ref(true)

const loadComposerDetails = async (id: string) => {
  isLoading.value = true
  try {
    const [composerData, tracksData] = await Promise.all([
      LibraryService.GetComposerByID(id),
      LibraryService.GetTracksByComposerID(id)
    ])
    composer.value = composerData
    tracks.value = tracksData.filter((t): t is TrackDTO => t !== null)
  } catch (err) {
    console.error('Failed to load composer details:', err)
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  const id = route.params.id as string
  if (id) loadComposerDetails(id)
})

watch(() => route.params.id, (newId) => {
  if (newId) loadComposerDetails(newId as string)
})
</script>

<template>
  <div class="h-full flex flex-col bg-background overflow-hidden animate-in fade-in slide-in-from-right-4 duration-300">
    <div v-if="isLoading" class="flex-1 flex items-center justify-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
    </div>

    <div v-else-if="composer" class="flex-1 flex flex-col overflow-hidden">
      <!-- Composer Header -->
      <div class="p-8 border-b bg-gradient-to-b from-accent/20 to-background flex items-end gap-6 flex-shrink-0">
        <div class="w-24 h-24 rounded-2xl bg-primary/10 flex items-center justify-center border-2 border-primary/20 shadow-inner flex-shrink-0">
          <UserCircle class="w-12 h-12 text-primary" />
        </div>
        <div class="flex-1 space-y-2">
          <h1 class="text-4xl font-bold tracking-tight">{{ composer.name || 'Unknown Composer' }}</h1>
          <div class="flex items-center gap-4 text-muted-foreground">
            <span class="flex items-center gap-1"><Music class="w-4 h-4" /> {{ tracks.length }} compositions</span>
          </div>
          <div class="pt-2">
            <button class="px-6 py-2 bg-primary text-primary-foreground rounded-full font-bold shadow-lg hover:scale-105 transition-transform flex items-center gap-2">
              <Play class="w-4 h-4 fill-current" />
              Play All
            </button>
          </div>
        </div>
      </div>

      <!-- Grouped Albums -->
      <div class="flex-1 overflow-y-auto p-8">
        <GroupedAlbumList :tracks="tracks" />
      </div>
    </div>
  </div>
</template>
