<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { Component } from 'vue'

defineProps<{
  to: string
  icon: Component
  label: string
}>()

defineEmits<{
  (e: 'contextmenu', event: MouseEvent): void
  (e: 'dblclick', event: MouseEvent): void
}>()
</script>

<template>
  <div class="relative group">
    <RouterLink :to="to"
      class="flex items-center gap-3 px-3 py-2 rounded-lg transition-colors text-foreground opacity-80 hover:text-foreground hover:bg-foreground/[0.05]"
      :class="{ 'pr-8': $slots.actions }"
      active-class="bg-foreground/[0.08] !text-primary font-medium"
      @contextmenu="$emit('contextmenu', $event)"
      @dblclick="$emit('dblclick', $event)">
      <component :is="icon" class="w-4 h-4 flex-shrink-0" />
      <span class="text-sm truncate">{{ label }}</span>
    </RouterLink>

    <div v-if="$slots.actions" class="absolute right-2 top-1/2 -translate-y-1/2 flex items-center">
      <slot name="actions" />
    </div>
  </div>
</template>
