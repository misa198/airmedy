<script setup lang="ts">
import SortIcon from './SortIcon.vue'

defineProps<{
  active: boolean
  dir: 'asc' | 'desc'
}>()

defineEmits<{ 'click': [] }>()
</script>

<template>
  <div
    class="relative flex items-center gap-1 px-2 min-w-0 cursor-pointer hover:text-foreground opacity-100 transition-colors select-none"
    :class="{ 'text-primary': active }"
    @click="$emit('click')"
  >
    <slot />
    <SortIcon :active="active" :dir="active ? dir : null" />
  </div>
</template>
