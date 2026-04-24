<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { RotateCcw, Plus, Trash2, Folder, CheckCircle2, AlertCircle, Loader2, Languages, Monitor, Sun, Moon } from 'lucide-vue-next'
import type { WatchedFolder, SyncProgress } from '../../bindings/airmedy/internal/domain/models'
import { Events } from '@wailsio/runtime'
import EQPanel from '@/components/EQPanel.vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const { t } = useI18n()
const appStore = useAppStore()
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
  showMessage(t('settings.sync.sync_complete'), 'success')
}

const addFolder = async () => {
  try {
    const path = await LibraryService.SelectFolder()
    if (path) {
      await LibraryService.AddFolder(path)
      await loadFolders()
      showMessage(t('settings.folders.added_success'), 'success')
    }
  } catch (err) {
    console.error('Failed to add folder:', err)
    showMessage(t('settings.folders.added_error'), 'error')
  }
}

const removeFolder = async (id: string) => {
  try {
    await LibraryService.RemoveFolder(id)
    await loadFolders()
    showMessage(t('settings.folders.removed_success'), 'success')
  } catch (err) {
    console.error('Failed to remove folder:', err)
    showMessage(t('settings.folders.removed_error'), 'error')
  }
}

const syncLibrary = async () => {
  if (isSyncing.value) return
  isSyncing.value = true
  try {
    await LibraryService.SyncAll()
    showMessage(t('settings.sync.sync_started'), 'success')
  } catch (err) {
    console.error('Sync failed:', err)
    showMessage(t('settings.sync.sync_failed'), 'error')
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
      <h1 class="text-3xl font-bold">{{ t('settings.title') }}</h1>
      
      <button 
        @click="syncLibrary" 
        :disabled="isSyncing"
        class="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md hover:opacity-90 transition-all disabled:opacity-50"
      >
        <RotateCcw class="w-4 h-4" :class="{ 'animate-spin': isSyncing }" />
        {{ isSyncing ? t('settings.sync.syncing') : t('settings.sync.sync_library') }}
      </button>
    </div>

    <!-- Sync Progress UI -->
    <div v-if="isSyncing && syncProgress" class="mb-8 p-6 bg-card rounded-xl ring-1 ring-primary/20 animate-in fade-in slide-in-from-top-2">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <Loader2 class="w-5 h-5 animate-spin text-primary" />
          <h2 class="font-semibold">{{ t('settings.sync.syncing_library') }}</h2>
        </div>
        <span class="text-sm font-medium text-foreground/40 bg-foreground/[0.06] px-2 py-1 rounded">
          {{ syncProgress.current }} / {{ syncProgress.total }}
        </span>
      </div>

      <div class="w-full bg-foreground/[0.06] rounded-full h-2 mb-3 overflow-hidden">
        <div 
          class="bg-primary h-full transition-all duration-300 ease-out"
          :style="{ width: `${(syncProgress.current / (syncProgress.total || 1)) * 100}%` }"
        ></div>
      </div>
      
      <p class="text-xs text-foreground/30 truncate italic">
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
      <section class="bg-card rounded-xl ring-1 ring-foreground/[0.06] p-6">
        <div class="flex items-center justify-between mb-6">
          <div>
            <h2 class="text-xl font-semibold mb-1">{{ t('settings.folders.title') }}</h2>
            <p class="text-sm text-foreground/40">{{ t('settings.folders.description') }}</p>
          </div>
          <button
            @click="addFolder"
            :disabled="isSyncing"
            class="flex items-center gap-2 px-3 py-1.5 bg-foreground/[0.06] text-foreground rounded-md hover:bg-foreground/[0.09] transition-colors text-sm font-medium disabled:opacity-50"
          >
            <Plus class="w-4 h-4" />
            {{ t('settings.folders.add_folder') }}
          </button>
        </div>

        <div v-if="isLoading" class="py-12 flex justify-center">
          <RotateCcw class="w-8 h-8 animate-spin text-foreground/30" />
        </div>
        
        <div v-else-if="folders.length === 0" class="py-12 text-center border border-dashed border-foreground/[0.08] rounded-lg">
          <Folder class="w-12 h-12 mx-auto text-foreground/40 mb-4 opacity-20" />
          <p class="text-foreground/40">{{ t('settings.folders.no_folders') }}</p>
          <button @click="addFolder" :disabled="isSyncing" class="mt-4 text-primary hover:underline font-medium text-sm disabled:opacity-50">
            {{ t('settings.folders.add_first') }}
          </button>
        </div>

        <ul v-else class="divide-y divide-foreground/[0.04] ring-1 ring-foreground/[0.06] rounded-lg overflow-hidden bg-background/50">
          <li v-for="folder in folders" :key="folder.id" class="flex items-center justify-between p-4 hover:bg-foreground/[0.04] transition-colors">
            <div class="flex items-center gap-3 overflow-hidden">
              <Folder class="w-5 h-5 text-foreground/30 flex-shrink-0" />
              <span class="text-sm font-medium truncate" :title="folder.path">{{ folder.path }}</span>
            </div>
            <button 
              @click="removeFolder(folder.id)"
              :disabled="isSyncing"
              class="p-2 text-foreground/30 hover:text-destructive hover:bg-destructive/10 rounded-md transition-all disabled:opacity-50"
              :title="t('settings.folders.remove_folder')"
            >
              <Trash2 class="w-4 h-4" />
            </button>
          </li>
        </ul>
      </section>

      <section class="bg-card rounded-xl ring-1 ring-foreground/[0.06] p-6">
        <h2 class="text-xl font-semibold mb-6">{{ t('settings.appearance.title') }}</h2>
        
        <div class="space-y-8">
          <!-- Theme Selection -->
          <div class="flex items-center justify-between max-w-md">
            <div class="flex items-center gap-3">
              <div class="p-2 bg-foreground/[0.04] rounded-lg">
                <Sun v-if="appStore.theme === 'light'" class="w-5 h-5 text-foreground/60" />
                <Moon v-else-if="appStore.theme === 'dark'" class="w-5 h-5 text-foreground/60" />
                <Monitor v-else class="w-5 h-5 text-foreground/60" />
              </div>
              <div>
                <p class="text-sm font-medium">{{ t('settings.appearance.theme', 'Theme') }}</p>
                <p class="text-xs text-foreground/30">{{ t('settings.appearance.theme_desc', 'Select application theme') }}</p>
              </div>
            </div>
            <Select 
              :model-value="appStore.theme" 
              @update:model-value="val => appStore.updateTheme(val as any)"
            >
              <SelectTrigger class="w-[180px] bg-foreground/[0.06] border-0">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="system">
                  <div class="flex items-center gap-2">
                    <Monitor class="w-4 h-4" />
                    <span>{{ t('settings.appearance.system', 'System') }}</span>
                  </div>
                </SelectItem>
                <SelectItem value="light">
                  <div class="flex items-center gap-2">
                    <Sun class="w-4 h-4" />
                    <span>{{ t('settings.appearance.light', 'Light') }}</span>
                  </div>
                </SelectItem>
                <SelectItem value="dark">
                  <div class="flex items-center gap-2">
                    <Moon class="w-4 h-4" />
                    <span>{{ t('settings.appearance.dark', 'Dark') }}</span>
                  </div>
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          <!-- Language Selection -->
          <div class="flex items-center justify-between max-w-md">
            <div class="flex items-center gap-3">
              <div class="p-2 bg-foreground/[0.04] rounded-lg">
                <Languages class="w-5 h-5 text-foreground/60" />
              </div>
              <div>
                <p class="text-sm font-medium">{{ t('settings.appearance.language') }}</p>
                <p class="text-xs text-foreground/30">{{ t('settings.appearance.select_language', 'Select application language') }}</p>
              </div>
            </div>
            <Select 
              :model-value="appStore.language" 
              @update:model-value="val => { appStore.language = val; appStore.updateTheme(appStore.theme) }"
            >
              <SelectTrigger class="w-[180px] bg-foreground/[0.06] border-0">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="en">English</SelectItem>
                <SelectItem value="zh">中文 (Chinese)</SelectItem>
                <SelectItem value="vi">Tiếng Việt (Vietnamese)</SelectItem>
                <SelectItem value="ja">日本語 (Japanese)</SelectItem>
                <SelectItem value="ko">한국어 (Korean)</SelectItem>
                <SelectItem value="de">Deutsch (German)</SelectItem>
                <SelectItem value="fr">Français (French)</SelectItem>
                <SelectItem value="es">Español (Spanish)</SelectItem>
                <SelectItem value="pt">Português (Portuguese)</SelectItem>
                <SelectItem value="it">Italiano (Italian)</SelectItem>
                <SelectItem value="ru">Русский (Russian)</SelectItem>
                <SelectItem value="th">ไทย (Thai)</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </section>

      <section class="bg-card rounded-xl ring-1 ring-foreground/[0.06] p-6">
        <h2 class="text-xl font-semibold mb-4">{{ t('settings.equalizer.title') }}</h2>
        <EQPanel />
      </section>
    </div>
  </div>
</template>
