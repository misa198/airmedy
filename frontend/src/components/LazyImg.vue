<script setup lang="ts">
defineProps<{
  src?: string | null
  alt?: string
}>()
</script>

<template>
  <img
    :src="src ?? undefined"
    :alt="alt"
    loading="lazy"
    decoding="async"
  />
</template>
