<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import { ArrowDown, ArrowUp, ArrowUpDown, Heart } from 'lucide-vue-next'
import { type ColumnDef, type ColumnKey, useTrackTableSettings } from '@/composables/useTrackTableSettings'

const props = defineProps<{
  orderedVisibleColumns: ColumnDef[]
  simpleMode?: boolean
  sortColumn: ColumnKey | null
  sortDir: 'asc' | 'desc' | null
  gridTemplateColumns: string
  headerHeight: number
  variant?: 'default' | 'glass'
}>()

const emit = defineEmits<{
  'cycle-sort': [key: ColumnKey]
}>()

const settings = useTrackTableSettings()

// ── Column drag-and-drop ───────────────────────────────────────────────────
const dragFrom = ref<ColumnKey | null>(null)
const dragOver = ref<ColumnKey | null>(null)
let suppressNextSort = false

function onDragStart(e: DragEvent, key: ColumnKey) {
  e.dataTransfer?.setData('text/plain', key)
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'

  const cell = e.currentTarget as HTMLElement
  const ghost = cell.cloneNode(true) as HTMLElement
  const effectiveBg = (() => {
    let node: HTMLElement | null = cell
    while (node && node !== document.body) {
      const bg = getComputedStyle(node).backgroundColor
      if (bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') return bg
      node = node.parentElement
    }
    return '#1a1a1a'
  })()
  Object.assign(ghost.style, {
    position: 'fixed',
    top: '-9999px',
    left: '0',
    width: cell.offsetWidth + 'px',
    height: cell.offsetHeight + 'px',
    background: effectiveBg,
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: '6px',
    display: 'flex',
    alignItems: 'center',
    padding: '0 8px',
    boxSizing: 'border-box',
    pointerEvents: 'none',
    opacity: '1',
    fontSize: '10px',
    textTransform: 'uppercase',
    fontFamily: 'var(--font-sans)',
    letterSpacing: '1px',
    fontWeight: '600'
  })
  document.body.appendChild(ghost)
  e.dataTransfer?.setDragImage(ghost, cell.offsetWidth / 2, cell.offsetHeight / 2)
  requestAnimationFrame(() => ghost.remove())

  dragFrom.value = key
  suppressNextSort = false
}

function onDragOver(e: DragEvent, key: ColumnKey) {
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  dragOver.value = key
}

function onDrop(e: DragEvent, key: ColumnKey) {
  e.preventDefault()
  if (dragFrom.value && dragFrom.value !== key) {
    settings.reorderColumns(dragFrom.value, key)
  }
  dragFrom.value = null
  dragOver.value = null
}

function onDragEnd() {
  suppressNextSort = true
  dragFrom.value = null
  dragOver.value = null
}

function handleHeaderClick(key: ColumnKey) {
  if (suppressNextSort) {
    suppressNextSort = false
    return
  }
  emit('cycle-sort', key)
}

// ── Column resize ──────────────────────────────────────────────────────────
const resizing = ref<{ key: ColumnKey; startX: number; startWidth: number } | null>(null)

function startResize(e: MouseEvent, col: ColumnDef) {
  e.preventDefault()
  e.stopPropagation()

  const headerGrid = (e.currentTarget as HTMLElement).parentElement?.parentElement
  if (headerGrid) {
    const cells = Array.from(headerGrid.children) as HTMLElement[]
    const snapshot: Partial<Record<ColumnKey, number>> = {}
    props.orderedVisibleColumns.forEach((c, i) => {
      if (cells[i]) snapshot[c.key] = Math.round(cells[i].getBoundingClientRect().width)
    })
    settings.freezeWidths(snapshot)
  }

  const cellEl = (e.currentTarget as HTMLElement).parentElement
  const startWidth = cellEl ? Math.round(cellEl.getBoundingClientRect().width) : col.minWidthPx
  resizing.value = { key: col.key, startX: e.clientX, startWidth }
  document.addEventListener('mousemove', onResizeMove)
  document.addEventListener('mouseup', onResizeEnd)
}

function onResizeMove(e: MouseEvent) {
  if (!resizing.value) return
  const dx = e.clientX - resizing.value.startX
  settings.setColumnWidth(resizing.value.key, resizing.value.startWidth + dx)
}

function onResizeEnd() {
  resizing.value = null
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
}

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
})
</script>

