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
import VirtualList from 'vue-virtual-sortable'

const SIMPLE_COLUMNS: ColumnKey[] = ['dnd', 'index', 'title', 'artist', 'duration', 'context_menu']
const HEADER_HEIGHT = 40
const rowHeight = computed(() => settings.collapsedMode.value ? 36 : 56)
const BUFFER = 5

const router = useRouter()
const playerStore = usePlayerStore()
const settings = useTrackTableSettings()

const props = withDefaults(defineProps<{
  tracks: TrackDTO[]
  isLoading?: boolean
  showArtwork?: boolean
  scrollToCurrent?: boolean
  simpleMode?: boolean
  hideColumns?: ColumnKey[]
  hideHeader?: boolean
  variant?: 'default' | 'glass'
  contextMenuOptions?: TrackContextMenuOptions
  allowDnd?: boolean
}>(), {
  allowDnd: false
})

const emit = defineEmits<{
  'play-track': [track: TrackDTO, index: number, queue: TrackDTO[]]
  'navigate-album': [id: string]
  'navigate-artist': [id: string]
  'reorder': [tracks: TrackDTO[]]
}>()

const internalTracks = ref<TrackDTO[]>([])
watch(() => props.tracks, (newTracks) => {
  internalTracks.value = [...newTracks]
}, { immediate: true })

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

const displayTracks = computed({
  get: () => {
    if (!sortColumn.value || !sortDir.value) return internalTracks.value
    const col = COLUMNS.find((c) => c.key === sortColumn.value)
    if (!col?.sortFn) return internalTracks.value
    const fn = col.sortFn
    return [...internalTracks.value].sort((a, b) => {
      const r = fn(a, b)
      return sortDir.value === 'asc' ? r : -r
    })
  },
  set: (val) => {
    internalTracks.value = val
    emit('reorder', val)
  }
})

