<script setup lang="ts">
import { Music } from '@lucide/vue'
import { onUnmounted, ref, watch } from 'vue'
import LazyImg from '@/components/LazyImg.vue'
import type { ArtworkCrossfadeState } from '@/stores/player'

const props = defineProps<{
  artworkUrl?: string | null
  trackTitle: string
  isPlaying: boolean
  crossfade?: ArtworkCrossfadeState | null
  maxSize?: 20 | 24
}>()

const outgoingOpacity = ref(1)
const incomingOpacity = ref(0)
let rafId: number | null = null

function stopCrossfadeAnimation() {
  if (rafId !== null) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
}

function startCrossfadeAnimation(crossfade: ArtworkCrossfadeState) {
  const durationMs = Math.max(1, crossfade.durationMs)
  let startedAt: number | null = null

  const update = (now: number) => {
    if (startedAt === null) startedAt = now
    const t = Math.min((now - startedAt) / durationMs, 1)
    // Match the equal-power audio crossfade: old = cos(t*pi/2), new = sin(t*pi/2).
    outgoingOpacity.value = Math.cos(t * Math.PI / 2)
    incomingOpacity.value = Math.sin(t * Math.PI / 2)
    if (t < 1) rafId = requestAnimationFrame(update)
  }

  outgoingOpacity.value = 1
  incomingOpacity.value = 0
  rafId = requestAnimationFrame(update)
}

watch(() => props.crossfade?.transitionId, () => {
  stopCrossfadeAnimation()
  if (!props.crossfade) {
    outgoingOpacity.value = 1
    incomingOpacity.value = 1
    return
  }
  startCrossfadeAnimation(props.crossfade)
}, { immediate: true, flush: 'post' })

onUnmounted(stopCrossfadeAnimation)
</script>

<template>
  <div
    class="relative rounded-2xl shadow-[0_24px_60px_rgba(0,0,0,0.6)] overflow-hidden flex-shrink-0 ring-1 ring-white/8 transition-all duration-700 ease-[cubic-bezier(0.4,0,0.2,1)] aspect-square w-auto"
    :class="[
      isPlaying ? 'scale-100' : 'scale-[0.80]',
      maxSize === 20 ? 'h-[clamp(8rem,34vh,20rem)]' : 'h-[clamp(8rem,34vh,24rem)]',
    ]">
    <template v-if="crossfade">
      <LazyImg v-if="crossfade.fromUrl" :src="crossfade.fromUrl" :alt="trackTitle"
        class="absolute inset-0 w-full h-full object-cover" :style="{ opacity: outgoingOpacity }" />
      <div v-else class="absolute inset-0 bg-white/5 flex items-center justify-center" :style="{ opacity: outgoingOpacity }">
        <Music class="w-20 h-20 text-white/15" />
      </div>
      <LazyImg v-if="crossfade.toUrl" :key="`${crossfade.transitionId}-img`" :src="crossfade.toUrl" :alt="trackTitle"
        class="absolute inset-0 w-full h-full object-cover artwork-crossfade-incoming"
        :style="{ opacity: incomingOpacity }" />
      <div v-else :key="`${crossfade.transitionId}-placeholder`" class="absolute inset-0 bg-white/5 artwork-crossfade-incoming"
        :style="{ opacity: incomingOpacity }">
        <Music class="w-20 h-20 text-white/15" />
      </div>
    </template>
    <LazyImg v-else-if="artworkUrl" :src="artworkUrl" :alt="trackTitle" class="w-full h-full object-cover" />
    <div v-else class="w-full h-full bg-white/5 flex items-center justify-center">
      <Music class="w-20 h-20 text-white/15" />
    </div>
  </div>
</template>

<style scoped>
.artwork-crossfade-incoming {
  mix-blend-mode: plus-lighter;
}
</style>
