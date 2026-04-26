<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import * as LibraryService from '../../../bindings/airmedy/internal/infra/wails/libraryservice'
import { RotateCcw, Plus, Trash2, Folder, Loader2, CheckCircle2, DatabaseZap } from 'lucide-vue-next'
import type { WatchedFolder, SyncProgress } from '../../../bindings/airmedy/internal/domain/models'
import { Events } from '@wailsio/runtime'

const { t } = useI18n()

// State
const folders = ref<WatchedFolder[]>([])
const isSyncing = ref(false)
const syncType = ref<'sync' | 'optimize' | null>(null)
const isLoading = ref(true)
const syncProgress = ref<SyncProgress | null>(null)
const syncComplete = ref(false)

const loadFolders = async () => {
  isLoading.value = true
  try {
    const result = await LibraryService.GetWatchedFolders()
    folders.value = result.filter((f): f is WatchedFolder => f !== null)
  } catch (err) {
    console.error('Failed to load folders:', err)
  } finally {
    isLoading.value = false
  }
}

const addFolder = async () => {
  try {
    const path = await LibraryService.SelectFolder()
    if (path) {
      await LibraryService.AddFolder(path)
      await loadFolders()
    }
  } catch (err) {
    console.error('Failed to add folder:', err)
  }
}

const removeFolder = async (id: string) => {
  try {
    await LibraryService.RemoveFolder(id)
    await loadFolders()
  } catch (err) {
    console.error('Failed to remove folder:', err)
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

const handleSyncFinished = () => {
  isSyncing.value = false
  syncComplete.value = true
}

onMounted(() => {
  loadFolders()
  Events.On('library:sync-started', handleSyncStarted)
  Events.On('library:sync-progress', handleSyncProgress)
  Events.On('library:sync-finished', handleSyncFinished)
})

onUnmounted(() => {
  Events.Off('library:sync-started', 'library:sync-progress', 'library:sync-finished')
})
</script>

<template>
  <div class="space-y-8 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <!-- Sync Header -->
    <div class="flex items-center justify-between mb-4 select-none">
      <h2 class="text-xl font-bold">{{ t('settings.library.title') }}</h2>
      <div class="flex gap-3">
        <button @click="optimizeSearch" :disabled="isSyncing"
          class="flex items-center gap-2 px-4 py-2 bg-foreground/[0.04] text-foreground rounded-xl hover:bg-foreground/[0.08] transition-all disabled:opacity-50 text-sm font-bold">
          <DatabaseZap v-if="!isSyncing" class="w-4 h-4" />
          <RotateCcw v-else class="w-4 h-4" :class="{ 'animate-spin': isSyncing }" />
          {{ isSyncing ? t('settings.sync.syncing') : t('settings.sync.optimize_search') }}
        </button>
        <button @click="syncLibrary" :disabled="isSyncing"
          class="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-xl hover:opacity-90 transition-all disabled:opacity-50 text-sm font-bold shadow-lg shadow-primary/20">
          <RotateCcw class="w-4 h-4" :class="{ 'animate-spin': isSyncing }" />
          {{ isSyncing ? t('settings.sync.syncing') : t('settings.sync.sync_library') }}
        </button>
      </div>
    </div>

    <!-- Sync Progress -->
    <div v-if="syncProgress" class="p-6 bg-primary/5 rounded-2xl border border-primary/10 mb-8">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <CheckCircle2 v-if="syncComplete" class="w-5 h-5 text-primary" />
          <Loader2 v-else class="w-5 h-5 animate-spin text-primary" />
          <h3 class="font-bold">
            {{ syncComplete ? t('settings.sync.sync_complete') : t('settings.sync.syncing_library') }}
          </h3>
        </div>
        <span class="text-xs font-bold bg-primary/10 text-primary px-2 py-1 rounded-lg">
          <template v-if="syncType === 'optimize'">
            {{ Math.round((syncProgress.current / (syncProgress.total || 1)) * 100) }}%
          </template>
          <template v-else>
            {{ syncProgress.current }} / {{ syncProgress.total }}
          </template>
        </span>
      </div>
      <template v-if="isSyncing">
        <div class="w-full bg-foreground/[0.06] rounded-full h-2 mb-3 overflow-hidden">
          <div class="bg-primary h-full transition-all duration-300 ease-out"
            :style="{ width: `${(syncProgress.current / (syncProgress.total || 1)) * 100}%` }"></div>
        </div>
        <p class="text-[10px] text-foreground/40 truncate font-medium">
          Importing: {{ syncProgress.path }}
        </p>
      </template>
    </div>

    <section class="bg-card rounded-2xl border border-foreground/[0.06] p-6">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h3 class="text-lg font-bold mb-1">{{ t('settings.folders.title') }}</h3>
          <p class="text-sm text-foreground/40">{{ t('settings.folders.description') }}</p>
        </div>
        <button @click="addFolder" :disabled="isSyncing"
          class="flex items-center gap-2 px-4 py-2 bg-foreground/[0.04] text-foreground rounded-xl hover:bg-foreground/[0.08] transition-all text-sm font-bold disabled:opacity-50">
          <Plus class="w-4 h-4" />
          {{ t('settings.folders.add_folder') }}
        </button>
      </div>

      <div v-if="isLoading" class="py-12 flex justify-center">
        <RotateCcw class="w-8 h-8 animate-spin text-foreground/20" />
      </div>

      <div v-else-if="folders.length === 0"
        class="py-12 text-center border-2 border-dashed border-foreground/[0.06] rounded-2xl">
        <Folder class="w-12 h-12 mx-auto text-foreground/10 mb-4" />
        <p class="text-foreground/40 text-sm font-medium">{{ t('settings.folders.no_folders') }}</p>
      </div>

      <ul v-else class="space-y-2">
        <li v-for="folder in folders" :key="folder.id"
          class="flex items-center justify-between p-4 bg-foreground/[0.02] border border-foreground/[0.04] rounded-xl group transition-all hover:bg-foreground/[0.04]">
          <div class="flex items-center gap-4 overflow-hidden">
            <div class="p-2 bg-background rounded-lg shadow-sm">
              <Folder class="w-4 h-4 text-foreground/40" />
            </div>
            <span class="text-sm font-bold truncate" :title="folder.path">{{ folder.path }}</span>
          </div>
          <button @click="removeFolder(folder.id)" :disabled="isSyncing"
            class="p-2 text-foreground/20 hover:text-destructive hover:bg-destructive/10 rounded-lg transition-all disabled:opacity-50">
            <Trash2 class="w-4 h-4" />
          </button>
        </li>
      </ul>
    </section>
  </div>
</template>
