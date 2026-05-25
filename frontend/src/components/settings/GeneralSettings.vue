<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import { AppWindow, Sun, Moon, Monitor, Languages, Circle } from 'lucide-vue-next'
import { Switch } from '@/components/ui/switch'
import RestartModal from '../RestartModal.vue'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const { t } = useI18n()
const appStore = useAppStore()
const showRestartDialog = ref(false)

const toggleStartAtLogin = async (enabled: boolean) => {
  try {
    await appStore.updateStartAtLogin(enabled)
  } catch (err) {
    console.error('Failed to save settings:', err)
  }
}

const toggleShowTrayIcon = async (enabled: boolean) => {
  try {
    await appStore.updateShowTrayIcon(enabled)
    showRestartDialog.value = true
  } catch (err) {
    console.error('Failed to save settings:', err)
  }
}

const toggleAutoCheckUpdate = async (enabled: boolean) => {
  try {
    await appStore.updateAutoCheckUpdate(enabled)
  } catch (err) {
    console.error('Failed to save settings:', err)
  }
}
</script>

<template>
  <div class="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground opacity-60 select-none">
        <AppWindow class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.general.behavior', 'Behavior') }}</h2>
      </div>
      
      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.behavior.start_at_login') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.behavior.start_at_login_desc') }}</p>
          </div>
          <Switch 
            :model-value="appStore.startAtLogin"
            @update:model-value="toggleStartAtLogin"
          />
        </div>

        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.behavior.show_tray_icon', 'Show Tray Icon') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.behavior.show_tray_icon_desc', 'Show Airmedy icon in system tray/menu bar') }}</p>
          </div>
          <Switch 
            :model-value="appStore.showTrayIcon"
            @update:model-value="toggleShowTrayIcon"
          />
        </div>

        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.about.check_updates_auto') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.about.check_updates_auto_desc') }}</p>
          </div>
          <Switch 
            :model-value="appStore.autoCheckUpdate"
            @update:model-value="toggleAutoCheckUpdate"
          />
        </div>
      </div>
    </section>

    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground opacity-60 select-none">
        <Sun class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.general.appearance') }}</h2>
      </div>
      
      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div class="flex items-center gap-4">
            <div class="p-2 bg-foreground/[0.04] rounded-xl">
              <Sun v-if="appStore.theme === 'light'" class="w-5 h-5 text-foreground opacity-80" />
              <Moon v-else-if="appStore.theme === 'dark'" class="w-5 h-5 text-foreground opacity-80" />
              <Circle v-else-if="appStore.theme === 'black'" class="w-5 h-5 text-foreground opacity-80" />
              <Monitor v-else class="w-5 h-5 text-foreground opacity-80" />
            </div>
            <div>
              <p class="text-sm font-semibold">{{ t('settings.appearance.theme') }}</p>
              <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.appearance.theme_desc') }}</p>
            </div>
          </div>
          <Select 
            :model-value="appStore.theme" 
            @update:model-value="val => appStore.updateTheme(val as any)"
          >
            <SelectTrigger class="w-[140px] bg-foreground/[0.04] border-0 h-9 text-sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="system">{{ t('settings.appearance.system') }}</SelectItem>
              <SelectItem value="light">{{ t('settings.appearance.light') }}</SelectItem>
              <SelectItem value="dark">{{ t('settings.appearance.dark') }}</SelectItem>
              <SelectItem value="black">{{ t('settings.appearance.black') }}</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div class="p-5 flex items-center justify-between gap-x-2">
          <div class="flex items-center gap-4">
            <div class="p-2 bg-foreground/[0.04] rounded-xl">
              <Languages class="w-5 h-5 text-foreground opacity-80" />
            </div>
            <div>
              <p class="text-sm font-semibold">{{ t('settings.appearance.language') }}</p>
              <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.appearance.select_language') }}</p>
            </div>
          </div>
          <Select 
            :model-value="appStore.language" 
            @update:model-value="val => appStore.updateLanguage(val)"
          >
            <SelectTrigger class="w-[140px] bg-foreground/[0.04] border-0 h-9 text-sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="en">English</SelectItem>
              <SelectItem value="zh">中文</SelectItem>
              <SelectItem value="vi">Tiếng Việt</SelectItem>
              <SelectItem value="ja">日本語</SelectItem>
              <SelectItem value="ko">한국어</SelectItem>
              <SelectItem value="de">Deutsch</SelectItem>
              <SelectItem value="fr">Français</SelectItem>
              <SelectItem value="es">Español</SelectItem>
              <SelectItem value="pt">Português</SelectItem>
              <SelectItem value="it">Italiano</SelectItem>
              <SelectItem value="ru">Русский</SelectItem>
              <SelectItem value="th">ไทย</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
    </section>

    <RestartModal v-model:open="showRestartDialog" />
  </div>
</template>
