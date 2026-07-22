<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch, type ComponentPublicInstance } from 'vue'

export interface TabOption {
  value: string
  label?: string
  icon?: any
}

const props = defineProps<{
  options: TabOption[]
  modelValue: string | null
  mandatory?: boolean
  variant?: 'icon' | 'label'
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | null): void
}>()

const activeIndex = computed(() => {
  if (props.modelValue === null) return -1
  return props.options.findIndex(o => o.value === props.modelValue)
})

const containerRef = ref<HTMLElement | null>(null)
const buttonRefs = ref<(HTMLButtonElement | null)[]>([])
const labelSliderStyle = ref<Record<string, string | number>>({ opacity: 0 })
let resizeObserver: ResizeObserver | null = null

const sliderStyle = computed(() => {
  if (props.variant === 'label') return labelSliderStyle.value
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
      transform: `translateX(${x}px)`,
    left: '0',
  }
})

function setButtonRef(element: Element | ComponentPublicInstance | null, index: number) {
  buttonRefs.value[index] = element instanceof HTMLButtonElement ? element : null
}

function syncLabelSlider() {
  if (props.variant !== 'label' || activeIndex.value === -1) {
    labelSliderStyle.value = { opacity: 0, transform: 'scale(0.8)', pointerEvents: 'none' }
    return
  }
  const button = buttonRefs.value[activeIndex.value]
  if (!button) return
  labelSliderStyle.value = {
    opacity: 1,
    width: `${button.offsetWidth}px`,
    transform: `translateX(${button.offsetLeft}px)`,
    left: '0',
  }
}

watch([activeIndex, () => props.options, () => props.variant], () => nextTick(syncLabelSlider), { deep: true, flush: 'post' })
onMounted(() => {
  syncLabelSlider()
  if (containerRef.value) {
    resizeObserver = new ResizeObserver(syncLabelSlider)
    resizeObserver.observe(containerRef.value)
  }
})
onUnmounted(() => resizeObserver?.disconnect())

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
  <div ref="containerRef"
    class="inline-flex items-center p-1 rounded-full backdrop-blur-md border border-foreground/[0.08] bg-foreground/[0.05] relative h-10 select-none isolate">
    <!-- Sliding background for active tab -->
    <div
      class="absolute inset-y-1 bg-foreground rounded-full transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)] shadow-sm"
      :style="sliderStyle" />

    <button v-for="(option, index) in props.options" :key="option.value" :ref="element => setButtonRef(element, index)"
      @click="handleClick(option.value)"
      class="relative z-10 flex items-center justify-center h-8 rounded-full transition-colors duration-300"
      :class="[props.variant === 'label' ? 'min-w-8 px-3' : 'w-8', props.modelValue === option.value ? 'text-background' : 'text-foreground/60 hover:text-foreground/90']"
      :title="option.label">
      <component :is="option.icon" v-if="option.icon" class="w-4 h-4" />
      <span v-else-if="option.label"
        :class="props.variant === 'label' ? 'text-xs font-semibold whitespace-nowrap' : 'text-[10px] font-bold uppercase tracking-widest px-1'">
        {{ option.label }}
      </span>
    </button>
  </div>
</template>
