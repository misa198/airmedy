<script setup lang="ts">
import { cn } from '@/lib/utils'
import { X } from 'lucide-vue-next'

defineOptions({ inheritAttrs: false })

const props = defineProps<{
  class?: string
  modelValue?: string | number
  clearable?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number): void
}>()
</script>

<template>
  <div class="relative">
    <input
      v-bind="$attrs"
      :value="modelValue"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      :class="cn(
        'w-full rounded-md border border-border bg-accent/50 px-3 py-2 text-sm text-foreground',
        'placeholder:text-muted-foreground',
        'focus:outline-none focus:ring-2 focus:ring-primary/20',
        'disabled:cursor-not-allowed disabled:opacity-50',
        'transition-all duration-200 ease-in-out',
        clearable && modelValue ? 'pr-7!' : '',
        props.class
      )"
    />
    <button
      v-if="clearable && modelValue"
      type="button"
      @click="emit('update:modelValue', '')"
      class="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
    >
      <X class="w-4 h-4" />
    </button>
  </div>
</template>
