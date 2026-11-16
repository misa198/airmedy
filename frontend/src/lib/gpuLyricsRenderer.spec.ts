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
  it.each([-1, 0])('animates secondary layout changes without restarting appearance with active index %i', async active => {
    vi.spyOn(layout, 'measureLyricFragments').mockReturnValue([])
    const viewport = document.createElement('div')
    viewport.style.color = 'rgb(240, 240, 240)'
    Object.defineProperty(viewport, 'clientHeight', { value: 800 })
    const element = document.createElement('div')
    let top = 100
    vi.spyOn(element, 'getBoundingClientRect').mockImplementation(() => ({ top, left: 0, height: 60 }) as DOMRect)
    const renderer = await createGpuLyricsRenderer(document.createElement('canvas'), viewport)
    const state: GpuLyricsState = {
      lines: [{ time: 0, text: '你好' }], active, browsing: false, immersive: true,
      hovered: -1, position: 0, reducedMotion: false, lyricsGlow: false,
    }
    renderer.resize([element])
    renderer.render(state, 0)
    renderer.render(state, 1000)
    const stage = gpu.render.mock.lastCall![0] as Container
    const originalY = stage.children[0].y
    top = 220
    renderer.resize([element], true)
    expect(renderer.render(state, 1100)).toBe(true)
    expect(stage.children[0].y).toBeCloseTo(originalY)
    expect(stage.children[0].scale.x).toBeCloseTo(active === 0 ? 1.06 : 1)
    renderer.render(state, 1260)
    expect(stage.children[0].y).toBeGreaterThan(originalY)
    expect(stage.children[0].y).toBeLessThan(originalY + 120)
    const interruptedY = stage.children[0].y
    top = 100
    renderer.resize([element], true)
    renderer.render(state, 1260)
    expect(stage.children[0].y).toBeCloseTo(interruptedY)
    renderer.render(state, 1580)
    expect(stage.children[0].y).toBeCloseTo(originalY)
    top = 220
    renderer.resize([element], true)
    expect(renderer.render({ ...state, reducedMotion: true }, 1600)).toBe(false)
    expect(stage.children[0].y).toBeCloseTo(originalY + 120)
    renderer.destroy()
  })

  it('keeps the active GPU row fixed throughout a compensated layout change', async () => {
    vi.spyOn(layout, 'measureLyricFragments').mockReturnValue([])
    const viewport = document.createElement('div')
    viewport.style.color = 'rgb(240, 240, 240)'
    Object.defineProperty(viewport, 'clientHeight', { value: 800 })
    const elements = [document.createElement('div'), document.createElement('div')]
    let tops = [100, 300]
    let height = 120
    elements.forEach((element, index) => {
      const primary = document.createElement('div')
      primary.setAttribute('data-lyric-primary', '')
      element.append(primary)
      vi.spyOn(primary, 'getBoundingClientRect').mockImplementation(() => ({ top: tops[index] - viewport.scrollTop, height: 40 }) as DOMRect)
      vi.spyOn(element, 'getBoundingClientRect').mockImplementation(() => ({ top: tops[index] - viewport.scrollTop, left: 0, height }) as DOMRect)
    })
    const renderer = await createGpuLyricsRenderer(document.createElement('canvas'), viewport)
    const state: GpuLyricsState = {
      lines: [{ time: 0, text: '你好' }, { time: 10, text: '世界' }], active: 1, browsing: false,
      immersive: true, hovered: -1, position: 10, reducedMotion: false, lyricsGlow: false,
    }
    viewport.scrollTop = 200
    renderer.resize(elements)
    renderer.render(state, 0)
    renderer.render(state, 1000)
    const stage = gpu.render.mock.lastCall![0] as Container
    const activeY = stage.children[1].y
    const previousY = stage.children[0].y
    tops = [80, 200]
    height = 60
    viewport.scrollTop = 100
    renderer.resize(elements, true, -100)
    for (const now of [1100, 1260, 1420]) {
      renderer.render(state, now)
      expect(stage.children[1].y).toBeCloseTo(activeY)
    }
    expect(stage.children[0].y).not.toBe(previousY)
    renderer.destroy()
  })

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
