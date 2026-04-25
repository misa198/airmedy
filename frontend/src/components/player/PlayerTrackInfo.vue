<script setup lang="ts">
import MarqueeText from '../MarqueeText.vue'

defineProps<{
  title: string
  artist: string
  album?: string
}>()
</script>

<template>
  <div class="text-center w-full max-w-sm mx-auto">
    <MarqueeText :text="title"
      content-class="text-2xl font-bold text-white tracking-tight text-center" />
    <MarqueeText :text="artist" content-class="text-base text-white/80 mt-1 text-center" />
    <MarqueeText v-if="album" :text="album"
      content-class="text-sm text-white/60 mt-0.5 text-center" />
  </div>
</template>
