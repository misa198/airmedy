<script setup lang="ts">
import { ref, onMounted, watch, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { AlbumDTO, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import TrackTable from '../components/TrackTable.vue'
import { Disc, User, Play, Clock, Calendar, ArrowLeft, MoreVertical, Music, Shuffle } from 'lucide-vue-next'
import { usePlayerStore, type ThemeColors } from '../stores/player'
import { hexToRgba } from '../lib/utils'
import { useContextMenu } from '@/composables/useContextMenu'
import { useGroupContextMenu } from '@/composables/useGroupContextMenu'
import ContextMenu from '../components/ContextMenu.vue'
import DetailsButton from '@/components/ui/DetailsButton.vue'

const playerStore = usePlayerStore()

const route = useRoute()
const router = useRouter()
const album = ref<AlbumDTO | null>(null)
const tracks = ref<TrackDTO[]>([])
const isLoading = ref(true)
const albumTheme = ref<ThemeColors | null>(null)

const scrollContainerRef = ref<HTMLElement | null>(null)
const lastScrollTop = ref(0)

const contextMenu = useContextMenu()
const { buildMenuItems } = useGroupContextMenu()

function openContextMenu(e: MouseEvent) {
  contextMenu.open(e, buildMenuItems(tracks.value))
}

const handleScroll = (event: Event) => {
  const target = event.target as HTMLElement
  if (target) {
    lastScrollTop.value = target.scrollTop
  }
}

onActivated(() => {
  if (scrollContainerRef.value && lastScrollTop.value > 0) {
    setTimeout(() => {
      if (scrollContainerRef.value) {
        scrollContainerRef.value.scrollTop = lastScrollTop.value
      }
    }, 0)
  }
})

const loadAlbumDetails = async (id: string) => {
  isLoading.value = true
  try {
    const [albumData, tracksData] = await Promise.all([
      LibraryService.GetAlbumByID(id),
      LibraryService.GetTracksByAlbumID(id)
    ])
    album.value = albumData
    tracks.value = tracksData.filter((t): t is TrackDTO => t !== null)

    // Fetch album colors for local theme
    try {
      const colors = await LibraryService.GetAlbumColors(id)
      albumTheme.value = colors
    } catch (e) {
      console.warn('Failed to fetch album colors', e)
    }
  } catch (err) {
    console.error('Failed to load album details:', err)
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  const id = route.params.id as string
  if (id) loadAlbumDetails(id)
})

watch(() => route.params.id, (newId) => {
  if (newId) loadAlbumDetails(newId as string)
})

const formatTotalDuration = (tracks: TrackDTO[]) => {
  const totalSeconds = tracks.reduce((acc, t) => acc + (t.duration || 0), 0)
  const hours = Math.floor(totalSeconds / 3600)
  const mins = Math.floor((totalSeconds % 3600) / 60)
  if (hours > 0) return `${hours} hr ${mins} min`
  return `${mins} min`
}
</script>

<template>
  <div class="h-full flex flex-col bg-background overflow-hidden">
    <div v-if="isLoading" class="flex-1 flex items-center justify-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
    </div>

    <div v-else-if="album" ref="scrollContainerRef" class="flex-1 overflow-y-auto" @scroll.passive="handleScroll">
      <!-- Album Hero Section -->
      <div class="flex flex-col bg-gradient-to-b from-dynamic-surface to-transparent"
        :style="{ '--dynamic-surface': albumTheme ? hexToRgba(albumTheme.dominant, 0.15) : 'var(--bg-glass)' }">
        <!-- Top Navigation -->
        <div class="pt-4 px-4 md:pt-5 md:px-8">
          <button @click="router.back()" class="p-2 hover:bg-foreground/[0.06] rounded-full transition-colors">
            <ArrowLeft class="w-6 h-6" />
          </button>
        </div>

        <!-- Album Details Hero -->
        <div class="px-8 pb-8 md:px-12 md:pb-12 pt-4 flex flex-col md:flex-row gap-8 items-end">
          <div
            class="w-48 h-48 rounded-lg shadow-2xl overflow-hidden ring-1 ring-foreground/[0.08] bg-foreground/5 flex-shrink-0">
            <img v-if="album.artwork_key" :src="'/artwork/' + album.artwork_key" class="w-full h-full object-cover" />
            <div v-else class="w-full h-full flex items-center justify-center text-foreground/10">
              <Disc class="w-24 h-24" />
            </div>
          </div>

          <div class="flex-1 space-y-4 @container min-w-0">
            <div class="space-y-2">
              <h1 class="text-2xl @sm:text-3xl @md:text-4xl @lg:text-5xl font-bold tracking-tight line-clamp-2">{{
                album.title || $t('library.unknown_album') }}</h1>
              <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-foreground/40">
                <div class="flex items-center gap-2 text-foreground font-semibold min-w-0">
                  <User class="w-4 h-4 flex-shrink-0" />
                  <span class="line-clamp-1">{{album.artists?.map(a => a?.name).join(', ') ||
                    $t('library.unknown_artist')}}</span>
                </div>
                <div class="flex gap-2 text-sm items-end flex-wrap">
                  <div v-if="album.year" class="flex items-center gap-2">
                    <Calendar class="w-4 h-4" />
                    <span>{{ album.year }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <Music class="w-4 h-4" />
                    <span>{{ tracks.length }} {{ $t('library.songs') }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <Clock class="w-4 h-4" />
                    <span>{{ formatTotalDuration(tracks) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="flex items-center gap-4 pt-2">
              <DetailsButton :icon="Play" :label="$t('common.play')"
                @click="playerStore.playTracks(tracks, 0); playerStore.setShuffle(false)" />
              <div class="flex gap-2">
                <DetailsButton :icon="Shuffle" variant="outline" @click="playerStore.shuffleTracks(tracks)" />
                <DetailsButton :icon="MoreVertical" variant="outline" @click="openContextMenu" />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Track List -->
      <div class="px-2 pb-12">
        <TrackTable :tracks="tracks" :show-artwork="false" :show-album="false"
          @play-track="(_, index) => playerStore.playTracks(tracks, index)" />
      </div>

      <!-- Album Footer Metadata -->
      <div v-if="album.copyright"
        class="px-8 pb-12 text-sm text-foreground/30 border-t border-foreground/[0.06] pt-8 mt-4">
        {{ album.copyright }}
      </div>
    </div>

    <ContextMenu :visible="contextMenu.visible.value" :x="contextMenu.x.value" :y="contextMenu.y.value"
      :items="contextMenu.items.value" @close="contextMenu.close()" />
  </div>
</template>
