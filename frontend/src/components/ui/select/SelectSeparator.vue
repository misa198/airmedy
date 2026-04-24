<script setup lang="ts">
import { SelectSeparator, type SelectSeparatorProps } from 'radix-vue'
import { cn } from '@/lib/utils'

const props = defineProps<SelectSeparatorProps & { class?: any }>()
</script>

<template>
  <SelectSeparator :class="cn('-mx-1 my-1 h-px bg-white/10', props.class)" v-bind="props" />
</template>
