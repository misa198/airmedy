<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import * as LibraryService from '../../../bindings/airmedy/internal/infra/wails/libraryservice'
import { RotateCcw, Plus, Trash2, Folder, Loader2, DatabaseZap, Info, Tags } from '@lucide/vue'
import type { WatchedFolder, SyncProgress } from '../../../bindings/airmedy/internal/domain/models'
import { Events } from '@wailsio/runtime'
import ConfirmDialog from '../ConfirmDialog.vue'
import SyncProgressDialog from './SyncProgressDialog.vue'
import DelimiterInput from './DelimiterInput.vue'
import SettingSection from './SettingSection.vue'
import { useAppStore } from '../../stores/app'

const { t } = useI18n()

const appStore = useAppStore()
const { artistDelimiters, albumArtistDelimiters, genreDelimiters, composerDelimiters } = storeToRefs(appStore)

// Becomes true once the user edits any delimiter; cleared after a library sync
// (which re-applies the splitting) completes.
const delimitersDirty = ref(false)

const onDelimitersChange = async (
  field: 'artist' | 'albumArtist' | 'genre' | 'composer',
  value: string[],
) => {
  try {
    await appStore.updateDelimiters(field, value)
    await refreshDelimitersPending()
  } catch (err) {
    console.error('Failed to save delimiters:', err)
  }
}

// State
const folders = ref<WatchedFolder[]>([])
const isSyncing = ref(false)
const syncType = ref<'sync' | 'optimize' | null>(null)
const isLoading = ref(true)
const syncProgress = ref<SyncProgress | null>(null)
const syncComplete = ref(false)
const folderToDelete = ref<string | null>(null)
const removingFolderIds = ref<string[]>([])

const loadFolders = async (showLoader = true) => {
  if (showLoader) isLoading.value = true
  try {
    const result = await LibraryService.GetWatchedFolders()
    folders.value = result.filter((f): f is WatchedFolder => f !== null)
  } catch (err) {
    console.error('Failed to load folders:', err)
  } finally {
    if (showLoader) isLoading.value = false
  }
}

const addFolder = async () => {
  try {
    const path = await LibraryService.SelectFolder()
    if (path) {
      await LibraryService.AddFolder(path)
      await loadFolders(false)
    }
  } catch (err) {
    console.error('Failed to add folder:', err)
  }
}

const removeFolder = async () => {
  if (!folderToDelete.value) return
  const id = folderToDelete.value
  folderToDelete.value = null
  removingFolderIds.value.push(id)
  
  try {
    await LibraryService.RemoveFolder(id)
    await loadFolders(false)
  } catch (err) {
    console.error('Failed to remove folder:', err)
  } finally {
    removingFolderIds.value = removingFolderIds.value.filter(fid => fid !== id)
  }
}

const syncLibrary = async () => {
  if (isSyncing.value) return
  isSyncing.value = true
  syncType.value = 'sync'
  try {
    await LibraryService.SyncAll()
  } catch (err) {
    console.error('Sync failed:', err)
    isSyncing.value = false
    syncType.value = null
  }
}

const optimizeSearch = async () => {
  if (isSyncing.value) return
  isSyncing.value = true
  syncType.value = 'optimize'
  try {
    await LibraryService.ReindexAll()
  } catch (err) {
    console.error('Optimization failed:', err)
    isSyncing.value = false
    syncType.value = null
  }
}

const handleSyncStarted = (ev: Events.WailsEvent) => {
  const data = ev.data as any
  isSyncing.value = true
  syncComplete.value = false
  syncProgress.value = {
    current: 0,
    total: data.total || 0,
    path: data.path || ''
  }
}

const handleSyncProgress = (ev: Events.WailsEvent) => {
  const progress = ev.data as SyncProgress
  isSyncing.value = true
  syncProgress.value = progress
}

const refreshDelimitersPending = async () => {
  try {
    delimitersDirty.value = await LibraryService.DelimitersPendingResync()
  } catch (err) {
    console.error('Failed to check delimiter sync state:', err)
  }
}

const handleSyncFinished = () => {
  isSyncing.value = false
  syncComplete.value = true
  // Re-query the backend so the banner reflects the actual applied state. Always
  // (not gated on syncType): a Sync Library run emits several sync-finished
  // events — folder sync, re-split, then re-index — and only the last one lands
  // after the delimiter signature is persisted.
  refreshDelimitersPending()
}

const showSyncDialog = computed(
  () => isSyncing.value || removingFolderIds.value.length > 0 || syncComplete.value
)

watch(syncComplete, (val) => {
  if (val) setTimeout(() => {
    syncProgress.value = null
    syncComplete.value = false
    syncType.value = null
  }, 1500)
})

let offSyncStarted: (() => void) | null = null
let offSyncProgress: (() => void) | null = null
let offSyncFinished: (() => void) | null = null

onMounted(() => {
  loadFolders()
  refreshDelimitersPending()
  offSyncStarted = Events.On('library:sync-started', handleSyncStarted)
  offSyncProgress = Events.On('library:sync-progress', handleSyncProgress)
  offSyncFinished = Events.On('library:sync-finished', handleSyncFinished)
})

onUnmounted(() => {
  offSyncStarted?.()
  offSyncProgress?.()
  offSyncFinished?.()
})
</script>

