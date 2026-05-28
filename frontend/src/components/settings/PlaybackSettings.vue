<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { AudioLines, Moon } from 'lucide-vue-next'
import EQPanel from '@/components/EQPanel.vue'
import { Switch } from '@airmedy/ui'
import { useAppStore } from '@/stores/app'

const { t } = useI18n()
const appStore = useAppStore()
</script>

<template>
  <div class="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground opacity-60 select-none">
        <Moon class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.playback.general') }}</h2>
      </div>
      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.playback.prevent_sleep') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.playback.prevent_sleep_desc') }}</p>
          </div>
          <Switch
            :model-value="appStore.preventSleepWhilePlaying"
            @update:model-value="appStore.updatePreventSleepWhilePlaying"
          />
        </div>
      </div>
    </section>

    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground opacity-60 select-none">
        <AudioLines class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.equalizer.title') }}</h2>
      </div>
      <div class="bg-card rounded-2xl border border-foreground/[0.06] p-6">
        <EQPanel />
      </div>
    </section>
  </div>
</template>
