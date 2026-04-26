<script setup lang="ts">
import { Music } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { usePlayerStore } from '../stores/player'
import TrackContextMenu from './TrackContextMenu.vue'
import TrackTableFilter from './TrackTableFilter.vue'
import TrackTableHeader from './TrackTableHeader.vue'
import TrackTableRow from './TrackTableRow.vue'
import { COLUMNS, type ColumnKey, useTrackTableSettings } from '@/composables/useTrackTableSettings'
import type { TrackContextMenuOptions } from '@/composables/useTrackContextMenu'

const SIMPLE_COLUMNS: ColumnKey[] = ['index', 'title', 'duration', 'context_menu']
const HEADER_HEIGHT = 40
const ROW_HEIGHT = 56
const BUFFER = 5

const router = useRouter()
const playerStore = usePlayerStore()
const settings = useTrackTableSettings()

const props = defineProps<{
  tracks: TrackDTO[]
  isLoading?: boolean
  showArtwork?: boolean
  scrollToCurrent?: boolean
  simpleMode?: boolean
  hideColumns?: ColumnKey[]
  hideHeader?: boolean
  variant?: 'default' | 'glass'
  contextMenuOptions?: TrackContextMenuOptions
}>()

const emit = defineEmits<{
  'play-track': [track: TrackDTO, index: number, queue: TrackDTO[]]
  'navigate-album': [id: string]
  'navigate-artist': [id: string]
}>()

// ── Sorting ────────────────────────────────────────────────────────────────
const sortColumn = ref<ColumnKey | null>(null)
const sortDir = ref<'asc' | 'desc' | null>(null)

function cycleSort(key: ColumnKey) {
  if (props.simpleMode) return
  if (sortColumn.value !== key) {
    sortColumn.value = key
    sortDir.value = 'asc'
    return
  }
  if (sortDir.value === 'asc') {
    sortDir.value = 'desc'
    return
  }
  sortColumn.value = null
  sortDir.value = null
}

const sortedTracks = computed(() => {
  if (!sortColumn.value || !sortDir.value) return props.tracks
  const col = COLUMNS.find((c) => c.key === sortColumn.value)
  if (!col?.sortFn) return props.tracks
  const fn = col.sortFn
  return [...props.tracks].sort((a, b) => {
    const r = fn(a, b)
    return sortDir.value === 'asc' ? r : -r
  })
})

// ── Column layout ──────────────────────────────────────────────────────────
const orderedVisibleColumns = computed(() => {
  const hideSet = new Set(props.hideColumns ?? [])
  const visibleSet = props.simpleMode
    ? new Set(SIMPLE_COLUMNS)
    : new Set(settings.visibleColumns.value)

  return settings.columnOrder.value
    .map((k) => COLUMNS.find((c) => c.key === k)!)
    .filter(
      (col) =>
        col &&
        visibleSet.has(col.key) &&
        !hideSet.has(col.key) &&
        (props.simpleMode ? SIMPLE_COLUMNS.includes(col.key) : true),
    )
})

const gridTemplateColumns = computed(() =>
  orderedVisibleColumns.value.map((c) => props.simpleMode ? c.gridWidth : settings.effectiveGridWidth(c)).join(' '),
)

const totalMinWidth = computed(() => {
  const sum = orderedVisibleColumns.value.reduce((acc, c) => {
    const override = settings.columnWidths.value[c.key]
    return acc + (props.simpleMode || override === undefined ? c.minWidthPx : override)
  }, 0)
  return sum + 'px'
})

const optionalColumns = computed(() =>
  COLUMNS.filter((c) => !c.alwaysVisible && !(props.hideColumns ?? []).includes(c.key)),
)

// ── Virtual scroll ─────────────────────────────────────────────────────────
const scrollEl = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const containerHeight = ref(0)
let ro: ResizeObserver | null = null

const effectiveHeaderHeight = computed(() => props.hideHeader ? 0 : HEADER_HEIGHT)

const totalHeight = computed(
  () => effectiveHeaderHeight.value + sortedTracks.value.length * ROW_HEIGHT,
)

const visibleStart = computed(() =>
  Math.max(0, Math.floor((scrollTop.value - effectiveHeaderHeight.value) / ROW_HEIGHT) - BUFFER),
)

const visibleEnd = computed(() =>
  Math.min(
    sortedTracks.value.length,
    Math.ceil((scrollTop.value - effectiveHeaderHeight.value + containerHeight.value) / ROW_HEIGHT) + BUFFER,
  ),
)

