<script setup lang="ts">
import { SelectSeparator, type SelectSeparatorProps } from 'radix-vue'
import { cn } from '@/lib/utils'
import { computed } from 'vue'

const props = defineProps<SelectSeparatorProps & { class?: any }>()
const forwarded = computed(() => {
  const { class: _, ...rest } = props
  return rest
})
</script>

<template>
  <SelectSeparator :class="cn('-mx-1 my-1 h-px bg-foreground/10', props.class)" v-bind="forwarded" />
</template>
