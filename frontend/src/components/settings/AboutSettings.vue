<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import * as SettingsService from '../../../bindings/airmedy/internal/infra/wails/settingsservice'
import { Github, FileText, Folder, ExternalLink } from 'lucide-vue-next'
import { Browser } from '@wailsio/runtime';

const { t } = useI18n()

// State
const appInfo = ref<any>(null)
const isLoading = ref(true)

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
      <p class="text-sm text-foreground/40 max-w-sm mx-auto leading-relaxed">
        {{ t('settings.about.description') }}
      </p>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <button v-if="appInfo?.github_url" @click="Browser.OpenURL(appInfo.github_url)"
        class="flex items-center justify-between p-5 bg-card rounded-2xl border border-foreground/[0.06] hover:bg-foreground/[0.02] transition-all group">
        <div class="flex items-center gap-4">
          <div class="p-2 bg-foreground/[0.04] rounded-xl group-hover:scale-110 transition-transform">
            <Github class="w-5 h-5 text-foreground/60" />
          </div>
          <span class="text-sm font-bold">{{ t('settings.about.github') }}</span>
        </div>
        <ExternalLink class="w-4 h-4 text-foreground/20" />
      </button>

      <button v-if="appInfo?.license_url" @click="Browser.OpenURL(appInfo.license_url)"
        class="flex items-center justify-between p-5 bg-card rounded-2xl border border-foreground/[0.06] hover:bg-foreground/[0.02] transition-all group">
        <div class="flex items-center gap-4">
          <div class="p-2 bg-foreground/[0.04] rounded-xl group-hover:scale-110 transition-transform">
            <FileText class="w-5 h-5 text-foreground/60" />
          </div>
          <span class="text-sm font-bold">{{ t('settings.about.license') }}</span>
        </div>
        <ExternalLink class="w-4 h-4 text-foreground/20" />
      </button>

      <button @click="openAppDataFolder"
        class="md:col-span-2 flex items-center justify-between p-5 bg-card rounded-2xl border border-foreground/[0.06] hover:bg-foreground/[0.02] transition-all group">
        <div class="flex items-center gap-4">
          <div class="p-2 bg-foreground/[0.04] rounded-xl group-hover:scale-110 transition-transform">
            <Folder class="w-5 h-5 text-foreground/60" />
          </div>
          <span class="text-sm font-bold">{{ t('settings.about.open_data_folder') }}</span>
        </div>
        <ExternalLink class="w-4 h-4 text-foreground/20" />
      </button>
    </div>
  </div>
</template>
