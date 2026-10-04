import { afterEach, describe, expect, it, vi } from 'vitest'
import { BufferImageSource, Container, Mesh, Text, Texture } from 'pixi.js'
import { createGpuLyricsRenderer, type GpuLyricsState } from './gpuLyricsRenderer'
import * as layout from './lyricsGpuLayout'

const gpu = vi.hoisted(() => ({ render: vi.fn(), loseContext: vi.fn(), textures: vi.fn() }))
vi.mock('pixi.js', async importOriginal => {
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
  const actual = await importOriginal<typeof import('pixi.js')>()
  return {
    ...actual,
    WebGLRenderer: class {
      resolution = 1
      init = vi.fn()
      resize = vi.fn()
      render = gpu.render
      generateTexture = ({ frame }: { frame: { width: number; height: number } }) => {
        gpu.textures(frame)
        return new actual.Texture({ source: new actual.BufferImageSource({ resource: new Uint8Array(frame.width * frame.height * 4), width: frame.width, height: frame.height }) })
      }
      destroy = vi.fn()
      gl = { getExtension: () => ({ loseContext: gpu.loseContext }) }
    },
  }
})

afterEach(() => { vi.restoreAllMocks(); vi.clearAllMocks() })

describe('enhanced lyric rendering', () => {
  it('deforms each shaped line fragment as a continuous wave and holds after the word ends', async () => {
    vi.spyOn(layout, 'measureLyricFragments').mockReturnValue([
      { text: 'Hello ', x: 0, y: 0, width: 60, height: 48, fontFamily: 'Arial', fontSize: 40, fontWeight: '600', secondary: false, word: 0 },
      { text: 'world', x: 0, y: 48, width: 40, height: 48, fontFamily: 'Arial', fontSize: 40, fontWeight: '600', secondary: false, word: 0 },
    ])
    vi.spyOn(Text.prototype, 'height', 'get').mockReturnValue(48)
    vi.spyOn(Text.prototype, 'width', 'get').mockReturnValue(60)
    const viewport = document.createElement('div')
    viewport.style.color = 'rgb(240, 240, 240)'
    document.body.append(viewport)
    const renderer = await createGpuLyricsRenderer(document.createElement('canvas'), viewport)
    renderer.resize([document.createElement('div')])
    const state: GpuLyricsState = {
      lines: [{ time: 10, text: 'Hello world', words: [{ text: 'Hello world', start: 10, end: 12 }] }],
      active: 0, browsing: false, immersive: true, hovered: -1, position: 10, reducedMotion: false,
    }
    renderer.render(state, 1000)
    const stage = gpu.render.mock.lastCall![0] as Container
    const row = stage.children[0] as Container
    const glow = row.children[0] as Container
    const meshes = row.children.filter(child => child instanceof Mesh) as Mesh[]
    const [first, second] = [meshes[0], meshes[2]]
    expect(gpu.textures).toHaveBeenCalledTimes(2) // One shaped texture per wrapped line fragment.
    expect(gpu.textures.mock.calls[0][0].height).toBeGreaterThan(48) // Descenders have room below the text bounds.
    expect(first.geometry.positions.length).toBeGreaterThan(8)
    renderer.render({ ...state, position: 11 }, 1400)
    expect(glow.visible).toBe(true)
    const firstTop = first.geometry.positions.filter((_, i) => i % 4 === 1)
    expect(firstTop[0]).toBeCloseTo(-1.2)
    expect(firstTop.at(-2)).toBeLessThan(0)
    expect(firstTop.at(-1)).toBeCloseTo(0)
    expect(second.geometry.positions[1]).toBeCloseTo(0)
    renderer.render({ ...state, position: 12.2, active: 1 }, 1500)
    expect(first.geometry.positions.filter((_, i) => i % 4 === 1).every(y => Math.abs(y + 1.2) < 0.001)).toBe(true)
    expect(second.geometry.positions.filter((_, i) => i % 4 === 1).every(y => Math.abs(y + 1.2) < 0.001)).toBe(true)
    renderer.render({ ...state, position: 13, active: 1 }, 1900)
    expect(glow.visible).toBe(false)
    expect(first.geometry.positions[1]).toBeCloseTo(-1.2)
    for (const change of [{ position: 9 }, { browsing: true }, { reducedMotion: true }, { active: -1 }]) {
      renderer.render({ ...state, position: 11, ...change }, 2000)
      expect(first.geometry.positions[1]).toBeCloseTo(0)
      expect(glow.visible).toBe(false)
    }
    const filter = glow.filters![0]
    const destroy = vi.spyOn(filter, 'destroy')
    const texture = first.texture as Texture
    const source = texture.source as BufferImageSource
    renderer.resize([])
    expect(destroy).toHaveBeenCalledOnce()
    expect(row.destroyed).toBe(true)
    expect(source.destroyed).toBe(true)
    renderer.destroy()
    expect(gpu.loseContext).toHaveBeenCalledOnce()
    viewport.remove()
  })
})
