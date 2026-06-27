<script setup lang="ts">
defineProps<{
  modelValue?: string | number | boolean
  value: string | number | boolean
  disabled?: boolean
}>()

defineEmits<{
  'update:modelValue': [value: string | number | boolean]
}>()
</script>

<template>
  <button
    type="button"
    role="radio"
    :aria-checked="modelValue === value"
    :disabled="disabled"
    @click="!disabled && $emit('update:modelValue', value)"
    class="relative inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full border-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
    :class="modelValue === value
      ? 'border-primary bg-primary'
      : 'border-foreground/30 bg-transparent hover:border-foreground/50'"
  >
    <span
      v-if="modelValue === value"
      class="block h-1.5 w-1.5 rounded-full bg-primary-foreground"
    />
  </button>
</template>
