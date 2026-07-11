import { brush as d3brush, type D3BrushEvent } from 'd3-brush'
import { select } from 'd3-selection'
import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import type { MoodBox } from '../lib/moodPlaylistFields'

// Wraps d3-brush's drag/resize-box interaction onto an SVG <g>, translating
// between its pixel-space selection and the [0,1]x[0,1] energy/danceability
// domain the Mood Playlist heatmap plots. D3 owns the brush <g>'s DOM subtree
// once mounted (it appends/updates its own .overlay/.selection/.handle
// rects there) — Vue never re-renders into it, so teardown on unmount is
// explicit rather than implicit.
//
// SVG y grows downward but "energy" should read bottom-to-top (low energy at
// the bottom), so the y axis is flipped when converting to/from pixel space.
export function useQuadrantBrush(
  groupRef: Ref<SVGGElement | null>,
  width: Ref<number>,
  height: Ref<number>,
  initialBox: MoodBox,
  onChange: (box: MoodBox) => void,
) {
  const box = ref<MoodBox>({ ...initialBox })
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let brush: any = null

  function toPixels(b: MoodBox): [[number, number], [number, number]] {
    const w = width.value
    const h = height.value
    return [
      [b.danceMin * w, (1 - b.energyMax) * h],
      [b.danceMax * w, (1 - b.energyMin) * h],
    ]
  }

  function fromPixels(sel: [[number, number], [number, number]]): MoodBox {
    const w = width.value || 1
    const h = height.value || 1
    const [[x0, y0], [x1, y1]] = sel
    return {
      danceMin: x0 / w,
      danceMax: x1 / w,
      energyMin: 1 - y1 / h,
      energyMax: 1 - y0 / h,
      brightnessMin: box.value.brightnessMin,
      brightnessMax: box.value.brightnessMax,
    }
  }

  function handleBrushed(event: D3BrushEvent<unknown>) {
    if (!event.selection) return
    // Ignore programmatic moves (setBox below) so they don't re-emit and
    // fight the caller that triggered them.
    if (event.sourceEvent == null) return
    box.value = fromPixels(event.selection as [[number, number], [number, number]])
    onChange(box.value)
  }

  onMounted(() => {
    if (!groupRef.value) return
    brush = d3brush()
      .extent([[0, 0], [width.value, height.value]])
      .on('brush end', handleBrushed)
    const selection = select(groupRef.value)
    selection.call(brush)
    selection.call(brush.move, toPixels(box.value))
  })

  // Keep the brush's extent in sync if the SVG is resized.
  watch([width, height], ([w, h]) => {
    if (!brush || !groupRef.value) return
    brush.extent([[0, 0], [w, h]])
    select(groupRef.value).call(brush)
  })

  // Programmatic update (e.g. from a numeric input) — moves the brush's
  // internal selection so a subsequent drag continues from the right place,
  // not just the rendered rect.
  function setBox(next: MoodBox) {
    box.value = next
    if (!brush || !groupRef.value) return
    select(groupRef.value).call(brush.move, toPixels(next))
  }

  onBeforeUnmount(() => {
    if (groupRef.value) {
      select(groupRef.value).on('.brush', null)
    }
  })

  return { box, setBox }
}
