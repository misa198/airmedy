<script setup lang="ts">
import { ref, shallowRef, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { Composer, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import GroupedAlbumList from '../components/GroupedAlbumList.vue'
import { UserCircle, Music, Shuffle, Play, MoreVertical } from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { useI18n } from 'vue-i18n'
import { useContextMenu } from '@/composables/useContextMenu'
import { useGroupContextMenu } from '@/composables/useGroupContextMenu'
import ContextMenu from '../components/ContextMenu.vue'
import DetailsButton from '@/components/ui/DetailsButton.vue'
import { sortTracksGrouped } from '@/lib/trackSort'

const { t } = useI18n()

const route = useRoute()
const playerStore = usePlayerStore()
const composer = ref<Composer | null>(null)
const tracks = shallowRef<TrackDTO[]>([])
const isLoading = ref(true)

const contextMenu = useContextMenu()
const { buildMenuItems } = useGroupContextMenu()
const sortedTracks = computed(() => sortTracksGrouped(tracks.value))

function openContextMenu(e: MouseEvent) {
  contextMenu.open(e, buildMenuItems(tracks.value))
}

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
      <div class="p-8 border-b border-foreground/[0.06] bg-gradient-to-b from-dynamic-surface to-transparent flex items-end gap-6 flex-shrink-0">
        <div class="w-24 h-24 rounded-2xl bg-foreground/5 flex items-center justify-center ring-1 ring-foreground/[0.08] flex-shrink-0">
          <UserCircle class="w-12 h-12 text-foreground opacity-70" />
        </div>
        <div class="flex-1 space-y-2">
          <h1 class="text-4xl font-bold tracking-tight">{{ composer.name || t('library.unknown_composer') }}</h1>
          <div class="flex items-center gap-4 text-foreground opacity-60">
            <span class="flex items-center gap-1"><Music class="w-4 h-4" /> {{ t('composer.compositions_count', { count: tracks.length }) }}</span>
          </div>
          <div class="pt-2 flex items-center gap-4">
            <DetailsButton :icon="Play" :label="t('common.play')" @click="playerStore.playTracks(sortedTracks, 0)" />
            <div class="flex gap-2">
              <DetailsButton :icon="Shuffle" variant="outline" @click="playerStore.shuffleTracks(tracks)" />
              <DetailsButton :icon="MoreVertical" variant="outline" @click="openContextMenu" />
            </div>
          </div>
        </div>
      </div>

      <!-- Grouped Albums -->
      <div class="flex-1 overflow-y-auto p-8">
        <GroupedAlbumList :tracks="tracks" />
      </div>
    </div>

    <ContextMenu
      :visible="contextMenu.visible.value"
      :x="contextMenu.x.value"
      :y="contextMenu.y.value"
      :items="contextMenu.items.value"
      @close="contextMenu.close()"
    />
  </div>
</template>
