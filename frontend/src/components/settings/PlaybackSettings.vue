<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import { Mic2 } from 'lucide-vue-next'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const { t } = useI18n()
const appStore = useAppStore()
</script>

<template>
  <div class="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground opacity-60 select-none">
        <Mic2 class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.lyrics.title', 'Lyrics') }}</h2>
      </div>

      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.lyrics.lrclib_mode', 'LRCLIB.NET') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.lyrics.lrclib_mode_desc', 'Control when to fetch lyrics from LRCLIB.NET') }}</p>
          </div>
          <Select
            :model-value="appStore.lrclibMode"
            @update:model-value="val => appStore.updateLrclibMode(val as any)"
          >
            <SelectTrigger class="w-[180px] bg-foreground/[0.04] border-0 h-9 text-sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="off">{{ t('settings.lyrics.lrclib_off', 'Off (metadata only)') }}</SelectItem>
              <SelectItem value="prefer_metadata">{{ t('settings.lyrics.lrclib_prefer_metadata', 'Prefer metadata') }}</SelectItem>
              <SelectItem value="prefer_lrclib">{{ t('settings.lyrics.lrclib_prefer_lrclib', 'Prefer lrclib') }}</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
    </section>
  </div>
</template>