// ── Column layout ──────────────────────────────────────────────────────────
const orderedVisibleColumns = computed(() => {
  const hideSet = new Set(props.hideColumns ?? [])
  const visibleSet = props.simpleMode
    ? new Set(SIMPLE_COLUMNS)
    : new Set(settings.visibleColumns.value)

  const cols = settings.columnOrder.value
    .map((k) => COLUMNS.find((c) => c.key === k)!)
    .filter(
      (col) =>
        col &&
        col.key !== 'dnd' && // Handle dnd separately based on props.allowDnd
        visibleSet.has(col.key) &&
        !hideSet.has(col.key) &&
        (props.simpleMode ? SIMPLE_COLUMNS.includes(col.key) : true),
    )

  if (props.allowDnd) {
    const dndCol = COLUMNS.find(c => c.key === 'dnd')!
    return [dndCol, ...cols]
  }

  return cols
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
  COLUMNS.filter((c) => !c.alwaysVisible && !(props.hideColumns ?? []).includes(c.key) && c.key !== 'dnd'),
)

// ── Virtual scroll ─────────────────────────────────────────────────────────
const scrollerRef = ref<any>(null)
const headerContainerRef = ref<HTMLElement | null>(null)
const containerHeight = ref(0)
let ro: ResizeObserver | null = null

const effectiveHeaderHeight = computed(() => props.hideHeader ? 0 : HEADER_HEIGHT)

// ── Selection ──────────────────────────────────────────────────────────────
const selectedIds = ref(new Set<string>())
const lastSelectedIndex = ref<number | null>(null)
const selectionAnchorIndex = ref<number | null>(null)

function updateRangeSelection(startIndex: number, endIndex: number, additive = false) {
  const start = Math.min(startIndex, endIndex)
  const end = Math.max(startIndex, endIndex)
  const newSelected = new Set(additive ? selectedIds.value : [])
  for (let i = start; i <= end; i++) {
    newSelected.add(displayTracks.value[i].id)
  }
  selectedIds.value = newSelected
}

function handleTrackClick(event: MouseEvent, track: TrackDTO, index: number) {
  const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0
  const cmdOrCtrl = isMac ? event.metaKey : event.ctrlKey
  const shift = event.shiftKey

  if (shift && selectionAnchorIndex.value !== null) {
    updateRangeSelection(selectionAnchorIndex.value, index, cmdOrCtrl)
    lastSelectedIndex.value = index
  } else if (cmdOrCtrl) {
    if (selectedIds.value.has(track.id)) {
      selectedIds.value.delete(track.id)
    } else {
      selectedIds.value.add(track.id)
    }
    lastSelectedIndex.value = index
    selectionAnchorIndex.value = index
  } else {
    selectedIds.value = new Set([track.id])
    lastSelectedIndex.value = index
    selectionAnchorIndex.value = index
  }
}

function handleKeyDown(e: KeyboardEvent) {
  if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return
  if (!e.shiftKey || lastSelectedIndex.value === null || selectionAnchorIndex.value === null) return

  if (e.key === 'ArrowUp') {
    e.preventDefault()
    const nextIndex = Math.max(0, lastSelectedIndex.value - 1)
    if (nextIndex !== lastSelectedIndex.value) {
      lastSelectedIndex.value = nextIndex
      updateRangeSelection(selectionAnchorIndex.value, nextIndex)
      scrollerRef.value?.scrollToItem(nextIndex)
    }
  } else if (e.key === 'ArrowDown') {
    e.preventDefault()
    const nextIndex = Math.min(displayTracks.value.length - 1, lastSelectedIndex.value + 1)
    if (nextIndex !== lastSelectedIndex.value) {
      lastSelectedIndex.value = nextIndex
      updateRangeSelection(selectionAnchorIndex.value, nextIndex)
      scrollerRef.value?.scrollToItem(nextIndex)
    }
  }
}

function handleScroll(e: Event) {
  const target = e.target as HTMLElement
  if (headerContainerRef.value) {
    headerContainerRef.value.scrollLeft = target.scrollLeft
  }
  trackContextMenu.value?.close()
}

function scrollToCurrentTrack() {
  if (!scrollerRef.value || !playerStore.currentTrack || props.tracks.length === 0) return
  const index = displayTracks.value.findIndex((t) => t.id === playerStore.currentTrack?.id)
  if (index !== -1) {
    scrollerRef.value.scrollToIndex(index)
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
  window.addEventListener('keydown', handleKeyDown)
  if (scrollerRef.value?.$el) {
    ro = new ResizeObserver((entries) => {
      containerHeight.value = entries[0].contentRect.height
    })
    ro.observe(scrollerRef.value.$el)
  }
  if (props.scrollToCurrent) {
    setTimeout(scrollToCurrentTrack, 100)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeyDown)
  ro?.disconnect()
})

// ── Context menu ───────────────────────────────────────────────────────────
const trackContextMenu = ref<InstanceType<typeof TrackContextMenu> | null>(null)

function openContextMenu(e: MouseEvent, item: TrackDTO) {
  if (selectedIds.value.size > 1 && selectedIds.value.has(item.id)) {
    const selectedTracks = displayTracks.value.filter(t => selectedIds.value.has(t.id))
    trackContextMenu.value?.openMulti(e, selectedTracks, props.contextMenuOptions)
  } else {
    // If not in selection or only 1 selected, select this one and show normal menu
    selectedIds.value = new Set([item.id])
    const index = displayTracks.value.findIndex(t => t.id === item.id)
    lastSelectedIndex.value = index !== -1 ? index : null
    selectionAnchorIndex.value = lastSelectedIndex.value
    trackContextMenu.value?.open(e, item, props.contextMenuOptions)
  }
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
  emit('play-track', track, index, displayTracks.value)
}

defineExpose({ scrollToCurrentTrack })
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden relative">
    <TrackTableFilter :simple-mode="simpleMode" :optional-columns="optionalColumns" />

    <div class="flex-1 overflow-hidden">
      <div v-if="isLoading" class="h-full flex items-center justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
      </div>

      <div v-else-if="tracks.length === 0"
        class="h-full flex flex-col items-center justify-center text-foreground opacity-80 py-10">
        <Music class="w-12 h-12 mb-4 opacity-20" />
        <p>{{ $t('library.no_tracks') }}</p>
      </div>

      <div v-else class="h-full flex flex-col overflow-hidden">
        <div ref="headerContainerRef" class="overflow-hidden flex-shrink-0">
          <div :style="{ minWidth: totalMinWidth }">
            <TrackTableHeader v-if="!hideHeader" :ordered-visible-columns="orderedVisibleColumns"
              :simple-mode="simpleMode" :sort-column="sortColumn" :sort-dir="sortDir"
              :grid-template-columns="gridTemplateColumns" :header-height="effectiveHeaderHeight" :variant="variant"
              @cycle-sort="cycleSort" />
          </div>
        </div>

        <VirtualList
          ref="scrollerRef"
          v-model="displayTracks"
          data-key="id"
          :size="rowHeight"
          handle=".dnd-handle"
          :sortable="allowDnd"
          :animation="150"
          :force-fallback="true"
          fallback-class="drag-chosen"
          chosen-class="drag-chosen"
          :ghost-style="{ display: 'none' }"
          class="flex-1 overflow-auto custom-scrollbar track-table-virtual-list transform-gpu"
          :wrap-style="{ minWidth: totalMinWidth }"
          @scroll="handleScroll"
        >
          <template v-slot:item="{ record, index }">
            <div :style="{ minWidth: totalMinWidth, height: `${rowHeight}px`, position: 'relative' }">
              <TrackTableRow :track="record" :index="index" :current-index="index"
                :ordered-visible-columns="orderedVisibleColumns" :grid-template-columns="gridTemplateColumns"
                :show-artwork="showArtwork && !settings.collapsedMode.value" :row-bg="rowBg" :variant="variant" :is-selected="selectedIds.has(record.id)"
                @click="handleTrackClick($event, record, index)" @play-track="handlePlayTrack"
                @contextmenu="openContextMenu" @navigate-album="navigateToAlbum" @navigate-artist="navigateToArtist" />
            </div>
          </template>
        </VirtualList>
      </div>
    </div>
  </div>

  <TrackContextMenu ref="trackContextMenu" />
</template>

<style scoped>
.track-table-virtual-list {
  overflow: auto !important;
}

.drag-chosen {
  background: var(--bg-main) !important;
  opacity: 0.9;
  z-index: 200;
  box-shadow: rgba(100, 100, 111, 0.2) 0px 7px 29px 0px;
  border-radius: 4px;
}
</style>
