<script setup lang="ts">
import { Languages, LoaderCircle, RotateCcw } from '@lucide/vue'

defineProps<{
  enabled: boolean
  loading: boolean
  error: boolean
  mandarinDefault: boolean
  mini?: boolean
}>()

const emit = defineEmits<{
  activate: []
}>()
</script>

<template>
  <button type="button" data-test="romanization-toggle" :aria-pressed="enabled" :aria-busy="loading"
    :aria-label="$t(error && enabled ? 'player.romanization_retry' : loading ? 'player.romanization_loading' : 'player.romanization')"
    :title="[$t(error && enabled ? 'player.romanization_retry' : 'player.romanization'), $t(mandarinDefault ? 'player.romanization_mandarin' : 'player.romanization_hint')].join(' — ')"
    class="relative isolate shrink-0 flex items-center justify-center rounded-full border backdrop-blur-md focus-visible:outline-2 focus-visible:outline-foreground transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]"
    :class="[
      mini ? 'size-8 border-mini-player-pill-border bg-mini-player-pill-background' : 'h-10 w-10 border-foreground/[0.08] bg-foreground/[0.1]',
      enabled
        ? mini ? 'text-mini-player-pill-active-foreground' : 'text-background'
        : mini ? 'text-mini-player-pill-foreground hover:text-mini-player-pill-active' : 'text-foreground/60 hover:text-foreground/90',
    ]" @click="emit('activate')">
    <span aria-hidden="true" data-test="romanization-fill"
      class="absolute rounded-full shadow-sm transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]"
      :class="[mini ? 'inset-0 bg-mini-player-pill-active' : 'inset-0 bg-foreground', enabled ? 'opacity-100' : 'opacity-0']" />
    <LoaderCircle v-if="loading" class="relative z-10 size-4 animate-spin" aria-hidden="true" />
    <RotateCcw v-else-if="error && enabled" class="relative z-10 size-4" aria-hidden="true" />
    <Languages v-else class="relative z-10 size-4" aria-hidden="true" />
  </button>
</template>
