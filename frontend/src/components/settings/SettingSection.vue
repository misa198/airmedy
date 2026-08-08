<script setup lang="ts">
import type { Component } from 'vue'

withDefaults(defineProps<{
  icon: Component
  label: string
  variant?: 'rows' | 'panel'
}>(), {
  variant: 'rows',
})
</script>

<template>
  <section>
    <div class="flex items-center justify-between gap-2 mb-6 select-none">
      <div class="flex items-center gap-2 text-dim">
        <component :is="icon" class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ label }}</h2>
      </div>
      <slot name="header-extra" />
    </div>
    <div :class="variant === 'panel'
      ? 'bg-card rounded-2xl border border-foreground/[0.06] p-6'
      : 'bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]'">
      <slot />
    </div>
  </section>
</template>
