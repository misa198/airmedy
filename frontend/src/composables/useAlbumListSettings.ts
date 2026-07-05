import { ref, watch } from 'vue'

export type AlbumColumnKey = 'artist' | 'year' | 'dateAdded'

export interface AlbumColumnDef {
  key: AlbumColumnKey
  labelKey: string
}

export const ALBUM_COLUMNS: AlbumColumnDef[] = [
  { key: 'artist', labelKey: 'library.artist' },
  { key: 'year', labelKey: 'library.year' },
  { key: 'dateAdded', labelKey: 'library.date_added' },
]

const DEFAULT_VISIBLE: AlbumColumnKey[] = ['artist', 'year']
const STORAGE_VISIBLE = 'airmedy:albums-visible-columns'

function loadJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    if (raw) return JSON.parse(raw) as T
  } catch {}
  return fallback
}

const visibleColumns = ref<AlbumColumnKey[]>(loadJson(STORAGE_VISIBLE, DEFAULT_VISIBLE))

watch(
  visibleColumns,
  (v) => localStorage.setItem(STORAGE_VISIBLE, JSON.stringify(v)),
  { deep: true },
)

export function useAlbumListSettings() {
  function toggleColumn(key: AlbumColumnKey) {
    const idx = visibleColumns.value.indexOf(key)
    if (idx === -1) {
      visibleColumns.value = [...visibleColumns.value, key]
    } else {
      visibleColumns.value = visibleColumns.value.filter((k) => k !== key)
    }
  }

  return { visibleColumns, toggleColumn }
}
