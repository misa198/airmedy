<script setup lang="ts">
import { Volume2, VolumeX } from 'lucide-vue-next'
import { Slider } from '@/components/ui/slider'
import { useI18n } from 'vue-i18n'

defineProps<{
  volume: number
  muted: boolean
}>()

const emit = defineEmits<{
  (e: 'update:volume', value: number): void
  (e: 'update:muted', value: boolean): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center gap-3 w-full max-w-[220px]">
    <button class="text-white/80 hover:text-white/80 transition-colors flex-shrink-0"
      @click="emit('update:muted', !muted)" :title="muted ? t('player.unmute') : t('player.mute')">
      <VolumeX v-if="muted" class="w-4 h-4" />
      <Volume2 v-else class="w-4 h-4" />
    </button>
    <Slider :model-value="muted ? 0 : volume" :min="0" :max="1" :step="0.01" class="flex-1"
      @update:model-value="(v) => emit('update:volume', v)" />
  </div>
</template>