<template>
  <div class="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <SettingSection :icon="Folder" :label="t('settings.folders.title')" variant="panel">
      <template #header-extra>
        <div class="flex gap-3">
          <button @click="optimizeSearch" :disabled="isSyncing || removingFolderIds.length > 0 || folders.length === 0"
            class="flex items-center gap-2 px-4 py-2 bg-foreground/[0.04] text-foreground rounded-xl hover:bg-foreground/[0.08] transition-all disabled:opacity-50 text-sm font-bold">
            <DatabaseZap v-if="!isSyncing" class="w-4 h-4" />
            <RotateCcw v-else class="w-4 h-4" :class="{ 'animate-spin': isSyncing }" />
            {{ isSyncing ? t('settings.sync.syncing') : t('settings.sync.optimize_search') }}
          </button>
          <button @click="syncLibrary" :disabled="isSyncing || removingFolderIds.length > 0 || folders.length === 0"
            class="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-xl hover:opacity-90 transition-all disabled:opacity-50 text-sm font-bold shadow-lg shadow-primary/20">
            <RotateCcw class="w-4 h-4" :class="{ 'animate-spin': isSyncing }" />
            {{ isSyncing ? t('settings.sync.syncing') : t('settings.sync.sync_library') }}
          </button>
        </div>
      </template>

      <div class="flex items-center justify-between mb-6">
        <p class="text-sm text-foreground opacity-60">{{ t('settings.folders.description') }}</p>
        <button @click="addFolder" :disabled="isSyncing"
          class="flex items-center gap-2 px-4 py-2 bg-foreground/[0.04] text-foreground rounded-xl hover:bg-foreground/[0.08] transition-all text-sm font-bold disabled:opacity-50 shrink-0">
          <Plus class="w-4 h-4" />
          {{ t('settings.folders.add_folder') }}
        </button>
      </div>

      <div v-if="isLoading" class="py-12 flex justify-center">
        <RotateCcw class="w-8 h-8 animate-spin text-foreground opacity-40" />
      </div>

      <div v-else-if="folders.length === 0"
        class="py-12 text-center border-2 border-dashed border-foreground/[0.06] rounded-2xl">
        <Folder class="w-12 h-12 mx-auto text-foreground opacity-30 mb-4" />
        <p class="text-foreground opacity-60 text-sm font-medium">{{ t('settings.folders.no_folders') }}</p>
      </div>

      <ul v-else class="space-y-2">
        <li v-for="folder in folders" :key="folder.id"
          class="flex items-center justify-between p-4 bg-foreground/[0.02] border border-foreground/[0.04] rounded-xl group transition-all"
          :class="removingFolderIds.includes(folder.id) ? 'opacity-50 pointer-events-none' : 'hover:bg-foreground/[0.04]'">
          <div class="flex items-center gap-4 overflow-hidden">
            <div class="p-2 bg-background rounded-lg shadow-sm">
              <Loader2 v-if="removingFolderIds.includes(folder.id)" class="w-4 h-4 text-foreground opacity-60 animate-spin" />
              <Folder v-else class="w-4 h-4 text-foreground opacity-60" />
            </div>
            <span class="text-sm font-bold truncate" :title="folder.path">
              {{ folder.path }}
              <span v-if="removingFolderIds.includes(folder.id)" class="ml-2 text-xs font-normal opacity-70">
                ({{ t('settings.folders.removing') }})
              </span>
            </span>
          </div>
          <button @click="folderToDelete = folder.id" :disabled="isSyncing || removingFolderIds.includes(folder.id)"
            class="p-2 text-red-500 hover:text-destructive hover:bg-destructive/10 rounded-lg transition-all disabled:opacity-50">
            <Trash2 class="w-4 h-4" />
          </button>
        </li>
      </ul>
    </SettingSection>

    <SettingSection :icon="Tags" :label="t('settings.library.delimiters_title')" variant="panel">
      <p class="text-sm text-foreground opacity-60 mb-6">{{ t('settings.library.delimiters_description') }}</p>

      <div v-if="delimitersDirty"
        class="flex items-start gap-3 mb-6 p-3 rounded-xl bg-amber-500/10 border border-amber-500/20">
        <Info class="w-4 h-4 text-amber-500 shrink-0 mt-0.5" />
        <p class="text-xs text-amber-600 dark:text-amber-400 font-medium leading-relaxed">
          {{ t('settings.delimiters.resync_hint') }}
        </p>
      </div>

      <div class="grid gap-5 grid-cols-1">
        <DelimiterInput :label="t('settings.delimiters.artists')" :model-value="artistDelimiters"
          :disabled="isSyncing" @update:model-value="(v) => onDelimitersChange('artist', v)" />
        <DelimiterInput :label="t('settings.delimiters.album_artists')" :model-value="albumArtistDelimiters"
          :disabled="isSyncing" @update:model-value="(v) => onDelimitersChange('albumArtist', v)" />
        <DelimiterInput :label="t('settings.delimiters.genres')" :model-value="genreDelimiters"
          :disabled="isSyncing" @update:model-value="(v) => onDelimitersChange('genre', v)" />
        <DelimiterInput :label="t('settings.delimiters.composers')" :model-value="composerDelimiters"
          :disabled="isSyncing" @update:model-value="(v) => onDelimitersChange('composer', v)" />
      </div>
    </SettingSection>

    <ConfirmDialog
      :open="!!folderToDelete"
      @cancel="folderToDelete = null"
      :title="t('settings.folders.remove_folder_title')"
      :message="t('settings.folders.remove_folder_confirm')"
      :confirm-label="t('settings.folders.remove_folder')"
      danger
      @confirm="removeFolder"
    />

    <SyncProgressDialog
      :open="showSyncDialog"
      :type="removingFolderIds.length > 0 ? 'deleting' : (syncType || 'sync')"
      :progress="syncProgress"
      :complete="syncComplete"
    />
  </div>
</template>
