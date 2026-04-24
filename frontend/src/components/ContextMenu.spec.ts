import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ContextMenu from './ContextMenu.vue'
import type { ContextMenuItem } from '@/composables/useContextMenu'

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
})
