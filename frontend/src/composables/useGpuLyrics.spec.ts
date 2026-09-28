import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { useGpuLyrics } from './useGpuLyrics'

const mock = vi.hoisted(() => ({ create: vi.fn() }))
vi.mock('../lib/gpuLyricsRenderer', () => ({ createGpuLyricsRenderer: mock.create }))

describe('GPU lyrics lifecycle', () => {
  const frames = new Map<number, FrameRequestCallback>()
  const renderer = { render: vi.fn(() => false), resize: vi.fn(), destroy: vi.fn() }
  const state = ref({ lines: [], active: -1, browsing: false, immersive: true, hovered: -1, position: 0, reducedMotion: false })
  let controls: ReturnType<typeof useGpuLyrics>
  const Host = defineComponent({
    setup() {
      const canvas = ref<HTMLCanvasElement | null>(null)
      const viewport = ref<HTMLElement | null>(null)
      controls = useGpuLyrics(canvas, viewport, ref([]), state)
      return { canvas, viewport, ready: controls.ready }
    },
    template: '<div ref="viewport" :data-ready="ready"><canvas ref="canvas" /></div>',
  })
  function paint() {
    const pending = [...frames.values()]
    frames.clear()
    pending.forEach(callback => callback(500))
  }
  beforeEach(() => {
    let id = 0
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { frames.set(++id, callback); return id })
    vi.stubGlobal('cancelAnimationFrame', (id: number) => frames.delete(id))
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(600)
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(800)
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    mock.create.mockResolvedValue(renderer)
  })
  afterEach(() => { frames.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.clearAllMocks() })

  it('draws on demand, preserves textures for playback, and falls back on context loss', async () => {
    const wrapper = mount(Host)
    await flushPromises()
    expect(controls.ready.value).toBe(false)
    paint()
    expect(controls.ready.value).toBe(true)
    expect(renderer.resize).toHaveBeenCalledTimes(1)
    state.value = { ...state.value, position: 2 }
    await flushPromises()
    paint()
    expect(renderer.resize).toHaveBeenCalledTimes(1)
    expect(renderer.render).toHaveBeenLastCalledWith(expect.objectContaining({ position: 2 }), 500)
    expect(frames.size).toBe(0)
    await wrapper.get('canvas').trigger('webglcontextlost')
    expect(controls.ready.value).toBe(false)
    expect(renderer.destroy).toHaveBeenCalledTimes(1)
    wrapper.unmount()
    expect(renderer.destroy).toHaveBeenCalledTimes(1)
  })

  it('disposes initialization that completes after unmount', async () => {
    let resolve!: (value: typeof renderer) => void
    mock.create.mockReturnValue(new Promise(yes => { resolve = yes }))
    const wrapper = mount(Host)
    await flushPromises()
    wrapper.unmount()
    resolve(renderer)
    await flushPromises()
    expect(renderer.destroy).toHaveBeenCalledTimes(1)
    expect(renderer.render).not.toHaveBeenCalled()
  })

  it('keeps readable DOM when initialization fails', async () => {
    mock.create.mockRejectedValue(new Error('WebGL unavailable'))
    const wrapper = mount(Host)
    await flushPromises()
    expect(controls.ready.value).toBe(false)
    expect(frames.size).toBe(0)
    wrapper.unmount()
  })
})
