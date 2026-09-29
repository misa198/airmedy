import { BlurFilter, Color, Container, Graphics, Text, WebGLRenderer } from 'pixi.js'
import type { TextStyleFontWeight } from 'pixi.js'
import type { LyricLine } from '../composables/useLyrics'
import { lyricsMotionProgress } from '../composables/useLyricsScrollMotion'
import { brightLayerVisible, fragmentFill, initialLyricAppearance, karaokeBaseAlpha, lyricAppearance, measureLyricFragments, wordProgress } from './lyricsGpuLayout'

export interface GpuLyricsState {
  lines: LyricLine[]
  active: number
  browsing: boolean
  immersive: boolean
  hovered: number
  position: number
  reducedMotion: boolean
}

type Appearance = ReturnType<typeof lyricAppearance>
type Run = { base: Text; bright?: Text; mask?: Graphics; word?: number; offset: number; width: number; total: number; secondary: boolean }
type Row = {
  container: Container
  blur: BlurFilter
  runs: Run[]
  current: Appearance
  from: Appearance
  target: Appearance
  started: number
}

export async function createGpuLyricsRenderer(canvas: HTMLCanvasElement, viewport: HTMLElement) {
  const renderer = new WebGLRenderer()
  try {
    await renderer.init({ canvas, backgroundAlpha: 0, antialias: true, resolution: window.devicePixelRatio || 1, autoDensity: true })
  } catch (error) {
    // init may fail after creating the GL context.
    canvas.getContext('webgl2')?.getExtension('WEBGL_lose_context')?.loseContext()
    throw error
  }
  const stage = new Container()
  const rows = new Map<number, Row>()
  let layout: { element: HTMLElement; top: number; left: number; height: number }[] = []

  function removeRow(index: number) {
    const row = rows.get(index)!
    row.container.destroy({ children: true, texture: true, textureSource: true })
    row.blur.destroy()
    rows.delete(index)
  }

  function resize(elements: HTMLElement[]) {
    for (const index of rows.keys()) removeRow(index)
    const origin = viewport.getBoundingClientRect()
    layout = elements.map(element => {
      const rect = element.getBoundingClientRect()
      return { element, top: rect.top - origin.top + viewport.scrollTop, left: rect.left - origin.left, height: rect.height }
    })
    renderer.resize(Math.max(1, viewport.clientWidth), Math.max(1, viewport.clientHeight), window.devicePixelRatio || 1)
  }

  function createRow(index: number, appearance: Appearance) {
    const container = new Container()
    const blur = new BlurFilter({ strength: 0, quality: 4, resolution: renderer.resolution })
    const runs: Run[] = []
    const fragments = measureLyricFragments(layout[index].element)
    const totals = new Map<number, number>()
    const offsets = new Map<number, number>()
    for (const fragment of fragments) {
      if (fragment.word !== undefined) totals.set(fragment.word, (totals.get(fragment.word) ?? 0) + fragment.width)
    }
    for (const fragment of fragments) {
      const options = {
        text: fragment.text,
        resolution: renderer.resolution,
        style: {
          fontFamily: fragment.fontFamily.split(',').map(font => font.trim().replace(/^['"]|['"]$/g, '')),
          fontSize: fragment.fontSize, fontWeight: 600, padding: 4,
          // Neutral texture; runtime theme foreground is applied via GPU tint.
          fill: 0xffffff,
        },
      }
      const base = new Text(options)
      base.position.set(fragment.x, fragment.y + (fragment.height - base.height) / 2)
      container.addChild(base)
      const word = fragment.word
      const run: Run = {
        base, word, offset: word === undefined ? 0 : offsets.get(word) ?? 0,
        width: fragment.width, total: word === undefined ? fragment.width : totals.get(word)!, secondary: fragment.secondary,
      }
      if (word !== undefined) {
        run.bright = new Text(options)
        run.bright.position.copyFrom(base.position)
        run.mask = new Graphics()
        run.bright.mask = run.mask
        container.addChild(run.bright, run.mask)
        offsets.set(word, run.offset + run.width)
      }
      runs.push(run)
    }
    stage.addChild(container)
    const initial = initialLyricAppearance(appearance)
    const row: Row = { container, blur, runs, current: initial, from: initial, target: initial, started: 0 }
    rows.set(index, row)
    return row
  }

  function render(state: GpuLyricsState, now: number) {
    const color = new Color(getComputedStyle(viewport).color).toNumber()
    let animating = false
    layout.forEach((item, index) => {
      const y = item.top - viewport.scrollTop
      // Keep textures only around the viewport; a long LRC must not fill GPU memory.
      if (y + item.height < -100 || y > viewport.clientHeight + 100) {
        if (rows.has(index)) removeRow(index)
        return
      }
      const activeWord = !state.browsing && index === state.active && !!state.lines[index]?.words
      const target = lyricAppearance(index, state.active, state.browsing, state.immersive, state.hovered)
      const row = rows.get(index) ?? createRow(index, target)
      const keys = Object.keys(target) as (keyof Appearance)[]
      if (keys.some(key => row.target[key] !== target[key])) {
        row.from = { ...row.current }
        row.target = target
        row.started = now
      }
      const progress = state.reducedMotion ? 1 : Math.min(1, (now - row.started) / 300)
      const eased = lyricsMotionProgress(progress)
      row.current = { ...target }
      for (const key of keys) row.current[key] = row.from[key] + (target[key] - row.from[key]) * eased
      if (progress < 1 && keys.some(key => row.from[key] !== target[key])) animating = true
      row.container.position.set(item.left, y + item.height / 2)
      row.container.pivot.y = item.height / 2
      row.container.scale.set(row.current.scale)
      row.container.alpha = row.current.alpha
      row.blur.strength = row.current.blur
      row.container.filters = row.current.blur > 0.001 ? [row.blur] : []
      for (const run of row.runs) {
        run.base.tint = color
        run.base.alpha = run.secondary ? 0.8 : run.word === undefined ? 1 : activeWord ? karaokeBaseAlpha(row.current.alpha) : 1
        if (!run.bright || !run.mask || run.word === undefined) continue
        run.bright.tint = color
        const word = state.lines[index]?.words?.[run.word]
        const fill = activeWord && word ? wordProgress(word, state.position) : 1
        const width = fragmentFill(fill, run.offset, run.width, run.total)
        run.mask.clear().rect(run.base.x - 2, run.base.y - 4, width > 0 ? width + 2 : 0, run.base.height + 8).fill(0xffffff)
        run.bright.visible = brightLayerVisible(activeWord, width)
      }
    })
    renderer.render(stage)
    return animating
  }

  function destroy() {
    for (const index of rows.keys()) removeRow(index)
    stage.destroy()
    const gl = renderer.gl
    renderer.destroy({ removeView: false })
    gl.getExtension('WEBGL_lose_context')?.loseContext()
  }

  return { resize, render, destroy }
}
