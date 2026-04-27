<script setup lang="ts">
import { Music } from 'lucide-vue-next'
import LazyImg from '@/components/LazyImg.vue'

defineProps<{
  artworkUrl?: string | null
  trackTitle: string
  isPlaying: boolean
  showRightColumn: boolean
}>()
</script>

<template>
  <div
    class="rounded-2xl shadow-[0_24px_60px_rgba(0,0,0,0.6)] overflow-hidden flex-shrink-0 ring-1 ring-white/8 transition-all duration-700 ease-[cubic-bezier(0.4,0,0.2,1)]"
    :class="[
      !showRightColumn ? 'w-64 h-64 md:w-80 md:h-80' : 'w-56 h-56 md:w-70 md:h-70',
      isPlaying ? 'scale-100' : 'scale-[0.80]'
    ]">
    <LazyImg v-if="artworkUrl" :src="artworkUrl" :alt="trackTitle"
      class="w-full h-full object-cover" />
    <div v-else class="w-full h-full bg-white/5 flex items-center justify-center">
      <Music class="w-20 h-20 text-white/15" />
    </div>
  </div>
</template>
