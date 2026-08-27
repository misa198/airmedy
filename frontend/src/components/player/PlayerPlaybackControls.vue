<script setup lang="ts">
import { computed } from 'vue'
import {
  Pause,
  Play,
  Repeat,
  Repeat1,
  Shuffle,
  SkipBack,
  SkipForward,
} from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { RepeatMode } from '../../../bindings/airmedy/internal/domain/models'
import PlayerControlButton from './PlayerControlButton.vue'

const props = defineProps<{
  isPlaying: boolean
  shuffle: boolean
  repeatMode: RepeatMode
  showIndicator: boolean
}>()

const emit = defineEmits<{
  (e: 'togglePlay'): void
  (e: 'next'): void
  (e: 'previous'): void
  (e: 'toggleShuffle'): void
  (e: 'cycleRepeat'): void
}>()

const { t } = useI18n()

const repeatIcon = computed(() =>
  props.repeatMode === RepeatMode.RepeatModeOne ? Repeat1 : Repeat,
)
const repeatActive = computed(
  () =>
    props.repeatMode === RepeatMode.RepeatModeOne ||
    props.repeatMode === RepeatMode.RepeatModeAll,
)
</script>

<template>
  <div class="flex items-center gap-7">
    <PlayerControlButton
      :class="shuffle ? 'text-white opacity-80' : 'text-white opacity-30 hover:opacity-80'"
      class="transition-opacity"
      :active="shuffle"
      :show-indicator="showIndicator"
      dot-class="bg-white"
      @click="emit('toggleShuffle')"
      :title="t('player.shuffle')"
    >
      <Shuffle class="w-5 h-5" />
    </PlayerControlButton>
    <button class="text-white opacity-80 hover:opacity-100 transition-opacity" @click="emit('previous')" :title="t('player.previous')">
      <SkipBack class="w-7 h-7 fill-current" />
    </button>
    <button
      class="w-14 h-14 bg-white rounded-full flex items-center justify-center hover:scale-105 transition-transform shadow-xl"
      @click="emit('togglePlay')" :title="isPlaying ? t('player.pause') : t('player.play')">
      <Pause v-if="isPlaying" class="w-6 h-6 fill-current text-[#0A0A0A]" />
      <Play v-else class="w-6 h-6 fill-current text-[#0A0A0A] ml-0.5" />
    </button>
    <button class="text-white opacity-80 hover:opacity-100 transition-opacity" @click="emit('next')" :title="t('player.next')">
      <SkipForward class="w-7 h-7 fill-current" />
    </button>
    <PlayerControlButton
      :class="repeatActive ? 'text-white opacity-80' : 'text-white opacity-30 hover:opacity-80'"
      class="transition-opacity"
      :active="repeatActive"
      :show-indicator="showIndicator"
      dot-class="bg-white"
      @click="emit('cycleRepeat')"
      :title="t('player.repeat')"
    >
      <component :is="repeatIcon" class="w-5 h-5" />
    </PlayerControlButton>
  </div>
</template>
