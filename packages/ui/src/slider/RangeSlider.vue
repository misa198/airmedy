<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@airmedy/utils'

const props = defineProps<{
  modelValue: [number, number]
  min?: number
  max?: number
  step?: number
  class?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [value: [number, number]] }>()

const resolvedMin = computed(() => props.min ?? 0)
const resolvedMax = computed(() => props.max ?? 100)
const resolvedStep = computed(() => props.step ?? 0.01)
const low = computed(() => Math.min(props.modelValue[0], props.modelValue[1]))
const high = computed(() => Math.max(props.modelValue[0], props.modelValue[1]))
const lowPct = computed(() => ((low.value - resolvedMin.value) / (resolvedMax.value - resolvedMin.value)) * 100)
const highPct = computed(() => ((high.value - resolvedMin.value) / (resolvedMax.value - resolvedMin.value)) * 100)

function updateLow(raw: string) {
  const value = Number(raw)
  if (!Number.isFinite(value)) return
  emit('update:modelValue', [Math.min(Math.max(value, resolvedMin.value), high.value), high.value])
}

function updateHigh(raw: string) {
  const value = Number(raw)
  if (!Number.isFinite(value)) return
  emit('update:modelValue', [low.value, Math.max(Math.min(value, resolvedMax.value), low.value)])
}
</script>

<template>
  <div :class="cn('relative h-4 flex items-center group/range-slider select-none', props.class)">
    <div class="absolute w-full h-1 rounded-full bg-foreground/15" />
    <div class="absolute h-1 rounded-full bg-foreground" :style="{ left: `${lowPct}%`, width: `${highPct - lowPct}%` }" />
    <input type="range" :min="resolvedMin" :max="resolvedMax" :step="resolvedStep" :value="low"
      class="range-thumb absolute inset-0 w-full bg-transparent appearance-none z-10"
      @input="updateLow(($event.target as HTMLInputElement).value)">
    <input type="range" :min="resolvedMin" :max="resolvedMax" :step="resolvedStep" :value="high"
      class="range-thumb absolute inset-0 w-full bg-transparent appearance-none z-20"
      @input="updateHigh(($event.target as HTMLInputElement).value)">
  </div>
</template>

<style scoped>
.range-thumb { pointer-events: none; }
.range-thumb::-webkit-slider-thumb { appearance: none; -webkit-appearance: none; pointer-events: auto; width: 12px; height: 12px; border-radius: 50%; background: white; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3); opacity: 0; transition: opacity 150ms ease; cursor: pointer; }
.group\/range-slider:hover .range-thumb::-webkit-slider-thumb { opacity: 1; }
.range-thumb::-moz-range-thumb { pointer-events: auto; width: 12px; height: 12px; border: 0; border-radius: 50%; background: white; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3); opacity: 0; transition: opacity 150ms ease; cursor: pointer; }
.group\/range-slider:hover .range-thumb::-moz-range-thumb { opacity: 1; }
</style>
