<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<{
  modelValue: number
  min?: number
  max?: number
  step?: number
  class?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number]
  'mousedown': [e: MouseEvent]
  'mouseup': [e: MouseEvent]
  'touchstart': [e: TouchEvent]
  'touchend': [e: TouchEvent]
}>()

const resolvedMin = computed(() => props.min ?? 0)
const resolvedMax = computed(() => props.max ?? 100)

const fillPct = computed(() => {
  const range = resolvedMax.value - resolvedMin.value
  if (range === 0) return 0
  return Math.min(100, Math.max(0, ((props.modelValue - resolvedMin.value) / range) * 100))
})
</script>

<template>
  <div :class="cn('relative h-4 flex items-center group/slider cursor-pointer select-none', props.class)">
    <!-- Visual track -->
    <div class="absolute w-full h-[2px] rounded-full bg-white/15">
      <div
        class="h-full rounded-full bg-white"
        :style="{ width: fillPct + '%' }"
      />
    </div>
    <!-- Thumb — visible on hover only -->
    <div
      class="absolute w-[10px] h-[10px] rounded-full bg-white shadow pointer-events-none opacity-0 group-hover/slider:opacity-100 transition-opacity duration-150 -translate-x-1/2"
      :style="{ left: fillPct + '%' }"
    />
    <!-- Invisible native input for interaction and accessibility -->
    <input
      type="range"
      :min="resolvedMin"
      :max="resolvedMax"
      :step="step ?? 0.01"
      :value="modelValue"
      class="absolute inset-0 w-full opacity-0 cursor-pointer"
      @input="(e) => emit('update:modelValue', Number((e.target as HTMLInputElement).value))"
      @mousedown="emit('mousedown', $event)"
      @mouseup="emit('mouseup', $event)"
      @touchstart="emit('touchstart', $event)"
      @touchend="emit('touchend', $event)"
    />
  </div>
</template>
