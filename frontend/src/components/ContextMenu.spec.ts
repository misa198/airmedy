import { afterEach, describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ContextMenu from './ContextMenu.vue'
import type { ContextMenuItem } from '@/composables/useContextMenu'

const originalInnerWidth = Object.getOwnPropertyDescriptor(window, 'innerWidth')
const originalInnerHeight = Object.getOwnPropertyDescriptor(window, 'innerHeight')
const originalClientWidth = Object.getOwnPropertyDescriptor(document.documentElement, 'clientWidth')
const originalClientHeight = Object.getOwnPropertyDescriptor(document.documentElement, 'clientHeight')
const originalGetBoundingClientRect = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'getBoundingClientRect')

const makeItems = (): ContextMenuItem[] => [
  { label: 'Play', action: vi.fn() },
  { separator: true },
  { label: 'Delete', danger: true, action: vi.fn() },
  { label: 'Disabled', disabled: true, action: vi.fn() },
]

function mountMenu(props: Record<string, unknown> = {}) {
  return mount(ContextMenu, {
    props: { items: makeItems(), x: 100, y: 100, visible: true, ...props },
    global: { stubs: { Teleport: true } },
  })
}

describe('ContextMenu', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()

    if (originalInnerWidth) {
      Object.defineProperty(window, 'innerWidth', originalInnerWidth)
    }
    if (originalInnerHeight) {
      Object.defineProperty(window, 'innerHeight', originalInnerHeight)
    }
    if (originalClientWidth) {
      Object.defineProperty(document.documentElement, 'clientWidth', originalClientWidth)
    }
    if (originalClientHeight) {
      Object.defineProperty(document.documentElement, 'clientHeight', originalClientHeight)
    }
    if (originalGetBoundingClientRect) {
      Object.defineProperty(HTMLElement.prototype, 'getBoundingClientRect', originalGetBoundingClientRect)
    }
  })

  it('renders item labels', () => {
    const w = mountMenu()
    expect(w.text()).toContain('Play')
    expect(w.text()).toContain('Delete')
  })

  it('applies danger class to danger items', () => {
    const w = mountMenu()
    const dangerEl = w.findAll('[class*="text-red"]')
    expect(dangerEl.length).toBeGreaterThan(0)
  })

  it('emits close when overlay is clicked', async () => {
    const w = mountMenu()
    const overlay = w.find('.fixed.inset-0')
    await overlay.trigger('click')
    expect(w.emitted('close')).toBeTruthy()
  })

  it('calls action and emits close when item is clicked', async () => {
    const action = vi.fn()
    const w = mount(ContextMenu, {
      props: { items: [{ label: 'Act', action }], x: 0, y: 0, visible: true },
      global: { stubs: { Teleport: true } },
    })
    const itemEl = w.findAll('[class*="flex items-center"]').find(el => el.text().includes('Act'))
    await itemEl!.trigger('click')
    expect(action).toHaveBeenCalled()
    expect(w.emitted('close')).toBeTruthy()
  })

  it('does not call action for disabled items', async () => {
    const action = vi.fn()
    const w = mount(ContextMenu, {
      props: { items: [{ label: 'Noop', disabled: true, action }], x: 0, y: 0, visible: true },
      global: { stubs: { Teleport: true } },
    })
    const itemEl = w.findAll('[class*="flex items-center"]').find(el => el.text().includes('Noop'))
    await itemEl!.trigger('click')
    expect(action).not.toHaveBeenCalled()
  })

  it('renders nothing when not visible', () => {
    const w = mountMenu({ visible: false })
    expect(w.find('.z-\\[999\\]').exists()).toBe(false)
  })

  it('renders separators inside a submenu without making them clickable items', async () => {
    const w = mount(ContextMenu, {
      props: {
        items: [{ label: 'EQ presets', children: [{ label: 'Flat' }, { separator: true }, { label: 'Settings' }] }],
        x: 0,
        y: 0,
        visible: true,
      },
      global: { stubs: { Teleport: true } },
    })

    await w.find('[class*="cursor-default"]').trigger('mouseenter')

    expect(w.text()).toContain('Flat')
    expect(w.text()).toContain('Settings')
    expect(w.findAll('.border-t.border-border-glass').length).toBeGreaterThan(0)
  })

  it('updates position when x and y props change', async () => {
    const w = mountMenu({ visible: true, x: 100, y: 100 })
    // In Vue test-utils, accessing internal component state is via w.vm
    // We expect initial adjustedX/Y to be 100
    expect((w.vm as any).adjustedX).toBe(100)
    expect((w.vm as any).adjustedY).toBe(100)

    await w.setProps({ x: 200, y: 300 })
    expect((w.vm as any).adjustedX).toBe(200)
    expect((w.vm as any).adjustedY).toBe(300)
  })

  it('keeps the menu inside the content viewport when a scrollbar reduces usable width', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1200 })
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 800 })
    Object.defineProperty(document.documentElement, 'clientWidth', { configurable: true, get: () => 1100 })
    Object.defineProperty(document.documentElement, 'clientHeight', { configurable: true, get: () => 800 })
    Object.defineProperty(HTMLElement.prototype, 'getBoundingClientRect', {
      configurable: true,
      value: vi.fn(() => ({
        x: 0,
        y: 0,
        top: 0,
        left: 0,
        right: 150,
        bottom: 100,
        width: 150,
        height: 100,
        toJSON: () => ({}),
      })),
    })

    const w = mountMenu({ x: 1000, y: 100, visible: true })

    await nextTick()
    await nextTick()

    expect((w.vm as any).adjustedX).toBe(850)
    expect((w.vm as any).adjustedY).toBe(100)
  })
})
