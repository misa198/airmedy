<script setup lang="ts">
import { MarqueeText } from '@airmedy/ui'

defineProps<{
  title: string
  artist: string
  album?: string
  align?: 'left' | 'center'
  fade?: boolean
}>()
</script>

<template>
  <div class="w-full max-w-sm mx-auto" :class="align === 'left' ? 'text-left' : 'text-center'">
    <MarqueeText :text="title" :fade="fade"
      content-class="text-2xl font-bold text-foreground tracking-tight"
      :class="align === 'left' ? 'text-left' : 'text-center'" />
    <MarqueeText :text="artist" :fade="fade" content-class="text-base text-foreground/80 mt-1"
      :class="align === 'left' ? 'text-left' : 'text-center'" />
    <MarqueeText v-if="album" :text="album"
      content-class="text-sm text-foreground/60 mt-0.5 text-center" />
  </div>
</template>
