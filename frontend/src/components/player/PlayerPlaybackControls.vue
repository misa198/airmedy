<script setup lang="ts">
import { BackwardFill, ForwardFill } from '@airmedy/ui'
import { computed } from 'vue'
import {
  Repeat,
  Repeat1,
  Shuffle,
} from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { RepeatMode } from '../../../bindings/airmedy/internal/domain/models'
import PlayerControlButton from './PlayerControlButton.vue'
import PlayPauseCutout from './PlayPauseCutout.vue'

const props = defineProps<{
  isPlaying: boolean
  shuffle: boolean
  repeatMode: RepeatMode
  showIndicator: boolean
  disabled?: boolean
  compact?: boolean
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
  <div class="flex items-center gap-6">
    <PlayerControlButton
      :class="shuffle ? 'text-mini-player-pill-foreground opacity-80' : 'text-mini-player-pill-foreground opacity-30'"
      class="transition-opacity"
      :active="shuffle"
      :show-indicator="showIndicator"
      dot-class="bg-mini-player-pill-active"
      @click="emit('toggleShuffle')"
      :title="t('player.shuffle')"
      :aria-label="t('player.shuffle')"
      :aria-pressed="shuffle"
      :disabled="disabled"
    >
      <Shuffle :class="compact ? 'w-4 h-4' : 'w-5 h-5'" />
    </PlayerControlButton>
    <button class="text-mini-player-pill-foreground opacity-80 hover:opacity-100 transition-opacity" @click="emit('previous')" :title="t('player.previous')" :aria-label="t('player.previous')" :disabled="disabled">
      <BackwardFill class="fill-current" :class="compact ? 'w-7 h-7' : 'w-8 h-8'" />
    </button>
    <button
      class="text-mini-player-pill-active rounded-full flex items-center justify-center shadow-xl"
      :class="compact ? 'w-9 h-9' : 'w-14 h-14'"
      @click="emit('togglePlay')" :title="isPlaying ? t('player.pause') : t('player.play')" :aria-label="isPlaying ? t('player.pause') : t('player.play')" :disabled="disabled">
      <PlayPauseCutout :is-playing="isPlaying" :icon-size="compact ? 16 : 104 / 7" />
    </button>
    <button class="text-mini-player-pill-foreground opacity-80 hover:opacity-100 transition-opacity" @click="emit('next')" :title="t('player.next')" :aria-label="t('player.next')" :disabled="disabled">
      <ForwardFill class="fill-current" :class="compact ? 'w-7 h-7' : 'w-8 h-8'" />
    </button>
    <PlayerControlButton
      :class="repeatActive ? 'text-mini-player-pill-foreground opacity-80' : 'text-mini-player-pill-foreground opacity-30'"
      class="transition-opacity"
      :active="repeatActive"
      :show-indicator="showIndicator"
      dot-class="bg-mini-player-pill-active"
      @click="emit('cycleRepeat')"
      :title="t('player.repeat')"
      :aria-label="t('player.repeat')"
      :aria-pressed="repeatActive"
      :disabled="disabled"
    >
      <component :is="repeatIcon" :class="compact ? 'w-4 h-4' : 'w-5 h-5'" />
    </PlayerControlButton>
  </div>
</template>
