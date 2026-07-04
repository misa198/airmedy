<script setup lang="ts">
import { computed, ref } from 'vue'
import { Music } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { Slider } from '@airmedy/ui'
import { usePlayerStore } from '../stores/player'
import { formatTime, getTrackDisplayTitle } from '@airmedy/utils'
import { send } from '../ws'
import { MarqueeText } from '@airmedy/ui'

defineProps<{
  showQueue?: boolean
}>()

const { t } = useI18n()
const store = usePlayerStore()
const track = computed(() => store.currentTrack)
const status = computed(() => store.status)
const isPlaying = computed(() => status.value?.playback_state === 'playing')

const displayTitle = computed(() =>
  track.value ? getTrackDisplayTitle(track.value) : t('player.not_playing')
)

const artworkSrc = computed(() =>
  track.value?.artwork_key ? store.artworkUrl(track.value.artwork_key, 'lg') : ''
)

const artistNames = computed(() => {
  if (!track.value) return ''
  const names = track.value.artists?.map(a => a.name).join(', ')
  if (names) return names
  if (track.value.album?.title) return track.value.album.title
  return t('library.unknown_artist')
})

// Seek with drag-lock + debounce
const isDraggingSeek = ref(false)
const localSeekPct = ref(0)
const seekPct = computed(() => {
  if (isDraggingSeek.value) return localSeekPct.value
  const s = status.value
  if (!s || !s.duration) return 0
  return (s.position / s.duration) * 100
})
let seekTimer: ReturnType<typeof setTimeout> | null = null

const displayPosition = computed(() => {
  if (isDraggingSeek.value) {
    const dur = status.value?.duration ?? 0
    return (localSeekPct.value / 100) * dur
  }
  return status.value?.position ?? 0
})

function onSeekUpdate(pct: number) {
  localSeekPct.value = pct
  isDraggingSeek.value = true
  if (seekTimer) clearTimeout(seekTimer)
  seekTimer = setTimeout(() => {
    const dur = status.value?.duration ?? 0
    if (dur > 0) send({ type: 'seek', position: (pct / 100) * dur })
  }, 300)
}

function onSeekEnd() {
  if (seekTimer) { clearTimeout(seekTimer); seekTimer = null }
  isDraggingSeek.value = false
  const dur = status.value?.duration ?? 0
  if (dur > 0) send({ type: 'seek', position: (localSeekPct.value / 100) * dur })
}
</script>

<template>
  <div class="flex flex-col items-center gap-8 w-full px-4">
    <!-- Artwork -->
    <div
      class="relative aspect-square rounded-2xl overflow-hidden shadow-[0_24px_60px_rgba(0,0,0,0.6)] ring-1 ring-white/10 transition-all duration-700 ease-[cubic-bezier(0.4,0,0.2,1)]"
      :class="[
        !showQueue ? 'w-64 h-64 md:w-80 md:h-80' : 'w-56 h-56 lg:w-70 lg:h-70',
        isPlaying ? 'scale-100' : 'scale-[0.85]'
      ]"
    >
      <img
        v-if="artworkSrc"
        :src="artworkSrc"
        :alt="displayTitle"
        class="w-full h-full object-cover"
        draggable="false"
      />
      <div v-else class="w-full h-full flex items-center justify-center bg-white/5">
        <Music class="w-20 h-20 text-white/10" />
      </div>
    </div>

    <!-- Track info -->
    <div class="text-center w-full max-w-sm px-2">
      <MarqueeText
        :text="displayTitle"
        content-class="text-2xl font-bold text-white leading-tight"
      />
      <MarqueeText
        :text="artistNames"
        content-class="text-lg text-white/60 mt-1.5"
      />
    </div>

    <!-- Progress bar -->
    <div class="w-full max-w-sm">
      <Slider
        :model-value="seekPct"
        :min="0"
        :max="100"
        :step="0.1"
        @update:model-value="onSeekUpdate"
        @mouseup="onSeekEnd"
        @touchend="onSeekEnd"
      />
      <div class="flex justify-between mt-1 text-xs font-semibold text-white/30 tabular-nums">
        <span>{{ formatTime(displayPosition) }}</span>
        <span>{{ formatTime(status?.duration ?? 0) }}</span>
      </div>
    </div>
  </div>
</template>

