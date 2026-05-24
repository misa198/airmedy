import { ref, watch } from 'vue'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { getTrackDisplayTitle } from '@/lib/utils'

export type ColumnKey =
  | 'dnd'
  | 'index'
  | 'title'
  | 'duration'
  | 'artist'
  | 'album'
  | 'year'
  | 'genre'
  | 'favorite'
  | 'play_count'
  | 'disc_number'
  | 'track_number'
  | 'album_artist'
  | 'context_menu'

export interface ColumnDef {
  key: ColumnKey
  labelKey: string
  gridWidth: string
  minWidthPx: number
  alwaysVisible: boolean
  sortable: boolean
  sortFn?: (a: TrackDTO, b: TrackDTO) => number
  sticky?: 'left' | 'right'
  draggable: boolean
}

const strCmp = (a: string, b: string) => a.localeCompare(b)
const numCmp = (a: number, b: number) => a - b

export const COLUMNS: ColumnDef[] = [
  {
    key: 'dnd',
    labelKey: '',
    gridWidth: '32px',
    minWidthPx: 32,
    alwaysVisible: false,
    sortable: false,
    sticky: 'left',
    draggable: false,
  },
  {
    key: 'index',
    labelKey: 'library.index',
    gridWidth: '48px',
    minWidthPx: 48,
    alwaysVisible: true,
    sortable: false,
    sticky: 'left',
    draggable: false,
  },
  {
    key: 'title',
    labelKey: 'library.title',
    gridWidth: 'minmax(180px,1fr)',
    minWidthPx: 180,
    alwaysVisible: true,
    sortable: true,
    sortFn: (a, b) => strCmp(getTrackDisplayTitle(a), getTrackDisplayTitle(b)),
    draggable: true,
  },
  {
    key: 'artist',
    labelKey: 'library.artist',
    gridWidth: 'minmax(140px,1fr)',
    minWidthPx: 140,
    alwaysVisible: false,
    sortable: true,
    sortFn: (a, b) => strCmp(a.raw_artist_names || '', b.raw_artist_names || ''),
    draggable: true,
  },
  {
    key: 'duration',
    labelKey: 'library.duration',
    gridWidth: '96px',
    minWidthPx: 96,
    alwaysVisible: false,
    sortable: true,
    sortFn: (a, b) => numCmp(a.duration, b.duration),
    draggable: true,
  },
  {
    key: 'album',
    labelKey: 'library.album',
    gridWidth: 'minmax(140px,1fr)',
    minWidthPx: 140,
    alwaysVisible: false,
    sortable: true,
    sortFn: (a, b) => strCmp(a.album?.title || '', b.album?.title || ''),
    draggable: true,
  },
  {
    key: 'year',
    labelKey: 'library.year',
    gridWidth: '70px',
    minWidthPx: 70,
    alwaysVisible: false,
    sortable: true,
    sortFn: (a, b) => numCmp(a.year, b.year),
    draggable: true,
  },
  {
    key: 'genre',
    labelKey: 'library.genre',
    gridWidth: 'minmax(120px,1fr)',
    minWidthPx: 120,
    alwaysVisible: false,
    sortable: false,
    draggable: true,
  },
  {
    key: 'favorite',
    labelKey: 'library.favorite',
    gridWidth: '52px',
    minWidthPx: 52,
    alwaysVisible: false,
    sortable: false,
    draggable: true,
  },
  {
    key: 'play_count',
    labelKey: 'library.play_count',
    gridWidth: '72px',
    minWidthPx: 72,
    alwaysVisible: false,
    sortable: true,
    sortFn: (a, b) => numCmp(a.play_count, b.play_count),
    draggable: true,
  },
  {
    key: 'disc_number',
    labelKey: 'library.disc_number',
    gridWidth: '80px',
    minWidthPx: 80,
    alwaysVisible: false,
    sortable: true,
    sortFn: (a, b) => numCmp(a.disc_number, b.disc_number),
    draggable: true,
  },
  {
    key: 'track_number',
    labelKey: 'library.track_number',
    gridWidth: '88px',
    minWidthPx: 88,
    alwaysVisible: false,
    sortable: true,
    sortFn: (a, b) => numCmp(a.track_number, b.track_number),
    draggable: true,
  },
  {
    key: 'album_artist',
    labelKey: 'library.album_artist',
    gridWidth: 'minmax(140px,1fr)',
    minWidthPx: 140,
    alwaysVisible: false,
    sortable: true,
    sortFn: (a, b) => strCmp(a.raw_album_artist_names || '', b.raw_album_artist_names || ''),
    draggable: true,
  },
  {
    key: 'context_menu',
    labelKey: 'library.context_menu',
    gridWidth: '48px',
    minWidthPx: 48,
    alwaysVisible: true,
    sortable: false,
    sticky: 'right',
    draggable: false,
  },
]

