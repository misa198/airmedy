<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as LibraryService from '../../bindings/changeme/internal/infra/wails/libraryservice'
import { RotateCcw, Plus, Trash2, Folder, CheckCircle2, AlertCircle, Loader2 } from 'lucide-vue-next'
import type { WatchedFolder, SyncProgress } from '../../bindings/changeme/internal/domain/models'
import { Events } from '@wailsio/runtime'

const folders = ref<WatchedFolder[]>([])
const isSyncing = ref(false)
const isLoading = ref(true)
const message = ref({ text: '', type: '' })
const syncProgress = ref<SyncProgress | null>(null)

const loadFolders = async () => {
  isLoading.value = true
  try {
    const result = await LibraryService.GetWatchedFolders()
    folders.value = result.filter((f): f is WatchedFolder => f !== null)
  } catch (err) {
    console.error('Failed to load folders:', err)
    showMessage('Failed to load folders', 'error')
  } finally {
    isLoading.value = false
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
  showMessage('Library sync complete', 'success')
}

const addFolder = async () => {
  try {
    const path = await LibraryService.SelectFolder()
    if (path) {
      await LibraryService.AddFolder(path)
      await loadFolders()
      showMessage('Folder added successfully', 'success')
    }
  } catch (err) {
    console.error('Failed to add folder:', err)
    showMessage('Failed to add folder', 'error')
  }
}

const removeFolder = async (id: string) => {
  try {
    await LibraryService.RemoveFolder(id)
    await loadFolders()
    showMessage('Folder removed successfully', 'success')
  } catch (err) {
    console.error('Failed to remove folder:', err)
    showMessage('Failed to remove folder', 'error')
  }
}

const syncLibrary = async () => {
  if (isSyncing.value) return
  isSyncing.value = true
  try {
    await LibraryService.SyncAll()
    showMessage('Library sync started', 'success')
  } catch (err) {
    console.error('Sync failed:', err)
    showMessage('Sync failed to start', 'error')
    isSyncing.value = false
  }
}

const showMessage = (text: string, type: string) => {
  message.value = { text, type }
  setTimeout(() => {
    message.value = { text: '', type: '' }
  }, 3000)
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
  <div class="p-8 max-w-4xl mx-auto">
    <div class="flex items-center justify-between mb-8">
      <h1 class="text-3xl font-bold">Settings</h1>
      
      <button 
        @click="syncLibrary" 
        :disabled="isSyncing"
        class="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md hover:opacity-90 transition-all disabled:opacity-50"
      >
        <RotateCcw class="w-4 h-4" :class="{ 'animate-spin': isSyncing }" />
        {{ isSyncing ? 'Syncing...' : 'Sync Library' }}
      </button>
    </div>

    <!-- Sync Progress UI -->
    <div v-if="isSyncing && syncProgress" class="mb-8 p-6 bg-card rounded-xl ring-1 ring-primary/20 animate-in fade-in slide-in-from-top-2">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <Loader2 class="w-5 h-5 animate-spin text-primary" />
          <h2 class="font-semibold">Syncing Music Library...</h2>
        </div>
        <span class="text-sm font-medium text-white/40 bg-white/[0.06] px-2 py-1 rounded">
          {{ syncProgress.current }} / {{ syncProgress.total }}
        </span>
      </div>

      <div class="w-full bg-white/[0.06] rounded-full h-2 mb-3 overflow-hidden">
        <div 
          class="bg-primary h-full transition-all duration-300 ease-out"
          :style="{ width: `${(syncProgress.current / (syncProgress.total || 1)) * 100}%` }"
        ></div>
      </div>
      
      <p class="text-xs text-white/30 truncate italic">
        Importing: {{ syncProgress.path }}
      </p>
    </div>

    <!-- Message Toast (Simple) -->
    <div v-if="message.text" 
      :class="[
        'mb-6 p-4 rounded-lg flex items-center gap-3 border transition-all animate-in fade-in slide-in-from-top-4',
        message.type === 'error' ? 'bg-destructive/10 border-destructive/20 text-destructive' : 'bg-primary/10 border-primary/20 text-primary'
      ]"
    >
      <AlertCircle v-if="message.type === 'error'" class="w-5 h-5" />
      <CheckCircle2 v-else class="w-5 h-5" />
      <p class="text-sm font-medium">{{ message.text }}</p>
    </div>

    <div class="space-y-8">
      <section class="bg-card rounded-xl ring-1 ring-white/[0.06] p-6">
        <div class="flex items-center justify-between mb-6">
          <div>
            <h2 class="text-xl font-semibold mb-1">Music Library</h2>
            <p class="text-sm text-white/40">Manage the folders where Airmedy looks for music.</p>
          </div>
          <button
            @click="addFolder"
            :disabled="isSyncing"
            class="flex items-center gap-2 px-3 py-1.5 bg-white/[0.06] text-white rounded-md hover:bg-white/[0.09] transition-colors text-sm font-medium disabled:opacity-50"
          >
            <Plus class="w-4 h-4" />
            Add Folder
          </button>
        </div>

        <div v-if="isLoading" class="py-12 flex justify-center">
          <RotateCcw class="w-8 h-8 animate-spin text-white/30" />
        </div>
        
        <div v-else-if="folders.length === 0" class="py-12 text-center border border-dashed border-white/[0.08] rounded-lg">
          <Folder class="w-12 h-12 mx-auto text-white/40 mb-4 opacity-20" />
          <p class="text-white/40">No music folders added yet.</p>
          <button @click="addFolder" :disabled="isSyncing" class="mt-4 text-primary hover:underline font-medium text-sm disabled:opacity-50">
            Add your first folder
          </button>
        </div>

        <ul v-else class="divide-y divide-white/[0.04] ring-1 ring-white/[0.06] rounded-lg overflow-hidden bg-background/50">
          <li v-for="folder in folders" :key="folder.id" class="flex items-center justify-between p-4 hover:bg-white/[0.04] transition-colors">
            <div class="flex items-center gap-3 overflow-hidden">
              <Folder class="w-5 h-5 text-white/30 flex-shrink-0" />
              <span class="text-sm font-medium truncate" :title="folder.path">{{ folder.path }}</span>
            </div>
            <button 
              @click="removeFolder(folder.id)"
              :disabled="isSyncing"
              class="p-2 text-white/30 hover:text-destructive hover:bg-destructive/10 rounded-md transition-all disabled:opacity-50"
              title="Remove folder"
            >
              <Trash2 class="w-4 h-4" />
            </button>
          </li>
        </ul>
      </section>

      <section class="bg-card rounded-xl ring-1 ring-white/[0.06] p-6 opacity-50">
        <h2 class="text-xl font-semibold mb-1">Appearance</h2>
        <p class="text-sm text-white/40 mb-4">Theming and UI customizations coming soon.</p>
        <div class="h-10 w-32 bg-secondary rounded-md"></div>
      </section>
    </div>
  </div>
</template>
