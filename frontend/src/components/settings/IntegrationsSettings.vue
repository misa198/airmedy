<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import { Input, Switch } from '@airmedy/ui'
import { Events } from '@wailsio/runtime'
import { Blocks, FileMusic, Folder, FolderTree, ImagePlay, MicVocal, Music } from 'lucide-vue-next'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import * as LastFmService from '../../../bindings/airmedy/internal/infra/wails/lastfmservice'
import * as LibraryService from '../../../bindings/airmedy/internal/infra/wails/libraryservice'

const { t } = useI18n()
const appStore = useAppStore()

const chooseLyricsFolder = async () => {
  try {
    const path = await LibraryService.SelectFolder()
    if (path) await appStore.updateLyricsFolderPath(path)
  } catch (err) {
    console.error('Failed to select lyrics folder:', err)
  }
}

// Lyrics subfolder (next to track)
const subfolderInput = ref(appStore.lyricsSubfolderName)
const subfolderError = ref('')
const INVALID_SUBFOLDER = /[/\\:*?"<>|]/

const validateSubfolder = (name: string): boolean => {
  const trimmed = name.trim()
  if (trimmed === '' || trimmed === '.' || trimmed === '..' || trimmed.includes('..') || INVALID_SUBFOLDER.test(trimmed)) {
    subfolderError.value = t('settings.integrations.lyrics_subfolder_invalid', 'Enter a single folder name (no slashes or "..")')
    return false
  }
  subfolderError.value = ''
  return true
}

const onSubfolderInput = (v: unknown) => {
  subfolderInput.value = String(v)
  if (subfolderError.value) validateSubfolder(subfolderInput.value)
}

const subfolderChanged = computed(() => subfolderInput.value.trim() !== appStore.lyricsSubfolderName)

const saveSubfolder = async () => {
  const trimmed = subfolderInput.value.trim()
  if (!validateSubfolder(trimmed)) return
  subfolderInput.value = trimmed
  await appStore.updateLyricsSubfolderName(trimmed)
}

const subfolderExample = computed(() =>
  t('settings.integrations.lyrics_subfolder_example', { name: subfolderInput.value.trim() || 'lyrics' }),
)

const toggleSubfolder = async (enabled: boolean) => {
  await appStore.updateLyricsSubfolderEnabled(enabled)
}

// Keep the local input in sync when settings load/change and the field is untouched
// (input still equals the last value pushed from the store).
const lastSyncedSubfolder = ref(appStore.lyricsSubfolderName)
watch(() => appStore.lyricsSubfolderName, (name) => {
  if (subfolderInput.value === lastSyncedSubfolder.value) subfolderInput.value = name
  lastSyncedSubfolder.value = name
})

const lastfmStatus = ref({ connected: false, username: '', avatar_url: '' })
const lastfmAvatar = ref('')
const isConnecting = ref(false)

const fetchLastFmStatus = async () => {
  try {
    lastfmStatus.value = await LastFmService.GetStatus()
    if (lastfmStatus.value.connected) {
      appStore.updateLastFmUsername(lastfmStatus.value.username)
      if (lastfmStatus.value.avatar_url) {
        lastfmAvatar.value = lastfmStatus.value.avatar_url
      }
    } else {
      appStore.updateLastFmUsername('')
      lastfmAvatar.value = ''
    }
  } catch (err) {
    console.error('Failed to fetch Last.fm status:', err)
  }
}

const connectLastFm = async () => {
  isConnecting.value = true
  try {
    await LastFmService.Connect()
    // Flow continues via deep link -> backend event -> frontend listener
  } catch (err) {
    console.error('Failed to start Last.fm connection:', err)
    isConnecting.value = false
  }
}

const disconnectLastFm = async () => {
  try {
    await LastFmService.Disconnect()
    lastfmAvatar.value = ''
    await fetchLastFmStatus()
  } catch (err) {
    console.error('Failed to disconnect from Last.fm:', err)
  }
}

onMounted(() => {
  fetchLastFmStatus()

  // Listen for successful deep link connection from backend
  const unoff = Events.On('lastfm:connected', (e) => {
    const username = e.data as string
    lastfmStatus.value = { connected: true, username, avatar_url: '' }
    isConnecting.value = false
    appStore.updateLastFmUsername(username)
  })

  const unoffAvatar = Events.On('lastfm:avatar', (e) => {
    lastfmAvatar.value = e.data as string
  })

  onUnmounted(() => {
    unoff()
    unoffAvatar()
  })
})
</script>

<template>
  <div class="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground opacity-60 select-none">
        <FileMusic class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.integrations.scrobbling', 'Scrobbling') }}
        </h2>
      </div>

      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div class="flex items-center gap-4">
            <div v-if="lastfmAvatar && lastfmStatus.connected" class="relative">
              <img :src="lastfmAvatar" class="w-10 h-10 rounded-xl object-cover border border-foreground/[0.06]" />
              <div class="absolute -bottom-1 -right-1 p-0.5 bg-[#D31F27] rounded-md">
                <Music class="w-2.5 h-2.5 text-white" />
              </div>
            </div>
            <div v-else class="p-2 bg-[#D31F27]/[0.08] rounded-xl">
              <Blocks class="w-5 h-5 text-[#D31F27]" />
            </div>
            <div>
              <p class="text-sm font-semibold">Last.fm</p>
              <p v-if="lastfmStatus.connected" class="text-xs text-foreground opacity-60 mt-1">
                {{ t('settings.lastfm.connected_as') }} <span class="font-bold opacity-100 text-foreground">{{
                  lastfmStatus.username }}</span>
              </p>
              <p v-else class="text-xs text-foreground opacity-60 mt-1">
                {{ t('settings.lastfm.scrobble_desc') }}
              </p>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <button v-if="!lastfmStatus.connected"
              class="h-9 px-4 rounded-xl font-semibold border border-foreground/[0.08] hover:bg-foreground/[0.04] text-sm transition-colors disabled:opacity-50"
              :disabled="isConnecting" @click="connectLastFm">
              {{ isConnecting ? t('settings.lastfm.connecting') : t('settings.lastfm.connect') }}
            </button>

            <button v-else
              class="h-9 px-4 rounded-xl font-semibold border border-foreground/[0.08] hover:bg-destructive/10 hover:text-destructive hover:border-destructive/20 text-sm transition-colors"
              @click="disconnectLastFm">
              {{ t('settings.lastfm.disconnect') }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground opacity-60 select-none">
        <ImagePlay class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.integrations.artwork', 'Artwork') }}
        </h2>
      </div>

      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.integrations.online_artist_artwork') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">
              {{ t('settings.integrations.online_artist_artwork_desc') }}
            </p>
          </div>
          <Switch :model-value="appStore.useOnlineArtistArtwork"
            @update:model-value="appStore.updateUseOnlineArtistArtwork" />
        </div>
      </div>
    </section>

    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground opacity-60 select-none">
        <MicVocal class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.integrations.lyrics', 'Lyrics') }}
        </h2>
      </div>

      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.integrations.enable_lrclib', 'LRCLIB.NET Lyrics') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">
              {{ t('settings.integrations.enable_lrclib_desc', 'Fetch lyrics from LRCLIB.NET') }}
            </p>
          </div>
          <Switch :model-value="appStore.enableLrclib" @update:model-value="appStore.updateEnableLrclib" />
        </div>
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.integrations.enable_kugou', 'KuGou Lyrics') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">
              {{ t('settings.integrations.enable_kugou_desc', 'Fetch lyrics from KuGou Music') }}
            </p>
          </div>
          <Switch :model-value="appStore.enableKugou" @update:model-value="appStore.updateEnableKugou" />
        </div>
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">
              {{ t('settings.integrations.prefer_local_lyrics', 'Prefer Local Lyrics') }}
            </p>
            <p class="text-xs text-foreground opacity-60 mt-1">
              {{ t('settings.integrations.prefer_local_lyrics_desc', 'Use local lyric files or embedded lyrics when available') }}
            </p>
          </div>
          <Switch :model-value="appStore.preferLocalLyrics"
            @update:model-value="appStore.updatePreferLocalLyrics" />
        </div>
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">
              {{ t('settings.integrations.lyrics_subfolder', 'Lyrics Subfolder Next to Track') }}
            </p>
            <p class="text-xs text-foreground opacity-60 mt-1">
              {{ t('settings.integrations.lyrics_subfolder_desc', 'Also look up .lrc/.txt files in a subfolder next to each track') }}
            </p>
          </div>
          <Switch :model-value="appStore.lyricsSubfolderEnabled" @update:model-value="toggleSubfolder" />
        </div>
        <div v-if="appStore.lyricsSubfolderEnabled" class="p-5 space-y-3">
          <div class="flex items-center gap-3">
            <div class="p-2 bg-background rounded-lg shadow-sm shrink-0">
              <FolderTree class="w-4 h-4 text-foreground opacity-60" />
            </div>
            <Input type="text" class="flex-1" :model-value="subfolderInput"
              :placeholder="t('settings.integrations.lyrics_subfolder_name', 'Folder name (e.g. lyrics)')"
              @update:model-value="onSubfolderInput" @keyup.enter="saveSubfolder" />
            <button @click="saveSubfolder" :disabled="!subfolderChanged"
              class="shrink-0 px-4 py-2 bg-primary text-primary-foreground rounded-xl hover:opacity-90 transition-all text-sm font-bold disabled:opacity-50">
              {{ t('common.save', 'Save') }}
            </button>
          </div>
          <p v-if="subfolderError" class="text-xs text-red-500 pl-11">{{ subfolderError }}</p>
          <template v-else>
            <p class="text-xs text-foreground opacity-50 pl-11 truncate">{{ subfolderExample }}</p>
            <p class="text-xs text-foreground opacity-40 pl-11">
              {{ t('settings.integrations.lyrics_subfolder_case', 'Folder name is matched ignoring upper/lowercase.') }}
            </p>
          </template>
        </div>
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">
              {{ t('settings.integrations.lyrics_folder', 'Dedicated Lyrics Folder') }}
            </p>
            <p class="text-xs text-foreground opacity-60 mt-1">
              {{ t('settings.integrations.lyrics_folder_desc', 'Also look up .lrc/.txt files (matched by track name) in a chosen folder') }}
            </p>
          </div>
          <Switch :model-value="appStore.lyricsFolderEnabled"
            @update:model-value="appStore.updateLyricsFolderEnabled" />
        </div>
        <div v-if="appStore.lyricsFolderEnabled" class="p-5 flex items-center justify-between gap-x-4">
          <div class="flex items-center gap-3 overflow-hidden">
            <div class="p-2 bg-background rounded-lg shadow-sm shrink-0">
              <Folder class="w-4 h-4 text-foreground opacity-60" />
            </div>
            <span class="text-sm font-medium truncate" :title="appStore.lyricsFolderPath">
              {{ appStore.lyricsFolderPath || t('settings.integrations.lyrics_folder_none', 'No folder selected') }}
            </span>
          </div>
          <button @click="chooseLyricsFolder"
            class="shrink-0 px-4 py-2 bg-foreground/[0.04] text-foreground rounded-xl hover:bg-foreground/[0.08] transition-all text-sm font-bold">
            {{ t('settings.integrations.lyrics_folder_choose', 'Choose Folder') }}
          </button>
        </div>
      </div>
    </section>
  </div>
</template>
