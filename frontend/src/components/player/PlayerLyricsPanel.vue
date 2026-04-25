<script setup lang="ts">
import { Mic2, X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import LyricsView from '../PlayerLyrics.vue'

defineProps<{
  lyrics?: string
  loading: boolean
  position: number
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'seek', time: number): void
}>()

const { t } = useI18n()
</script>

<template>
  <div
    class="absolute left-0 h-[85%] my-auto bg-black/30 backdrop-blur-3xl rounded-3xl border border-white/10 flex flex-col overflow-hidden shadow-2xl w-[50cqw] max-w-xl">
    <div class="flex-1 flex flex-col h-full">
      <!-- Lyrics Header -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-white/5">
        <div class="flex items-center gap-2 text-white/80">
          <Mic2 class="w-4 h-4" />
          <span class="text-sm font-semibold uppercase tracking-wider">{{ t('player.lyrics') }}</span>
        </div>
        <button @click="emit('close')"
          class="text-white/40 hover:text-white transition-colors p-1 hover:bg-white/5 rounded-full">
          <X class="w-4 h-4" />
        </button>
      </div>

      <!-- Content Area -->
      <div class="flex-1 overflow-hidden">
        <LyricsView
          :lyrics="lyrics"
          :is-loading="loading"
          :current-position="position"
          class="dark"
          @seek="(time) => emit('seek', time)"
        />
      </div>
    </div>
  </div>
</template>
