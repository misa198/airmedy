<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import { useDeviceStore } from '@/stores/device'
import { AppWindow, Sun, Moon, Monitor, Languages, Circle } from '@lucide/vue'
import { Switch } from '@airmedy/ui'
import RestartModal from '../RestartModal.vue'
import SettingSection from './SettingSection.vue'
import SettingRow from './SettingRow.vue'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@airmedy/ui'

const { t } = useI18n()
const appStore = useAppStore()
const deviceStore = useDeviceStore()
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
    <SettingSection :icon="AppWindow" :label="t('settings.general.behavior', 'Behavior')">
      <SettingRow :title="t('settings.behavior.start_at_login')" :description="t('settings.behavior.start_at_login_desc')">
        <Switch
          :model-value="appStore.startAtLogin"
          @update:model-value="toggleStartAtLogin"
        />
      </SettingRow>

      <SettingRow
        v-if="!deviceStore.isWindows && !deviceStore.isLinux"
        :title="t('settings.behavior.show_tray_icon', 'Show Tray Icon')"
        :description="t('settings.behavior.show_tray_icon_desc', 'Show Airmedy icon in system tray/menu bar')"
      >
        <Switch
          :model-value="appStore.showTrayIcon"
          @update:model-value="toggleShowTrayIcon"
        />
      </SettingRow>

      <SettingRow :title="t('settings.about.check_updates_auto')" :description="t('settings.about.check_updates_auto_desc')">
        <Switch
          :model-value="appStore.autoCheckUpdate"
          @update:model-value="toggleAutoCheckUpdate"
        />
      </SettingRow>
    </SettingSection>

    <SettingSection :icon="Sun" :label="t('settings.general.appearance')">
      <SettingRow :title="t('settings.appearance.theme')" :description="t('settings.appearance.theme_desc')">
        <template #leading>
          <div class="p-2 bg-foreground/[0.04] rounded-xl">
            <Sun v-if="appStore.theme === 'light'" class="w-5 h-5 text-foreground opacity-80" />
            <Moon v-else-if="appStore.theme === 'dark'" class="w-5 h-5 text-foreground opacity-80" />
            <Circle v-else-if="appStore.theme === 'black'" class="w-5 h-5 text-foreground opacity-80" />
            <Monitor v-else class="w-5 h-5 text-foreground opacity-80" />
          </div>
        </template>
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
      </SettingRow>

      <SettingRow :title="t('settings.appearance.language')" :description="t('settings.appearance.select_language')">
        <template #leading>
          <div class="p-2 bg-foreground/[0.04] rounded-xl">
            <Languages class="w-5 h-5 text-foreground opacity-80" />
          </div>
        </template>
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
      </SettingRow>
    </SettingSection>

    <RestartModal v-model:open="showRestartDialog" />
  </div>
</template>
