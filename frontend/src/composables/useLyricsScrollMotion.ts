import { onUnmounted } from 'vue'

export const lyricsMotionDurationClasses = 'duration-[230ms] ease-[cubic-bezier(0.4,0,0.2,1)]'
export const lyricsMotionClasses = 'transition-[color,opacity] duration-[230ms] ease-[cubic-bezier(0.4,0,0.2,1)]'

const lyricsMotionDuration = 230

function lyricsMotionProgress(progress: number) {
  const sample = (a: number, b: number, t: number) => 3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t
  let lower = 0
  let upper = 1
  for (let iteration = 0; iteration < 12; iteration++) {
    const t = (lower + upper) / 2
    if (sample(0.4, 0.2, t) < progress) lower = t
    else upper = t
  }
  return sample(0, 1, (lower + upper) / 2)
}

export function useLyricsScrollMotion() {
  let frame: number | undefined

  function stop() {
    if (frame !== undefined) cancelAnimationFrame(frame)
    frame = undefined
  }

  function scrollTo(container: HTMLElement, top: number, animated: boolean) {
    stop()
    if (!animated) {
      container.scrollTop = top
      return
    }
    const from = container.scrollTop
    const distance = top - from
    let startTime: number | undefined
    const step = (timestamp: number) => {
      startTime ??= timestamp
      const progress = Math.min((timestamp - startTime) / lyricsMotionDuration, 1)
      container.scrollTop = progress === 1 ? top : from + distance * lyricsMotionProgress(progress)
      if (progress < 1) frame = requestAnimationFrame(step)
      else frame = undefined
    }
    frame = requestAnimationFrame(step)
  }

  onUnmounted(stop)
  return { scrollTo, stop }
}
