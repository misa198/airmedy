import { afterEach, describe, expect, it, vi } from 'vitest'
import { Container, Text } from 'pixi.js'
import { createGpuLyricsRenderer, type GpuLyricsState } from './gpuLyricsRenderer'
import * as layout from './lyricsGpuLayout'

const gpu = vi.hoisted(() => ({ render: vi.fn(), loseContext: vi.fn() }))
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
      destroy = vi.fn()
      gl = { getExtension: () => ({ loseContext: gpu.loseContext }) }
    },
  }
})

afterEach(() => { vi.restoreAllMocks(); vi.clearAllMocks() })

describe('enhanced lyric rendering', () => {
  it('keeps shaped word fragments still while the sung mask and glow advance', async () => {
    vi.spyOn(layout, 'measureLyricFragments').mockReturnValue([
      { text: 'going ', x: 0, y: 0, width: 60, height: 48, fontFamily: 'Arial', fontSize: 40, fontWeight: '600', secondary: false, word: 0 },
      { text: 'gypsy', x: 0, y: 48, width: 40, height: 48, fontFamily: 'Arial', fontSize: 40, fontWeight: '600', secondary: false, word: 0 },
    ])
    vi.spyOn(Text.prototype, 'height', 'get').mockReturnValue(48)
    const viewport = document.createElement('div')
    viewport.style.color = 'rgb(240, 240, 240)'
    document.body.append(viewport)
    const renderer = await createGpuLyricsRenderer(document.createElement('canvas'), viewport)
    renderer.resize([document.createElement('div')])
    const state: GpuLyricsState = {
      lines: [{ time: 10, text: 'going gypsy', words: [{ text: 'going gypsy', start: 10, end: 12 }] }],
      active: 0, browsing: false, immersive: true, hovered: -1, position: 10, reducedMotion: false, lyricsGlow: true,
    }
    renderer.render(state, 1000)
    const stage = gpu.render.mock.lastCall![0] as Container
    const row = stage.children[0] as Container
    const glow = row.children[0] as Container
    const texts = row.children.filter(child => child instanceof Text) as Text[]
    const originalY = texts.map(text => text.y)
    renderer.render({ ...state, position: 11 }, 1400)
    expect(row.scale.x).toBe(1)
    expect(glow.visible).toBe(true)
    expect(texts.map(text => text.y)).toEqual(originalY)
    expect((glow.children[0] as Text).y).toBe(originalY[0])
    expect((glow.children[2] as Text).y).toBe(originalY[2])
    expect((glow.children[0] as Text).alpha).toBeCloseTo(0.42)
    expect(row.children[3].width).toBeGreaterThan(0) // Sung mask advances.
    expect(row.children[6].width).toBe(0) // Wrapped continuation is still unsung.
    renderer.render({ ...state, position: 11, lyricsGlow: false }, 1450)
    expect(glow.visible).toBe(false)
    expect(row.children[3].width).toBeGreaterThan(0)
    renderer.render({ ...state, position: 11 }, 1475)
    expect(glow.visible).toBe(true)
    renderer.render({ ...state, position: 12.2, active: 1 }, 1500)
    expect(glow.visible).toBe(true)
    expect(texts.map(text => text.y)).toEqual(originalY)
    renderer.render({ ...state, position: 13, active: 1 }, 1900)
    expect(glow.visible).toBe(false)
    expect(texts.map(text => text.y)).toEqual(originalY)
    for (const change of [{ position: 9 }, { browsing: true }, { reducedMotion: true }, { active: -1 }]) {
      renderer.render({ ...state, position: 11, ...change }, 2000)
      expect(glow.visible).toBe(false)
      expect(texts.map(text => text.y)).toEqual(originalY)
    }
    const filter = glow.filters![0]
    const destroy = vi.spyOn(filter, 'destroy')
    renderer.resize([])
    expect(destroy).toHaveBeenCalledOnce()
    expect(row.destroyed).toBe(true)
    renderer.destroy()
    expect(gpu.loseContext).toHaveBeenCalledOnce()
    viewport.remove()
  })
})
