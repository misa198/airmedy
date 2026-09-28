import { onMounted, onUnmounted, ref, watch, type Ref } from 'vue'
import type { createGpuLyricsRenderer, GpuLyricsState } from '../lib/gpuLyricsRenderer'

export function useGpuLyrics(
  canvas: Ref<HTMLCanvasElement | null>, viewport: Ref<HTMLElement | null>,
  rows: Ref<HTMLElement[]>, state: Readonly<Ref<GpuLyricsState>>,
) {
  const ready = ref(false)
  let renderer: Awaited<ReturnType<typeof createGpuLyricsRenderer>> | undefined
  let disposed = false
  let frame: number | undefined
  let needsLayout = true
  let themeObserver: MutationObserver | undefined
  let motion: MediaQueryList | undefined
  let element: HTMLCanvasElement | null = null

  function draw() {
    if (disposed || !renderer || frame !== undefined) return
    frame = requestAnimationFrame(now => {
      frame = undefined
      if (!renderer || !viewport.value?.clientWidth || !viewport.value.clientHeight) return
      try {
        if (needsLayout) {
          renderer.resize(rows.value)
          needsLayout = false
        }
        const animating = renderer.render({ ...state.value, reducedMotion: motion?.matches ?? false }, now)
        ready.value = true
        if (animating) draw()
      } catch (error) {
        fail(error)
      }
    })
  }

  function layout() {
    needsLayout = true
    draw()
  }

  function release() {
    if (frame !== undefined) cancelAnimationFrame(frame)
    frame = undefined
    ready.value = false
    const previous = renderer
    renderer = undefined
    previous?.destroy()
  }

  function fail(error: unknown) {
    console.warn('GPU lyrics unavailable; using readable DOM lyrics without blur.', error)
    element?.removeEventListener('webglcontextlost', contextLost)
    release()
  }

  function contextLost(event: Event) {
    event.preventDefault()
    fail(new Error('WebGL context lost'))
  }

  watch(state, draw)
  onMounted(async () => {
    element = canvas.value
    motion = window.matchMedia?.('(prefers-reduced-motion: reduce)')
    motion?.addEventListener('change', draw)
    themeObserver = new MutationObserver(layout)
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style'] })
    document.fonts?.addEventListener('loadingdone', layout)
    try {
      const { createGpuLyricsRenderer } = await import('../lib/gpuLyricsRenderer')
      await document.fonts?.ready
      if (disposed || !element || !viewport.value) return
      const instance = await createGpuLyricsRenderer(element, viewport.value)
      if (disposed) { instance.destroy(); return }
      renderer = instance
      element.addEventListener('webglcontextlost', contextLost)
      layout()
    } catch (error) {
      if (!disposed) fail(error)
    }
  })

  onUnmounted(() => {
    disposed = true
    element?.removeEventListener('webglcontextlost', contextLost)
    motion?.removeEventListener('change', draw)
    document.fonts?.removeEventListener('loadingdone', layout)
    themeObserver?.disconnect()
    release()
  })

  return { ready, draw, layout }
}
