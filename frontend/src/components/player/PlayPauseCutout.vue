<script setup lang="ts">
import { useId } from 'vue'
import { PlayFill as Play, PauseFill as Pause } from '@airmedy/ui'

withDefaults(defineProps<{ isPlaying: boolean; iconSize?: number }>(), { iconSize: 16 })
const maskId = useId()
</script>

<template>
  <svg aria-hidden="true" width="100%" height="100%" viewBox="0 0 32 32">
    <mask :id="maskId" mask-type="luminance">
      <rect width="32" height="32" fill="white" />
      <Pause v-if="isPlaying" :x="(32 - iconSize) / 2" :y="(32 - iconSize) / 2" :size="iconSize" color="black" fill="black" />
      <Play v-else :x="(32 - iconSize) / 2 + iconSize / 16" :y="(32 - iconSize) / 2" :size="iconSize" color="black" fill="black" />
    </mask>
    <circle cx="16" cy="16" r="16" fill="currentColor" :mask="`url(#${maskId})`" />
  </svg>
</template>
