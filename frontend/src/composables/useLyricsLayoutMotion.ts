import { nextTick, onUnmounted, watch, type Ref, type WatchSource } from 'vue'
import { fullscreenLyricsMotionDuration } from './useLyricsScrollMotion'

export function useLyricsLayoutMotion(options: {
  secondary: WatchSource<unknown>
  viewport: Ref<HTMLElement | null>
  rows: Ref<HTMLElement[]>
  activeIndex: Readonly<Ref<number>>
  following: () => boolean
  stop: () => void
}) {
  const animations = new Set<Animation>()
  let disposed = false

  function cancel() {
    animations.forEach(animation => animation.cancel())
    animations.clear()
  }

  watch(options.secondary, () => {
    const viewport = options.viewport.value
    if (!viewport?.clientHeight || !viewport.clientWidth) return
    const previous = new Map(options.rows.value.map(row => [row, row.getBoundingClientRect()]))
    const active = options.rows.value[options.activeIndex.value]
    const pinned = options.following() && previous.has(active)
    if (pinned) options.stop()
    cancel()
    nextTick(() => {
      if (disposed || options.viewport.value !== viewport) return
      // Restore the primary line before paint, even if shrinking rows clamped scrollTop.
      if (pinned && options.following() && options.rows.value[options.activeIndex.value] === active) {
        viewport.scrollTop += active.getBoundingClientRect().top - previous.get(active)!.top
      }
      if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
      const bounds = viewport.getBoundingClientRect()
      for (const row of options.rows.value) {
        const before = previous.get(row)
        if (!before) continue
        const after = row.getBoundingClientRect()
        const delta = before.top - after.top
        if (Math.abs(delta) < 0.5) continue
        if (before.bottom < bounds.top && after.bottom < bounds.top) continue
        if (before.top > bounds.bottom && after.top > bounds.bottom) continue
        const animation = row.animate?.([
          { translate: `0 ${delta}px` }, { translate: '0 0' },
        ], { duration: fullscreenLyricsMotionDuration, easing: 'cubic-bezier(0.4, 0, 0.2, 1)' })
        if (animation) {
          animations.add(animation)
          animation.onfinish = () => { animations.delete(animation) }
        }
      }
    })
  })

  onUnmounted(() => {
    disposed = true
    cancel()
  })
}
