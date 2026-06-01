<template>
  <button v-bind="$attrs" class="relative">
    <slot />
    <span
      class="absolute -bottom-1.5 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full transition-opacity"
      :class="showIndicator && active ? dotClass : 'opacity-0'"
    />
  </button>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  active: boolean
  showIndicator: boolean
  dotClass?: string
}>(), {
  dotClass: 'bg-primary',
})
</script>
