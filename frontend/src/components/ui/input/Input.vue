<script setup lang="ts">
import { cn } from '@/lib/utils'

defineOptions({ inheritAttrs: false })

const props = defineProps<{
  class?: string
  modelValue?: string | number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number): void
}>()
</script>

<template>
  <input
    v-bind="$attrs"
    :value="modelValue"
    @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    :class="cn(
      'w-full rounded-md border border-border bg-accent/50 px-3 py-2 text-sm text-foreground',
      'placeholder:text-muted-foreground',
      'focus:outline-none focus:ring-2 focus:ring-primary/20',
      'disabled:cursor-not-allowed disabled:opacity-50',
      props.class
    )"
  />
</template>