const visibleRows = computed(() =>
  sortedTracks.value.slice(visibleStart.value, visibleEnd.value).map((track, i) => ({
    track,
    index: visibleStart.value + i,
    top: effectiveHeaderHeight.value + (visibleStart.value + i) * ROW_HEIGHT,
  })),
)

function onScroll(e: Event) {
  const el = e.target as HTMLElement
  scrollTop.value = el.scrollTop
  trackContextMenu.value?.close()
}

function scrollToCurrentTrack() {
  if (!scrollEl.value || !playerStore.currentTrack || props.tracks.length === 0) return
  const index = sortedTracks.value.findIndex((t) => t.id === playerStore.currentTrack?.id)
  if (index !== -1) {
    const target = effectiveHeaderHeight.value + index * ROW_HEIGHT - containerHeight.value / 2
    scrollEl.value.scrollTop = Math.max(0, target)
  }
}

watch(
  [() => props.tracks, () => playerStore.currentTrack],
  () => {
    if (props.scrollToCurrent) nextTick(scrollToCurrentTrack)
  },
  { deep: false },
)

onMounted(() => {
  if (scrollEl.value) {
    ro = new ResizeObserver((entries) => {
      containerHeight.value = entries[0].contentRect.height
    })
    ro.observe(scrollEl.value)
  }
  if (props.scrollToCurrent) {
    setTimeout(scrollToCurrentTrack, 100)
  }
})

onBeforeUnmount(() => {
  ro?.disconnect()
})

// ── Context menu ───────────────────────────────────────────────────────────
const trackContextMenu = ref<InstanceType<typeof TrackContextMenu> | null>(null)

function openContextMenu(e: MouseEvent, item: TrackDTO) {
  trackContextMenu.value?.open(e, item, props.contextMenuOptions)
}

// ── Navigation ─────────────────────────────────────────────────────────────
const navigateToAlbum = (id: string) => {
  if (playerStore.playerMode === 'fullscreen') {
    playerStore.playerMode = 'sticky'
  }
  router.push(`/albums/${id}`)
  emit('navigate-album', id)
}
const navigateToArtist = (id: string) => {
  if (!id) return
  if (playerStore.playerMode === 'fullscreen') {
    playerStore.playerMode = 'sticky'
  }
  router.push(`/artists/${id}`)
  emit('navigate-artist', id)
}

function rowBg(index: number, opaque = false) {
  if (props.variant === 'glass' && opaque) return 'transparent'

  if (index % 2 !== 0) {
    return opaque ? 'var(--bg-main)' : 'transparent'
  }
  return opaque
    ? 'color-mix(in srgb, var(--bg-main), var(--text-main) 2%)'
    : 'var(--bg-zebra)'
}

function handlePlayTrack(track: TrackDTO, index: number) {
  emit('play-track', track, index, sortedTracks.value)
}
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden select-none relative">
    <TrackTableFilter :simple-mode="simpleMode" :optional-columns="optionalColumns" />

    <div ref="scrollEl" class="flex-1 overflow-auto" @scroll="onScroll">
      <div v-if="isLoading" class="h-full flex items-center justify-center" :style="{ minWidth: totalMinWidth }">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
      </div>

      <div v-else-if="tracks.length === 0"
        class="h-full flex flex-col items-center justify-center text-foreground/80 py-10"
        :style="{ minWidth: totalMinWidth }">
        <Music class="w-12 h-12 mb-4 opacity-20" />
        <p>{{ $t('library.no_tracks') }}</p>
      </div>

      <div v-else :style="{
        minWidth: totalMinWidth,
        height: totalHeight + 'px',
        position: 'relative',
      }">
        <TrackTableHeader v-if="!hideHeader" :ordered-visible-columns="orderedVisibleColumns" :simple-mode="simpleMode"
          :sort-column="sortColumn" :sort-dir="sortDir" :grid-template-columns="gridTemplateColumns"
          :header-height="effectiveHeaderHeight" :variant="variant" @cycle-sort="cycleSort" />

        <TrackTableRow v-for="{ track, index, top } in visibleRows" :key="track.id" :track="track" :index="index"
          :top="top" :ordered-visible-columns="orderedVisibleColumns" :grid-template-columns="gridTemplateColumns"
          :show-artwork="showArtwork" :row-bg="rowBg" :variant="variant" @play-track="handlePlayTrack"
          @contextmenu="openContextMenu" @navigate-album="navigateToAlbum" @navigate-artist="navigateToArtist" />
      </div>
    </div>
  </div>

  <TrackContextMenu ref="trackContextMenu" />
</template>
