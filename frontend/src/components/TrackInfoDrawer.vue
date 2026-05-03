<script setup lang="ts">
import { computed } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { Music, AudioLines, X } from 'lucide-vue-next'
import LazyImg from '@/components/LazyImg.vue'
import { useI18n } from 'vue-i18n'
import { formatTime, buildArtworkUrl, getTrackDisplayTitle } from '@/lib/utils'

const { t } = useI18n()
const store = usePlayerStore()

const track = computed(() => store.trackInfoTrack)

const artworkUrl = computed(() => buildArtworkUrl(track.value?.artwork_key, 'md'))

const isLossless = computed(() => {
  if (!track.value) return false
  const fmt = track.value.format.toLowerCase()
  return ['flac', 'alac', 'wav', 'aiff', 'dsf', 'dff', 'ape'].includes(fmt) || track.value.bitrate >= 1411 || track.value.sample_rate >= 96000
})

const details = computed(() => {
  if (!track.value) return []
  return [
    { label: t('track_info.album'), value: track.value.album?.title || '-', isHyphenAuto: true },
    { label: t('track_info.genre'), value: track.value.raw_genre_names || '-', isHyphenAuto: true },
    { label: t('track_info.year'), value: track.value.year || '-', isHyphenAuto: true },
    { label: t('track_info.composer'), value: track.value.raw_composer_names || '-', isHyphenAuto: true },
    { label: t('track_info.format'), value: track.value.format?.toUpperCase() || '-', isHyphenAuto: true },
    { label: t('track_info.bitrate'), value: track.value.bitrate ? `${Math.round(track.value.bitrate)} kbps` : '-', isHyphenAuto: true },
    { label: t('track_info.sample_rate'), value: track.value.sample_rate ? `${track.value.sample_rate / 1000} kHz` : '-', isHyphenAuto: true },
    { label: t('track_info.duration'), value: formatTime(track.value.duration), isHyphenAuto: true },
    { label: t('track_info.bpm'), value: track.value.bpm || '-', isHyphenAuto: true },
    { label: t('track_info.disc'), value: track.value.total_discs > 1 ? `${track.value.disc_number} / ${track.value.total_discs}` : track.value.disc_number || '-', isHyphenAuto: true },
    { label: t('track_info.track'), value: track.value.total_tracks > 0 ? `${track.value.track_number} / ${track.value.total_tracks}` : track.value.track_number || '-', isHyphenAuto: true },
    { label: t('track_info.play_count'), value: track.value.play_count || '0', isHyphenAuto: true },
    { label: t('track_info.label'), value: track.value.label || '-', isHyphenAuto: true },
    { label: t('track_info.isrc'), value: track.value.isrc || '-', isHyphenAuto: true },
    { label: t('track_info.file_size'), value: formatFileSize(track.value.file_size), isHyphenAuto: true },
    { label: t('track_info.file_path'), value: track.value.path || '-', isHyphenAuto: false },
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
  <div class="h-full flex flex-col bg-background text-foreground">
    <!-- Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-foreground/[0.06] select-none">
      <div class="flex items-center gap-2 font-semibold">
        <AudioLines class="w-4 h-4 text-primary" />
        <span class="text-sm">{{ t('track_info.title') }}</span>
      </div>
      <button
        class="p-1.5 rounded-full hover:bg-foreground/8 transition-colors text-foreground opacity-60 hover:text-foreground"
        @click="store.closeAllDrawers()">
        <X class="w-4 h-4" />
      </button>
    </div>

    <div class="flex-1 overflow-y-auto custom-scrollbar">
      <div v-if="track" class="py-8 px-4 flex flex-col items-center">
        <!-- Artwork -->
        <div
          class="w-48 h-48 rounded-2xl overflow-hidden shadow-2xl ring-1 ring-foreground/10 mb-8 transition-transform hover:scale-[1.02] duration-300">
          <LazyImg v-if="artworkUrl" :src="artworkUrl" class="w-full h-full object-cover" />
          <div v-else class="w-full h-full bg-foreground/5 flex items-center justify-center">
            <Music class="w-16 h-16 text-foreground opacity-30" />
          </div>
        </div>

        <!-- Basic Info -->
        <div class="text-center mb-8 w-full px-4">
          <h1 class="text-lg font-bold mb-1 tracking-tight leading-tight">{{ getTrackDisplayTitle(track) || t('library.unknown_title') }}</h1>
          <p class="text-xs text-foreground opacity-70 font-medium mb-3">
            {{ track.raw_artist_names || t('library.unknown_artist') }}
            <span v-if="track.album?.title" class="mx-1 opacity-30">•</span>
            {{ track.album?.title }}
          </p>

          <!-- Lossless Badge -->
          <div v-if="isLossless"
            class="inline-flex items-center gap-1 px-2 py-0.5 bg-primary/10 text-primary rounded-full border border-primary/20 select-none">
            <AudioLines class="w-3 h-3" />
            <span class="text-[9px] font-bold uppercase tracking-wider">{{ t('track_info.lossless') }}</span>
          </div>
        </div>

        <!-- Details -->
        <div class="w-full max-w-sm px-2">
          <h3 class="text-[10px] font-bold uppercase tracking-widest text-foreground opacity-50 mb-4 text-left">
            {{ t('track_info.details') }}
          </h3>

          <div class="space-y-3">
            <div v-for="detail in details" :key="detail.label"
              class="flex justify-between items-start gap-4 py-1.5 border-b border-foreground/[0.03]">
              <span class="text-[11px] text-foreground opacity-60 font-bold uppercase tracking-tight whitespace-nowrap pt-0.5">
                {{ detail.label }}
              </span>
              <span class="text-xs font-semibold text-right selection:bg-primary/20"
                :class="{ 'hyphens-auto': detail.isHyphenAuto, 'break-all': !detail.isHyphenAuto }">
                {{ detail.value }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="h-full flex items-center justify-center p-8 text-foreground opacity-50 italic">
        {{ t('player.select_track') }}
      </div>
    </div>
  </div>
</template>

<style scoped>
</style>
