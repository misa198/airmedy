<script setup lang="ts">
import { ref, shallowRef, computed } from 'vue'
import { useDetailRouteLoader } from '@/composables/useDetailRouteLoader'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { Genre, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import GroupedAlbumList from '../components/GroupedAlbumList.vue'
import { Tag, Music, Shuffle, Play, MoreVertical } from '@lucide/vue'
import { usePlayerStore } from '../stores/player'
import { useI18n } from 'vue-i18n'
import { useContextMenu } from '@/composables/useContextMenu'
import { useGroupContextMenu } from '@/composables/useGroupContextMenu'
import ContextMenu from '../components/ContextMenu.vue'
import { DetailsButton } from '@airmedy/ui'
import { sortTracksGrouped } from '@/lib/trackSort'
import { useLibrarySync } from '@/composables/useLibrarySync'

const { t } = useI18n()

const playerStore = usePlayerStore()
const genre = ref<Genre | null>(null)
const tracks = shallowRef<TrackDTO[]>([])
const isLoading = ref(true)

const contextMenu = useContextMenu()
const { buildMenuItems } = useGroupContextMenu()
const sortedTracks = computed(() => sortTracksGrouped(tracks.value))

function openContextMenu(e: MouseEvent) {
  contextMenu.open(e, buildMenuItems(tracks.value))
}

// Synchronous token marking the most recently requested genre, set before
// any await so a fast-resolving load can't be mistaken for stale (comparing
// against vue-router's reactive route.params.id instead is racy: it may not
// have finished updating yet by the time a quick fetch resolves).
let currentGenreId: string | null = null
const isStale = (id: string) => currentGenreId !== id

const loadGenreDetails = async (id: string, silent = false) => {
  currentGenreId = id
  if (!silent) isLoading.value = true
  try {
    const [genreData, tracksData] = await Promise.all([
      LibraryService.GetGenreByID(id),
      LibraryService.GetTracksByGenreID(id)
    ])
    if (isStale(id)) return
    genre.value = genreData
    tracks.value = tracksData.filter((t): t is TrackDTO => t !== null)
  } catch (err) {
    console.error('Failed to load genre details:', err)
  } finally {
    if (!isStale(id)) isLoading.value = false
  }
}

// Track edits can move tracks in or out of this genre; refresh silently.
// Reads our own loaded genre id, not route.params.id — this listener stays
// registered while KeepAlive backgrounds this instance on an unrelated route.
useLibrarySync(() => {
  const id = genre.value?.id
  if (id) loadGenreDetails(id, true)
})

useDetailRouteLoader(loadGenreDetails)
</script>

<template>
  <div class="h-full flex flex-col bg-background overflow-hidden animate-in fade-in slide-in-from-right-4 duration-300">
    <div v-if="isLoading" class="flex-1 flex items-center justify-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
    </div>

    <div v-else-if="genre" class="flex-1 flex flex-col overflow-hidden">
      <!-- Genre Header -->
      <div class="p-8 border-b border-foreground/[0.06] bg-gradient-to-b from-dynamic-surface to-transparent flex items-end gap-6 flex-shrink-0">
        <div class="w-24 h-24 rounded-2xl bg-foreground/5 flex items-center justify-center ring-1 ring-foreground/[0.08] flex-shrink-0">
          <Tag class="w-12 h-12 text-foreground opacity-70" />
        </div>
        <div class="flex-1 space-y-2">
          <h1 class="text-3xl font-bold tracking-tight line-clamp-2">{{ genre.name || t('library.unknown_genre') }}</h1>
          <div class="flex items-center gap-4 text-dim">
            <span class="flex items-center gap-1 text-sm"><Music class="w-4 h-4" /> {{ t('genre.tracks_count', { count: tracks.length }) }}</span>
          </div>
          <div class="pt-2 flex items-center gap-4">
            <DetailsButton :icon="Play" :label="t('common.play')" :filled-icon="true" @click="playerStore.playTracks(sortedTracks, 0)" />
            <div class="flex gap-2">
              <DetailsButton :icon="Shuffle" variant="outline" @click="playerStore.shuffleTracks(tracks)" />
              <DetailsButton :icon="MoreVertical" variant="outline" @click="openContextMenu" />
            </div>
          </div>
        </div>
      </div>

      <!-- Grouped Albums -->
      <div class="flex-1 min-h-0">
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
