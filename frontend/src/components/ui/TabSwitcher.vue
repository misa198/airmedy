<script setup lang="ts">
import { computed } from 'vue'

export interface TabOption {
  value: string
  label?: string
  icon?: any
}

const props = defineProps<{
  options: TabOption[]
  modelValue: string | null
  mandatory?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | null): void
}>()

const activeIndex = computed(() => {
  if (props.modelValue === null) return -1
  return props.options.findIndex(o => o.value === props.modelValue)
})

const sliderStyle = computed(() => {
  if (activeIndex.value === -1) {
    return {
      opacity: 0,
      transform: 'scale(0.8)',
      pointerEvents: 'none' as const
    }
  }

  const buttonSize = 30
  const padding = 6
  const gap = 1
  const x = padding + activeIndex.value * (buttonSize + gap)

  return {
    opacity: 1,
    width: `${buttonSize}px`,
    height: `${buttonSize}px`,
    transform: `translateX(${x}px)`,
    left: '0',
  }
})

function handleClick(value: string) {
  if (props.modelValue === value) {
    if (!props.mandatory) {
      emit('update:modelValue', null)
    }
  } else {
    emit('update:modelValue', value)
  }
}
</script>

<template>
  <div
    class="inline-flex items-center p-1 rounded-full bg-foreground/10 backdrop-blur-md border border-foreground/5 relative h-10 select-none isolate">
    <!-- Sliding background for active tab -->
    <div
      class="absolute top-1 bg-foreground rounded-full transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)] shadow-sm"
      :style="sliderStyle" />

    <button v-for="option in props.options" :key="option.value" @click="handleClick(option.value)"
      class="relative z-10 flex items-center justify-center w-8 h-8 rounded-full transition-colors duration-300"
      :class="props.modelValue === option.value ? 'text-background' : 'text-foreground/60 hover:text-foreground/90'"
      :title="option.label">
      <component :is="option.icon" v-if="option.icon" class="w-4 h-4" />
      <span v-else-if="option.label" class="text-[10px] font-bold uppercase tracking-widest px-1">
        {{ option.label }}
      </span>
    </button>
  </div>
</template>
