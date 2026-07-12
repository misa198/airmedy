import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import DevToolsOverlay from './DevToolsOverlay.vue'

const openDevTools = vi.fn()

vi.mock('@wailsio/runtime', () => ({
  Window: { OpenDevTools: openDevTools },
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

describe('DevToolsOverlay', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('opens Wails developer tools when clicked', async () => {
    const wrapper = mount(DevToolsOverlay)

    await wrapper.get('button').trigger('click')

    expect(openDevTools).toHaveBeenCalledOnce()
  })

  it('moves within the viewport and does not open developer tools after a drag', async () => {
    const wrapper = mount(DevToolsOverlay)
    const button = wrapper.get('button')
    const initialStyle = button.attributes('style')

    await button.trigger('pointerdown', { button: 0, pointerId: 1, clientX: 20, clientY: 20 })
    await button.trigger('pointermove', { pointerId: 1, clientX: 120, clientY: 80 })
    await button.trigger('pointerup', { pointerId: 1, clientX: 120, clientY: 80 })
    await button.trigger('click')

    expect(button.attributes('style')).not.toBe(initialStyle)
    expect(openDevTools).not.toHaveBeenCalled()
  })
})
