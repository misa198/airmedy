<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import * as SettingsService from '../../../bindings/airmedy/internal/infra/wails/settingsservice'
import { Github, FileText, Folder, ExternalLink, RefreshCw, CheckCircle2 } from 'lucide-vue-next'
import { Browser } from '@wailsio/runtime';

const { t } = useI18n()
const appStore = useAppStore()

// State
const appInfo = ref<any>(null)
const isLoading = ref(true)
const updateError = ref<string | null>(null)

const loadData = async () => {
  isLoading.value = true
  try {
    appInfo.value = await SettingsService.GetAppInfo()
  } catch (err) {
    console.error('Failed to load app info:', err)
  } finally {
    isLoading.value = false
  }
}

const checkUpdate = async () => {
  updateError.value = null
  try {
    await appStore.checkForUpdate()
  } catch (err) {
    console.error('Failed to check for update:', err)
    updateError.value = t('settings.about.check_update_failed')
  }
}

const applyUpdate = async () => {
  updateError.value = null
  try {
    await appStore.applyUpdate()
  } catch (err) {
    console.error('Failed to apply update:', err)
    updateError.value = 'Failed to apply update'
  }
}

const openAppDataFolder = async () => {
  try {
    await SettingsService.OpenAppDataFolder()
  } catch (err) {
    console.error('Failed to open folder:', err)
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="space-y-8 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <div class="text-center py-8">
      <img src="/airmedy.png" alt="Airmedy" class="w-24 h-24 mx-auto mb-6 drop-shadow-2xl" />
      <h2 class="text-3xl font-black mb-2">{{ appInfo?.name || 'Airmedy' }}</h2>
      <p class="text-sm font-bold text-primary mb-2">{{ t("settings.about.version") }} {{ appInfo?.version || '1.0.0' }}
      </p>

      <!-- Update Section -->
      <div class="mt-4 flex flex-col items-center min-h-[40px]">
        <button v-if="!appStore.updateInfo && !appStore.updateApplied" @click="checkUpdate" :disabled="appStore.isCheckingUpdate"
          class="flex items-center gap-2 px-4 py-2 bg-foreground/[0.04] hover:bg-foreground/[0.08] rounded-full text-xs font-bold disabled:opacity-50 transition-colors">
          <div class="w-3 h-3 flex items-center justify-center">
            <RefreshCw class="w-3 h-3" :class="{ 'animate-spin': appStore.isCheckingUpdate }" />
          </div>
          <span class="inline-block min-w-[100px]">{{ appStore.isCheckingUpdate ? t('settings.about.checking') : t('settings.about.check_updates') }}</span>
        </button>

        <div v-if="!appStore.updateInfo && !appStore.updateApplied && appStore.updateChecked && !appStore.isCheckingUpdate" class="flex items-center gap-2 text-foreground/50 py-1">
          <CheckCircle2 class="w-4 h-4" />
          <span class="text-xs font-bold">{{ t('settings.about.up_to_date') }}</span>
        </div>

        <div v-if="appStore.updateInfo && !appStore.updateApplied" class="p-4 bg-primary/10 rounded-2xl border border-primary/20 max-w-sm w-full">
          <p class="text-xs font-bold text-primary mb-3">{{ t('settings.about.new_version_available', { version: appStore.updateInfo.version }) }}</p>
          <div class="flex flex-col gap-2">
            <button @click="applyUpdate" :disabled="appStore.isUpdating"
              class="w-full py-2 bg-primary text-primary-foreground rounded-xl text-xs font-bold shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-[0.98] transition-all disabled:opacity-50 flex items-center justify-center gap-2">
              <div v-if="appStore.isUpdating" class="w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              {{ appStore.isUpdating ? t('settings.about.updating') : t('settings.about.update_now') }}
            </button>
            <button @click="appStore.isUpdateDialogOpen = true"
              class="w-full py-2 bg-foreground/[0.04] hover:bg-foreground/[0.08] rounded-xl text-xs font-bold transition-all">
              {{ t('app.view_details') }}
            </button>
          </div>
        </div>

        <div v-if="appStore.updateApplied" class="flex items-center gap-2 text-green-500 py-2">
          <CheckCircle2 class="w-4 h-4" />
          <span class="text-xs font-bold">{{ t('settings.about.update_applied') }}</span>
        </div>

        <p v-if="updateError" class="text-xs text-red-500 mt-2 font-bold">{{ updateError }}</p>
      </div>

      <p class="text-sm text-foreground opacity-60 max-w-sm mx-auto leading-relaxed mt-4">
        {{ t('settings.about.description') }}
      </p>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <button v-if="appInfo?.github_url" @click="Browser.OpenURL(appInfo.github_url)"
        class="flex items-center justify-between p-5 bg-card rounded-2xl border border-foreground/[0.06] hover:bg-foreground/[0.02] transition-all group">
        <div class="flex items-center gap-4">
          <div class="p-2 bg-foreground/[0.04] rounded-xl group-hover:scale-110 transition-transform">
            <Github class="w-5 h-5 text-foreground opacity-80" />
          </div>
          <span class="text-sm font-bold">{{ t('settings.about.github') }}</span>
        </div>
        <ExternalLink class="w-4 h-4 text-foreground opacity-40" />
      </button>

      <button v-if="appInfo?.license_url" @click="Browser.OpenURL(appInfo.license_url)"
        class="flex items-center justify-between p-5 bg-card rounded-2xl border border-foreground/[0.06] hover:bg-foreground/[0.02] transition-all group">
        <div class="flex items-center gap-4">
          <div class="p-2 bg-foreground/[0.04] rounded-xl group-hover:scale-110 transition-transform">
            <FileText class="w-5 h-5 text-foreground opacity-80" />
          </div>
          <span class="text-sm font-bold">{{ t('settings.about.license') }}</span>
        </div>
        <ExternalLink class="w-4 h-4 text-foreground opacity-40" />
      </button>

      <button @click="openAppDataFolder"
        class="md:col-span-2 flex items-center justify-between p-5 bg-card rounded-2xl border border-foreground/[0.06] hover:bg-foreground/[0.02] transition-all group">
        <div class="flex items-center gap-4">
          <div class="p-2 bg-foreground/[0.04] rounded-xl group-hover:scale-110 transition-transform">
            <Folder class="w-5 h-5 text-foreground opacity-80" />
          </div>
          <span class="text-sm font-bold">{{ t('settings.about.open_data_folder') }}</span>
        </div>
        <ExternalLink class="w-4 h-4 text-foreground opacity-40" />
      </button>
    </div>
  </div>
</template>
