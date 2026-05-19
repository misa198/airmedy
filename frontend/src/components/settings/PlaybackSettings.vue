<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { AudioLines } from 'lucide-vue-next'
import EQPanel from '@/components/EQPanel.vue'

const { t } = useI18n()
</script>

<template>
  <div class="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
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
