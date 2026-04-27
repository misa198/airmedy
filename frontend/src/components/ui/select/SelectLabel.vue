<script setup lang="ts">
import { SelectLabel, type SelectLabelProps } from 'radix-vue'
import { cn } from '@/lib/utils'

const props = defineProps<SelectLabelProps & { class?: any }>()
</script>

<template>
  <SelectLabel :class="cn('py-1.5 pl-8 pr-2 text-sm font-semibold text-foreground opacity-80', props.class)">
    <slot />
  </SelectLabel>
</template>
