<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import { AppWindow, Sun, Moon, Monitor, Languages } from 'lucide-vue-next'
import { Switch } from '@/components/ui/switch'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const { t } = useI18n()
const appStore = useAppStore()

const emit = defineEmits(['message'])

const toggleStartAtLogin = async (enabled: boolean) => {
  try {
    await appStore.updateStartAtLogin(enabled)
    emit('message', { text: t('settings.behavior.updated'), type: 'success' })
  } catch (err) {
    console.error('Failed to save settings:', err)
    emit('message', { text: t('settings.behavior.error'), type: 'error' })
  }
}
</script>

<template>
  <div class="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground/40">
        <AppWindow class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.general.behavior', 'Behavior') }}</h2>
      </div>
      
      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <div class="p-5 flex items-center justify-between">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.behavior.start_at_login') }}</p>
            <p class="text-xs text-foreground/40 mt-1">{{ t('settings.behavior.start_at_login_desc') }}</p>
          </div>
          <Switch 
            :model-value="appStore.startAtLogin"
            @update:model-value="toggleStartAtLogin"
          />
        </div>
      </div>
    </section>

    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground/40">
        <Sun class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.general.appearance') }}</h2>
      </div>
      
      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <div class="p-5 flex items-center justify-between">
          <div class="flex items-center gap-4">
            <div class="p-2 bg-foreground/[0.04] rounded-xl">
              <Sun v-if="appStore.theme === 'light'" class="w-5 h-5 text-foreground/60" />
              <Moon v-else-if="appStore.theme === 'dark'" class="w-5 h-5 text-foreground/60" />
              <Monitor v-else class="w-5 h-5 text-foreground/60" />
            </div>
            <div>
              <p class="text-sm font-semibold">{{ t('settings.appearance.theme') }}</p>
              <p class="text-xs text-foreground/40 mt-1">{{ t('settings.appearance.theme_desc') }}</p>
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
            </SelectContent>
          </Select>
        </div>

        <div class="p-5 flex items-center justify-between">
          <div class="flex items-center gap-4">
            <div class="p-2 bg-foreground/[0.04] rounded-xl">
              <Languages class="w-5 h-5 text-foreground/60" />
            </div>
            <div>
              <p class="text-sm font-semibold">{{ t('settings.appearance.language') }}</p>
              <p class="text-xs text-foreground/40 mt-1">{{ t('settings.appearance.select_language') }}</p>
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
  </div>
</template>