const COLUMN_MAP = Object.fromEntries(COLUMNS.map((c) => [c.key, c])) as Record<ColumnKey, ColumnDef>

const ALL_KEYS = COLUMNS.map((c) => c.key)
const DEFAULT_VISIBLE: ColumnKey[] = [
  'index',
  'title',
  'duration',
  'artist',
  'album',
  'year',
  'favorite',
  'context_menu',
]

const STORAGE_VISIBLE = 'airmedy:track-table-visible'
const STORAGE_ORDER = 'airmedy:track-table-order'
const STORAGE_WIDTHS = 'airmedy:track-table-widths'
const STORAGE_COLLAPSED = 'airmedy:track-table-collapsed'

function loadJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    if (raw) return JSON.parse(raw) as T
  } catch {}
  return fallback
}

const visibleColumns = ref<ColumnKey[]>(loadJson(STORAGE_VISIBLE, DEFAULT_VISIBLE))
const columnOrder = ref<ColumnKey[]>(
  // ensure all keys present (handles new columns added after save)
  (() => {
    const saved = loadJson<ColumnKey[]>(STORAGE_ORDER, ALL_KEYS)
    const missing = ALL_KEYS.filter((k) => !saved.includes(k))
    return [...saved, ...missing]
  })(),
)
const columnWidths = ref<Partial<Record<ColumnKey, number>>>(loadJson(STORAGE_WIDTHS, {}))
const collapsedMode = ref<boolean>(loadJson(STORAGE_COLLAPSED, false))

watch(
  visibleColumns,
  (v) => localStorage.setItem(STORAGE_VISIBLE, JSON.stringify(v)),
  { deep: true },
)
watch(
  columnOrder,
  (v) => localStorage.setItem(STORAGE_ORDER, JSON.stringify(v)),
  { deep: true },
)
watch(
  columnWidths,
  (v) => localStorage.setItem(STORAGE_WIDTHS, JSON.stringify(v)),
  { deep: true },
)
watch(
  collapsedMode,
  (v) => localStorage.setItem(STORAGE_COLLAPSED, JSON.stringify(v)),
  { deep: true },
)

export function useTrackTableSettings() {
  function toggleColumn(key: ColumnKey) {
    const col = COLUMN_MAP[key]
    if (col.alwaysVisible) return
    const idx = visibleColumns.value.indexOf(key)
    if (idx === -1) {
      visibleColumns.value = [...visibleColumns.value, key]
    } else {
      visibleColumns.value = visibleColumns.value.filter((k) => k !== key)
    }
  }

  function reorderColumns(from: ColumnKey, to: ColumnKey) {
    if (from === to) return
    const order = [...columnOrder.value]
    const fi = order.indexOf(from)
    const ti = order.indexOf(to)
    if (fi === -1 || ti === -1) return
    order.splice(fi, 1)
    order.splice(ti, 0, from)
    columnOrder.value = order
  }

  function setColumnWidth(key: ColumnKey, width: number) {
    columnWidths.value = { ...columnWidths.value, [key]: Math.max(COLUMN_MAP[key].minWidthPx, width) }
  }

  function freezeWidths(widths: Partial<Record<ColumnKey, number>>) {
    columnWidths.value = { ...columnWidths.value, ...widths }
  }

  function effectiveGridWidth(col: ColumnDef): string {
    const override = columnWidths.value[col.key]
    return override !== undefined ? `${override}px` : col.gridWidth
  }

  function toggleCollapsedMode() {
    collapsedMode.value = !collapsedMode.value
  }

  return { visibleColumns, columnOrder, columnWidths, collapsedMode, toggleColumn, reorderColumns, setColumnWidth, freezeWidths, effectiveGridWidth, toggleCollapsedMode, COLUMN_MAP }
}
