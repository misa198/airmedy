<script setup lang="ts">
defineProps<{
  text: string
}>()
</script>

<template>
  <span class="relative inline-flex group">
    <slot />
    <span
      class="pointer-events-none absolute bottom-full left-1/2 z-50 mb-2 w-max max-w-[240px] -translate-x-1/2 rounded-md border border-border bg-card px-2 py-1.5 text-center text-xs text-foreground opacity-0 shadow-lg invisible transition-opacity duration-150 group-hover:visible group-hover:opacity-100"
    >
      {{ text }}
    </span>
  </span>
</template>