<template>
  <div
    class="grid sticky top-0 z-10 border-b border-foreground/[0.06] text-[10px] font-semibold text-foreground opacity-80 uppercase tracking-widest overflow-visible"
    :class="variant === 'glass' ? 'bg-transparent' : 'bg-background'"
    :style="{ gridTemplateColumns, height: headerHeight + 'px' }"
  >
    <template v-for="col in orderedVisibleColumns" :key="col.key">
      <!-- DnD header -->
      <div
        v-if="col.key === 'dnd'"
        class="sticky left-0 z-10 flex items-center justify-center relative"
        :class="variant === 'glass' ? 'bg-transparent' : 'bg-background'"
      />

      <!-- Index header -->
      <div
        v-else-if="col.key === 'index'"
        class="sticky z-10 flex items-center justify-center relative"
        :class="[
          variant === 'glass' ? 'bg-transparent' : 'bg-background',
          orderedVisibleColumns[0].key === 'dnd' ? 'left-[32px]' : 'left-0'
        ]"
      >
        #
        <!-- Column divider / resize handle -->
        <div
          v-if="!simpleMode"
          class="absolute top-1/2 -translate-y-1/2 -right-2 h-4/5 w-4 z-20 cursor-col-resize flex items-center justify-center group/resize"
          @mousedown.stop.prevent="startResize($event, col)"
          @click.stop
        >
          <div class="w-px h-full bg-foreground/[0.12] group-hover/resize:bg-primary/60 transition-colors" />
        </div>
      </div>

      <!-- Context menu header -->
      <div
        v-else-if="col.key === 'context_menu'"
        class="sticky right-0 z-10"
        :class="variant === 'glass' ? 'bg-transparent' : 'bg-background'"
      />

      <!-- Sortable column header -->
      <div
        v-else-if="col.sortable && !simpleMode"
        class="relative flex items-center gap-1 px-2 min-w-0 cursor-grab hover:text-foreground opacity-100 transition-colors select-none"
        :class="{
          'text-primary': sortColumn === col.key,
          'opacity-60 ring-1 ring-primary/40 rounded bg-primary/20': dragOver === col.key && dragFrom !== col.key,
          'opacity-40': dragFrom === col.key,
        }"
        :draggable="col.draggable"
        @click="handleHeaderClick(col.key)"
        @dragstart="onDragStart($event, col.key)"
        @dragover="onDragOver($event, col.key)"
        @drop="onDrop($event, col.key)"
        @dragend="onDragEnd"
      >
        <Heart v-if="col.key === 'favorite'" class="w-1 h-3 flex-shrink-0 pointer-events-none" draggable="false" />
        <span v-else class="truncate min-w-0 pointer-events-none" draggable="false">{{ $t(col.labelKey) }}</span>
        <ArrowUp v-if="sortColumn === col.key && sortDir === 'asc'" class="w-3 h-3 flex-shrink-0 pointer-events-none" draggable="false" />
        <ArrowDown v-else-if="sortColumn === col.key && sortDir === 'desc'" class="w-3 h-3 flex-shrink-0 pointer-events-none" draggable="false" />
        <ArrowUpDown v-else class="w-3 h-3 flex-shrink-0 opacity-40 pointer-events-none" draggable="false" />
        <!-- Column divider / resize handle -->
        <div
          v-if="!simpleMode"
          class="absolute top-1/2 -translate-y-1/2 -right-2 h-4/5 w-4 z-20 cursor-col-resize flex items-center justify-center group/resize"
          @mousedown.stop.prevent="startResize($event, col)"
          @click.stop
        >
          <div class="w-px h-full bg-foreground/[0.12] group-hover/resize:bg-primary/60 transition-colors" />
        </div>
      </div>

      <!-- Non-sortable column header -->
      <div
        v-else
        class="relative flex items-center px-2 min-w-0 transition-colors"
        :class="[
          col.draggable && !simpleMode ? 'cursor-grab' : '',
          {
            'opacity-60 ring-1 ring-primary/40 rounded': dragOver === col.key && dragFrom !== col.key,
            'opacity-40': dragFrom === col.key,
          },
        ]"
        :draggable="col.draggable && !simpleMode"
        @dragstart="!simpleMode && onDragStart($event, col.key)"
        @dragover="!simpleMode && onDragOver($event, col.key)"
        @drop="!simpleMode && onDrop($event, col.key)"
        @dragend="!simpleMode && onDragEnd"
      >
        <Heart v-if="col.key === 'favorite'" class="w-3 h-3 flex-shrink-0 pointer-events-none" draggable="false" />
        <span v-else class="truncate min-w-0 pointer-events-none" draggable="false">{{ $t(col.labelKey) }}</span>
        <!-- Column divider / resize handle -->
        <div
          v-if="!simpleMode"
          class="absolute top-1/2 -translate-y-1/2 -right-2 h-4/5 w-4 z-20 cursor-col-resize flex items-center justify-center group/resize"
          @mousedown.stop.prevent="startResize($event, col)"
          @click.stop
        >
          <div class="w-px h-full bg-foreground/[0.12] group-hover/resize:bg-primary/60 transition-colors" />
        </div>
      </div>
    </template>
  </div>
</template>
