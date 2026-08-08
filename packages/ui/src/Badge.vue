<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@airmedy/utils'

const props = withDefaults(defineProps<{
  color?: string
  class?: string
}>(), {
  color: 'var(--primary)',
})

const style = computed(() => ({ '--badge-color': props.color }))
</script>

<template>
  <span :class="cn('inline-flex items-center rounded-full border px-1.5 py-px text-[9px] font-medium leading-4 transition-colors duration-300', props.class)" :style="style">
    <slot />
  </span>
</template>

<style scoped>
span {
  color: var(--badge-color);
  border-color: var(--badge-color);
  background-color: color-mix(in srgb, var(--badge-color) 14%, transparent);
}
</style>
