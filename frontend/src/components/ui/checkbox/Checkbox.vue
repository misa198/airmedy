<script setup lang="ts">
import { Check } from 'lucide-vue-next'

defineProps<{
  checked: boolean
  variant?: 'outlined' | 'contained'
}>()

defineEmits<{
  'update:checked': [value: boolean]
}>()
</script>

<template>
  <div class="w-4 h-4 rounded border flex items-center justify-center flex-shrink-0 transition-colors cursor-pointer"
    :class="checked
      ? variant === 'outlined'
        ? 'border-primary'
        : 'border-primary bg-primary'
      : 'border-foreground/20 hover:border-foreground/40'" @click="$emit('update:checked', !checked)">
    <Check v-if="checked" class="w-2.5 h-2.5" :class="variant === 'outlined' ? 'text-primary' : 'text-white'" />
  </div>
</template>
