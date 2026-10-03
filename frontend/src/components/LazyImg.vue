<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  src?: string | null
  alt?: string
}>()

const retried = ref(false)
watch(() => props.src, () => { retried.value = false })

const imageSrc = computed(() => {
  if (!props.src) return undefined
  if (!retried.value) return props.src
  return `${props.src}${props.src.includes('?') ? '&' : '?'}retry=1`
})
</script>

<template>
  <img
    :src="imageSrc"
    :alt="alt"
    loading="lazy"
    decoding="async"
    @error="retried = true"
  />
</template>
