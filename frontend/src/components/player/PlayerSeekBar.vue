<script setup lang="ts">
import { ref, computed } from 'vue'
import { Slider } from '@/components/ui/slider'
import { formatTime } from '../../lib/utils'

const props = defineProps<{
  progressPercent: number
  position: number
  duration: number
}>()

const emit = defineEmits<{
  (e: 'seek', value: number): void
}>()

const isSeeking = ref(false)
const seekValue = ref(0)

const displayPosition = computed(() =>
  isSeeking.value ? (seekValue.value / 100) * props.duration : props.position,
)

function onSeekStart() {
  isSeeking.value = true
  seekValue.value = props.progressPercent
}

function onSeekEnd() {
  emit('seek', (seekValue.value / 100) * props.duration)
  isSeeking.value = false
}
</script>

<template>
  <div class="w-full max-w-sm space-y-1.5">
    <Slider :model-value="isSeeking ? seekValue : progressPercent" :min="0" :max="100" :step="0.1"
      @update:model-value="(v) => (seekValue = v)" @mousedown="onSeekStart" @mouseup="onSeekEnd"
      @touchstart="onSeekStart" @touchend="onSeekEnd" />
    <div class="flex justify-between text-[10.5px] text-white/60 tabular-nums">
      <span>{{ formatTime(displayPosition) }}</span>
      <span>{{ formatTime(duration) }}</span>
    </div>
  </div>
</template>
