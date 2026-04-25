<script setup lang="ts">
import { computed } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { Music, AudioLines, X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const store = usePlayerStore()

const track = computed(() => store.trackInfoTrack)

const artworkUrl = computed(() => {
  const key = track.value?.artwork_key
  return key ? `/artwork/${key}` : null
})

const isLossless = computed(() => {
  if (!track.value) return false
  const fmt = track.value.format.toLowerCase()
  return ['flac', 'alac', 'wav', 'aiff', 'dsf', 'dff', 'ape'].includes(fmt)
})

const details = computed(() => {
  if (!track.value) return []
  return [
    { label: t('track_info.album'), value: track.value.album?.title || '-' },
    { label: t('track_info.genre'), value: track.value.raw_genre_names || '-' },
    { label: t('track_info.year'), value: track.value.year || '-' },
    { label: t('track_info.composer'), value: track.value.raw_composer_names || '-' },
    { label: t('track_info.format'), value: track.value.format?.toUpperCase() || '-' },
    { label: t('track_info.bitrate'), value: track.value.bitrate ? `${Math.round(track.value.bitrate)} kbps` : '-' },
    { label: t('track_info.sample_rate'), value: track.value.sample_rate ? `${track.value.sample_rate / 1000} kHz` : '-' },
    { label: t('track_info.file_size'), value: formatFileSize(track.value.file_size) },
  ]
})

function formatFileSize(bytes: number) {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(1)} ${units[unitIndex]}`
}
</script>

<template>
  <div class="h-full flex flex-col bg-background text-foreground select-none">
    <!-- Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-foreground/[0.06]">
      <div class="flex items-center gap-2 font-semibold">
        <AudioLines class="w-4 h-4 text-primary" />
        <span class="text-sm">{{ t('track_info.title') }}</span>
      </div>
      <button 
        class="p-1.5 rounded-full hover:bg-foreground/8 transition-colors text-foreground/40 hover:text-foreground"
        @click="store.isTrackInfoOpen = false"
      >
        <X class="w-4 h-4" />
      </button>
    </div>

    <div class="flex-1 overflow-y-auto custom-scrollbar">
      <div v-if="track" class="py-8 px-4 flex flex-col items-center">
        <!-- Artwork -->
        <div class="w-48 h-48 rounded-2xl overflow-hidden shadow-2xl ring-1 ring-foreground/10 mb-8 transition-transform hover:scale-[1.02] duration-300">
          <img 
            v-if="artworkUrl" 
            :src="artworkUrl" 
            class="w-full h-full object-cover"
          />
          <div v-else class="w-full h-full bg-foreground/5 flex items-center justify-center">
            <Music class="w-16 h-16 text-foreground/10" />
          </div>
        </div>

        <!-- Basic Info -->
        <div class="text-center mb-8 w-full px-4">
          <h1 class="text-lg font-bold mb-1 tracking-tight leading-tight">{{ track.title || t('library.unknown_title') }}</h1>
          <p class="text-xs text-foreground/50 font-medium mb-3">
            {{ track.raw_artist_names || t('library.unknown_artist') }}
            <span v-if="track.album?.title" class="mx-1 opacity-30">•</span>
            {{ track.album?.title }}
          </p>
          
          <!-- Lossless Badge -->
          <div v-if="isLossless" class="inline-flex items-center gap-1 px-2 py-0.5 bg-primary/10 text-primary rounded-full border border-primary/20">
            <AudioLines class="w-3 h-3" />
            <span class="text-[9px] font-bold uppercase tracking-wider">{{ t('track_info.lossless') }}</span>
          </div>
        </div>

        <!-- Details -->
        <div class="w-full max-w-sm px-2">
          <h3 class="text-[10px] font-bold uppercase tracking-widest text-foreground/30 mb-4 text-left">
            {{ t('track_info.details') }}
          </h3>
          
          <div class="space-y-3">
            <div 
              v-for="detail in details" 
              :key="detail.label"
              class="flex justify-between items-baseline gap-4 py-0.5 border-b border-foreground/[0.03]"
            >
              <span class="text-xs text-foreground/40 font-medium whitespace-nowrap">{{ detail.label }}</span>
              <span class="text-xs font-bold text-right leading-relaxed">{{ detail.value }}</span>
            </div>
          </div>
        </div>
      </div>
      
      <div v-else class="h-full flex items-center justify-center p-8 text-foreground/30 italic">
        {{ t('player.select_track') }}
      </div>
    </div>
  </div>
</template>

<style scoped>
@reference "../assets/index.css";

.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  @apply bg-foreground/10 rounded-full;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  @apply bg-foreground/20;
}
</style>
