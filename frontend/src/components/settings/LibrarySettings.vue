<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import * as LibraryService from '../../../bindings/airmedy/internal/infra/wails/libraryservice'
import { RotateCcw, Plus, Trash2, Folder, Loader2 } from 'lucide-vue-next'
import type { WatchedFolder, SyncProgress } from '../../../bindings/airmedy/internal/domain/models'
import { Events } from '@wailsio/runtime'

const { t } = useI18n()
const emit = defineEmits(['message'])

// State
const folders = ref<WatchedFolder[]>([])
const isSyncing = ref(false)
const isLoading = ref(true)
const syncProgress = ref<SyncProgress | null>(null)

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
      emit('message', { text: t('settings.folders.added_success'), type: 'success' })
    }
  } catch (err) {
    console.error('Failed to add folder:', err)
    emit('message', { text: t('settings.folders.added_error'), type: 'error' })
  }
}

const removeFolder = async (id: string) => {
  try {
    await LibraryService.RemoveFolder(id)
    await loadFolders()
    emit('message', { text: t('settings.folders.removed_success'), type: 'success' })
  } catch (err) {
    console.error('Failed to remove folder:', err)
    emit('message', { text: t('settings.folders.removed_error'), type: 'error' })
  }
}

const syncLibrary = async () => {
  if (isSyncing.value) return
  isSyncing.value = true
  try {
    await LibraryService.SyncAll()
    emit('message', { text: t('settings.sync.sync_started'), type: 'success' })
  } catch (err) {
    console.error('Sync failed:', err)
    emit('message', { text: t('settings.sync.sync_failed'), type: 'error' })
    isSyncing.value = false
  }
}

const handleSyncStarted = (ev: Events.WailsEvent) => {
  const data = ev.data as any
  isSyncing.value = true
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
  syncProgress.value = null
  emit('message', { text: t('settings.sync.sync_complete'), type: 'success' })
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
    <div class="flex items-center justify-between mb-4">
      <h2 class="text-xl font-bold">{{ t('settings.library.title') }}</h2>
      <button 
        @click="syncLibrary" 
        :disabled="isSyncing"
        class="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-xl hover:opacity-90 transition-all disabled:opacity-50 text-sm font-bold shadow-lg shadow-primary/20"
      >
        <RotateCcw class="w-4 h-4" :class="{ 'animate-spin': isSyncing }" />
        {{ isSyncing ? t('settings.sync.syncing') : t('settings.sync.sync_library') }}
      </button>
    </div>

    <!-- Sync Progress -->
    <div v-if="isSyncing && syncProgress" class="p-6 bg-primary/5 rounded-2xl border border-primary/10 mb-8">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <Loader2 class="w-5 h-5 animate-spin text-primary" />
          <h3 class="font-bold">{{ t('settings.sync.syncing_library') }}</h3>
        </div>
        <span class="text-xs font-bold bg-primary/10 text-primary px-2 py-1 rounded-lg">
          {{ syncProgress.current }} / {{ syncProgress.total }}
        </span>
      </div>
      <div class="w-full bg-foreground/[0.06] rounded-full h-2 mb-3 overflow-hidden">
        <div 
          class="bg-primary h-full transition-all duration-300 ease-out"
          :style="{ width: `${(syncProgress.current / (syncProgress.total || 1)) * 100}%` }"
        ></div>
      </div>
      <p class="text-[10px] text-foreground/40 truncate font-medium">
        Importing: {{ syncProgress.path }}
      </p>
    </div>

    <section class="bg-card rounded-2xl border border-foreground/[0.06] p-6">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h3 class="text-lg font-bold mb-1">{{ t('settings.folders.title') }}</h3>
          <p class="text-sm text-foreground/40">{{ t('settings.folders.description') }}</p>
        </div>
        <button
          @click="addFolder"
          :disabled="isSyncing"
          class="flex items-center gap-2 px-4 py-2 bg-foreground/[0.04] text-foreground rounded-xl hover:bg-foreground/[0.08] transition-all text-sm font-bold disabled:opacity-50"
        >
          <Plus class="w-4 h-4" />
          {{ t('settings.folders.add_folder') }}
        </button>
      </div>

      <div v-if="isLoading" class="py-12 flex justify-center">
        <RotateCcw class="w-8 h-8 animate-spin text-foreground/20" />
      </div>
      
      <div v-else-if="folders.length === 0" class="py-12 text-center border-2 border-dashed border-foreground/[0.06] rounded-2xl">
        <Folder class="w-12 h-12 mx-auto text-foreground/10 mb-4" />
        <p class="text-foreground/40 text-sm font-medium">{{ t('settings.folders.no_folders') }}</p>
      </div>

      <ul v-else class="space-y-2">
        <li v-for="folder in folders" :key="folder.id" class="flex items-center justify-between p-4 bg-foreground/[0.02] border border-foreground/[0.04] rounded-xl group transition-all hover:bg-foreground/[0.04]">
          <div class="flex items-center gap-4 overflow-hidden">
            <div class="p-2 bg-background rounded-lg shadow-sm">
              <Folder class="w-4 h-4 text-foreground/40" />
            </div>
            <span class="text-sm font-bold truncate" :title="folder.path">{{ folder.path }}</span>
          </div>
          <button 
            @click="removeFolder(folder.id)"
            :disabled="isSyncing"
            class="p-2 text-foreground/20 hover:text-destructive hover:bg-destructive/10 rounded-lg transition-all disabled:opacity-50"
          >
            <Trash2 class="w-4 h-4" />
          </button>
        </li>
      </ul>
    </section>
  </div>
</template>
