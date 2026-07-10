import { onUnmounted, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'
import type { ArtworkCrossfadeState } from '@/stores/player'

// Drives the visual layers with the same equal-power curve as the audio fade.
export function useArtworkCrossfadeOpacity(crossfade: MaybeRefOrGetter<ArtworkCrossfadeState | null | undefined>) {
  const outgoingOpacity = ref(1)
  const incomingOpacity = ref(1)
  let rafId: number | null = null

  function stop() {
    if (rafId !== null) cancelAnimationFrame(rafId)
    rafId = null
  }

  function start(value: ArtworkCrossfadeState) {
    const startedAt = performance.now()
    const durationMs = Math.max(1, value.durationMs)
    outgoingOpacity.value = 1
    incomingOpacity.value = 0

    const update = (now: number) => {
      const t = Math.min(1, Math.max(0, (now - startedAt) / durationMs))
      outgoingOpacity.value = Math.cos(t * Math.PI / 2)
      incomingOpacity.value = Math.sin(t * Math.PI / 2)
      if (t < 1) rafId = requestAnimationFrame(update)
    }
    rafId = requestAnimationFrame(update)
  }

  watch(() => toValue(crossfade)?.transitionId, () => {
    stop()
    const value = toValue(crossfade)
    if (value) start(value)
    else {
      outgoingOpacity.value = 1
      incomingOpacity.value = 1
    }
  }, { immediate: true, flush: 'post' })

  onUnmounted(stop)
  return { outgoingOpacity, incomingOpacity }
}
