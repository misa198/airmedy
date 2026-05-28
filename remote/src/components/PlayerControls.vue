<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Repeat,
  Repeat1,
  Shuffle,
  Volume2,
  VolumeX,
} from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { Slider } from '@airmedy/ui'
import { send } from '../ws'
import { usePlayerStore } from '../stores/player'

const { t } = useI18n()
const store = usePlayerStore()
const status = computed(() => store.status)
const isPlaying = computed(() => status.value?.playback_state === 'playing')
const isMuted = computed(() => status.value?.muted ?? false)
const volume = computed(() => status.value?.volume ?? 1)
const shuffle = computed(() => status.value?.shuffle ?? false)
const repeat = computed(() => status.value?.repeat_mode ?? 'off')

function togglePlay() { send({ type: 'toggle_pause' }) }
function next() { send({ type: 'next' }) }
function prev() { send({ type: 'prev' }) }
function toggleMute() { send({ type: 'set_muted', muted: !isMuted.value }) }
function toggleShuffle() { send({ type: 'set_shuffle', enabled: !shuffle.value }) }
function cycleRepeat() {
  const modes = ['off', 'all', 'one'] as const
  const idx = modes.indexOf(repeat.value)
  send({ type: 'set_repeat', mode: modes[(idx + 1) % modes.length] })
}

// Volume with drag-lock + debounce
const isDraggingVolume = ref(false)
const localVolume = ref(1)
const displayVolume = computed(() =>
  isDraggingVolume.value ? localVolume.value : (isMuted.value ? 0 : volume.value)
)
let volumeTimer: ReturnType<typeof setTimeout> | null = null

function onVolumeUpdate(v: number) {
  localVolume.value = v
  isDraggingVolume.value = true
  if (volumeTimer) clearTimeout(volumeTimer)
  volumeTimer = setTimeout(() => {
    isDraggingVolume.value = false
    send({ type: 'set_volume', volume: v })
  }, 150)
}
</script>

<template>
  <div class="flex flex-col gap-8 w-full max-w-sm px-4">
    <!-- Main controls -->
    <div class="flex items-center justify-between w-full max-w-xs mx-auto">
      <!-- Shuffle -->
      <button
        @click="toggleShuffle"
        class="p-2 transition-colors"
        :class="shuffle ? 'text-white' : 'text-white/30 hover:text-white hover:opacity-80'"
        :title="t('player.shuffle')"
      >
        <Shuffle class="w-5 h-5" />
      </button>

      <!-- Previous -->
      <button
        @click="prev"
        class="p-2 text-white opacity-80 hover:text-white transition-colors"
        :title="t('player.previous')"
      >
        <SkipBack class="w-7 h-7 fill-current" />
      </button>

      <!-- Play/Pause -->
      <button
        @click="togglePlay"
        class="w-16 h-16 rounded-full bg-white text-[#0A0A0A] flex items-center justify-center shadow-[0_12px_40px_rgba(255,255,255,0.2)] active:scale-90 transition-all hover:scale-105"
        :title="isPlaying ? t('player.pause') : t('player.play')"
      >
        <Pause v-if="isPlaying" class="w-7 h-7 fill-current" />
        <Play v-else class="w-7 h-7 fill-current ml-1" />
      </button>

      <!-- Next -->
      <button
        @click="next"
        class="p-2 text-white opacity-80 hover:text-white transition-colors"
        :title="t('player.next')"
      >
        <SkipForward class="w-7 h-7 fill-current" />
      </button>

      <!-- Repeat -->
      <button
        @click="cycleRepeat"
        class="p-2 transition-colors relative"
        :class="repeat !== 'off' ? 'text-white' : 'text-white/30 hover:text-white opacity-80'"
        :title="t('player.repeat')"
      >
        <Repeat1 v-if="repeat === 'one'" class="w-5 h-5" />
        <Repeat v-else class="w-5 h-5" />
      </button>
    </div>

    <!-- Volume -->
    <div class="flex items-center gap-4 max-w-[280px] mx-auto w-full">
      <button
        @click="toggleMute"
        class="text-white/40 hover:text-white transition-opacity shrink-0"
        :title="isMuted ? t('player.unmute') : t('player.mute')"
      >
        <VolumeX v-if="isMuted || volume === 0" class="w-4 h-4" />
        <Volume2 v-else class="w-4 h-4" />
      </button>
      <Slider
        :model-value="displayVolume"
        :min="0"
        :max="1"
        :step="0.01"
        class="flex-1"
        @update:model-value="onVolumeUpdate"
      />
      <div class="w-[16px] h-[16px]"></div>
    </div>
  </div>
</template>

