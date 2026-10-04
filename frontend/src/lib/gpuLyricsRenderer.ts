import { BlurFilter, BufferImageSource, Color, Container, Mesh, MeshGeometry, Rectangle, Sprite, Text, Texture, WebGLRenderer } from 'pixi.js'
import type { LyricLine } from '../composables/useLyrics'
import { fullscreenLyricsMotionDuration, lyricsMotionProgress } from '../composables/useLyricsScrollMotion'
import { brightLayerVisible, fragmentFill, initialLyricAppearance, karaokeBaseAlpha, lyricAppearance, measureLyricFragments, wordEmphasis, wordGlow, wordProgress } from './lyricsGpuLayout'

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
type Run = { base: Text | Mesh; texture?: Texture; geometry?: MeshGeometry; y: number; height: number; fontSize: number; bright?: Mesh; mask?: Sprite; glow?: Mesh; glowMask?: Sprite; word?: number; offset: number; width: number; total: number; secondary: boolean }
type Row = {
  container: Container
  blur: BlurFilter
  glowLayer: Container
  glowBlur: BlurFilter
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
  // Neutral alpha ramp, shared by every word mask; tint still comes from the theme.
  const pixels = new Uint8Array(256 * 4).fill(255)
  for (let x = 224; x < 256; x++) pixels[x * 4 + 3] = Math.round((255 - x) / 31 * 255)
  const maskTexture = new Texture({ source: new BufferImageSource({ resource: pixels, width: 256, height: 1 }) })
  const stage = new Container()
  const rows = new Map<number, Row>()
  let layout: { element: HTMLElement; top: number; left: number; height: number }[] = []

  function waveGeometry(width: number, height: number) {
    const columns = Math.max(2, Math.ceil(width / 6))
    const positions = new Float32Array((columns + 1) * 4)
    const uvs = new Float32Array(positions.length)
    const indices = new Uint32Array(columns * 6)
    for (let i = 0; i <= columns; i++) {
      const x = i / columns
      positions.set([x * width, 0, x * width, height], i * 4)
      uvs.set([x, 0, x, 1], i * 4)
      if (i < columns) indices.set([i * 2, i * 2 + 1, i * 2 + 2, i * 2 + 1, i * 2 + 3, i * 2 + 2], i * 6)
    }
    return new MeshGeometry({ positions, uvs, indices })
  }

  function removeRow(index: number) {
    const row = rows.get(index)!
    for (const run of row.runs) {
      run.mask?.destroy()
      run.glowMask?.destroy()
    }
    row.container.destroy({ children: true })
    for (const run of row.runs) {
      run.geometry?.destroy()
      run.texture?.destroy(true)
    }
    row.blur.destroy()
    row.glowBlur.destroy()
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
    const glowLayer = new Container()
    const glowBlur = new BlurFilter({ strength: 0, quality: 4, resolution: renderer.resolution })
    glowLayer.filters = [glowBlur]
    container.addChild(glowLayer)
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
      const word = fragment.word
      const text = new Text(options)
      const height = text.height
      // Pixi rounds generated texture bounds down; leave room for descenders.
      const texture = word === undefined ? undefined : renderer.generateTexture({
        target: text, resolution: renderer.resolution,
        frame: new Rectangle(0, 0, Math.ceil(text.width), Math.ceil(height) + 4),
      })
      const meshHeight = texture?.height ?? height
      const geometry = texture ? waveGeometry(fragment.width, meshHeight) : undefined
      const base = texture && geometry ? new Mesh({ texture, geometry }) : text
      if (texture) text.destroy()
      base.position.set(fragment.x, fragment.y + (fragment.height - height) / 2)
      container.addChild(base)
      const run: Run = {
        base, texture, geometry, y: base.y, height: meshHeight, fontSize: fragment.fontSize, word, offset: word === undefined ? 0 : offsets.get(word) ?? 0,
        width: fragment.width, total: word === undefined ? fragment.width : totals.get(word)!, secondary: fragment.secondary,
      }
      if (word !== undefined && texture && geometry) {
        run.bright = new Mesh({ texture, geometry })
        run.bright.position.copyFrom(base.position)
        run.mask = new Sprite(maskTexture)
        run.bright.mask = run.mask
        container.addChild(run.bright, run.mask)
        run.glow = new Mesh({ texture, geometry })
        run.glow.position.copyFrom(base.position)
        run.glowMask = new Sprite(maskTexture)
        run.glow.mask = run.glowMask
        glowLayer.addChild(run.glow, run.glowMask)
        glowBlur.strength = Math.max(glowBlur.strength, fragment.fontSize * 0.12)
        offsets.set(word, run.offset + run.width)
      }
      runs.push(run)
    }
    stage.addChild(container)
    const initial = initialLyricAppearance(appearance)
    const row: Row = { container, blur, glowLayer, glowBlur, runs, current: initial, from: initial, target: initial, started: 0 }
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
      const target = lyricAppearance(index, state.active, state.browsing, state.immersive, state.hovered, !!state.lines[index]?.words?.length)
      const row = rows.get(index) ?? createRow(index, target)
      const keys = Object.keys(target) as (keyof Appearance)[]
      if (keys.some(key => row.target[key] !== target[key])) {
        row.from = { ...row.current }
        row.target = target
        row.started = now
      }
      const progress = state.reducedMotion ? 1 : Math.min(1, (now - row.started) / fullscreenLyricsMotionDuration)
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
      row.glowLayer.visible = false
      for (const run of row.runs) {
        run.base.tint = color
        run.base.alpha = run.secondary ? 0.8 : run.word === undefined ? 1 : activeWord ? karaokeBaseAlpha(row.current.alpha) : 1
        if (!run.bright || !run.mask || !run.geometry || run.word === undefined) continue
        run.bright.tint = color
        const word = state.lines[index]?.words?.[run.word]
        const fill = activeWord && word ? wordProgress(word, state.position) : 1
        const effects = word && !state.browsing && !state.reducedMotion && index <= state.active
        const glow = effects ? wordGlow(word, state.position) : 0
        const positions = run.geometry.positions
        for (let i = 0; i < positions.length / 4; i++) {
          const x = positions[i * 4]
          const lift = effects ? wordEmphasis(word, state.position, run.offset + x, run.total) * run.fontSize * 0.03 : 0
          positions[i * 4 + 1] = -lift
          positions[i * 4 + 3] = run.height - lift
        }
        run.geometry.getAttribute('aPosition').buffer.update()
        const width = fragmentFill(fill, run.offset, run.width, run.total)
        const maskWidth = fragmentFill(fill, run.offset, run.width / 0.875, run.total / 0.875)
        run.mask.position.set(run.base.x - 2, run.y - 6)
        run.mask.width = width > 0 ? maskWidth + 2 : 0
        run.mask.height = run.height + 12
        run.bright.visible = brightLayerVisible(activeWord, width)
        if (run.glow && run.glowMask) {
          run.glow.tint = color
          run.glow.alpha = glow * 0.42
          run.glow.visible = width > 0 && glow > 0
          run.glowMask.position.copyFrom(run.mask.position)
          run.glowMask.width = run.mask.width
          run.glowMask.height = run.mask.height
          row.glowLayer.visible ||= run.glow.visible
        }
      }
    })
    renderer.render(stage)
    return animating
  }

  function destroy() {
    for (const index of rows.keys()) removeRow(index)
    stage.destroy()
    maskTexture.destroy(true)
    const gl = renderer.gl
    renderer.destroy({ removeView: false })
    gl.getExtension('WEBGL_lose_context')?.loseContext()
  }

  return { resize, render, destroy }
}
