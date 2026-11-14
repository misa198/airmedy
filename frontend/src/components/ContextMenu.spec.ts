import { afterEach, describe, it, expect, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ContextMenu from './ContextMenu.vue'
import { readFileSync } from 'node:fs'

const menuSource = readFileSync('src/components/ContextMenu.vue', 'utf8')
const globalStyles = readFileSync('src/assets/index.css', 'utf8')
import type { ContextMenuItem } from '@/composables/useContextMenu'

const originalInnerWidth = Object.getOwnPropertyDescriptor(window, 'innerWidth')
const originalInnerHeight = Object.getOwnPropertyDescriptor(window, 'innerHeight')
const originalClientWidth = Object.getOwnPropertyDescriptor(document.documentElement, 'clientWidth')
const originalClientHeight = Object.getOwnPropertyDescriptor(document.documentElement, 'clientHeight')
const originalOffsetWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetWidth')
const originalOffsetHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetHeight')
const originalGetBoundingClientRect = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'getBoundingClientRect')

enableAutoUnmount(afterEach)

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
    if (originalOffsetWidth) Object.defineProperty(HTMLElement.prototype, 'offsetWidth', originalOffsetWidth)
    if (originalOffsetHeight) Object.defineProperty(HTMLElement.prototype, 'offsetHeight', originalOffsetHeight)
    if (originalGetBoundingClientRect) {
      Object.defineProperty(HTMLElement.prototype, 'getBoundingClientRect', originalGetBoundingClientRect)
    }
  })

  it('scales on enter and leave despite the WKWebView compositing transform', async () => {
    const style = document.createElement('style')
    const compositingRule = globalStyles.slice(globalStyles.indexOf('html:not(.platform-linux) [class*="bg-"]'), globalStyles.indexOf('/* Ensure backdrop-blur'))
    style.textContent = compositingRule + menuSource.split('<style scoped>')[1]!.split('</style>')[0]
    document.head.appendChild(style)
    const w = mount(ContextMenu, {
      props: { items: makeItems(), x: 100, y: 100, visible: false },
      attachTo: document.body,
      global: { stubs: { Teleport: true, transition: false } },
    })
    try {
      await w.setProps({ visible: true })
      const panel = w.get('.context-menu-panel').element
      expect(getComputedStyle(panel).transformOrigin).toBe('0px 0px')
      expect(getComputedStyle(panel).scale).toBe('1')
      for (const [active, phase] of [
        ['context-menu-enter-active', 'context-menu-enter-from'],
        ['context-menu-leave-active', 'context-menu-leave-to'],
      ] as const) {
        panel.classList.add(active, phase)
        expect(getComputedStyle(panel).transformOrigin).toBe('0px 0px')
        expect(getComputedStyle(panel).scale).toBe('0.8')
        expect(getComputedStyle(panel).transform).toBe('translate3d(0, 0, 0)')
        panel.classList.remove(active, phase)
        expect(getComputedStyle(panel).transformOrigin).toBe('0px 0px')
        expect(getComputedStyle(panel).scale).toBe('1')
      }
    } finally {
      w.unmount()
      style.remove()
    }
  })

  it('renders item labels', () => {
    const w = mountMenu()
    expect(w.text()).toContain('Play')
    expect(w.text()).toContain('Delete')
  })

  it('renders above the fullscreen player', () => {
    const w = mountMenu()
    expect(w.get('.fixed.inset-0').classes()).toContain('z-[1005]')
    expect(w.get('[class*="min-w-[200px]"]').classes()).toContain('z-[1006]')
  })

  it('applies danger class to danger items', () => {
    const w = mountMenu()
    const dangerEl = w.findAll('[class*="text-red"]')
    expect(dangerEl.length).toBeGreaterThan(0)
  })

  it('does not animate menu item hover states', () => {
    const w = mountMenu()
    const menuItems = w.findAll('[class*="cursor-default"]')
    expect(menuItems).not.toHaveLength(0)
    expect(menuItems.every(item => !item.classes().some(className => className.startsWith('transition-')))).toBe(true)
  })

  it('emits close when overlay is clicked', async () => {
    const w = mountMenu()
    const overlay = w.find('.fixed.inset-0')
    await overlay.trigger('click')
    expect(w.emitted('close')).toBeTruthy()
  })

  it('closes on Escape while allowing the fullscreen player to handle the same key', () => {
    const w = mountMenu()
    const closeFullscreen = vi.fn()
    window.addEventListener('keydown', closeFullscreen)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    expect(w.emitted('close')).toBeUndefined()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }))

    expect(w.emitted('close')).toHaveLength(1)
    expect(closeFullscreen).toHaveBeenCalledTimes(2)
    window.removeEventListener('keydown', closeFullscreen)
  })

  it('ignores Escape when hidden and removes its listener after unmount', async () => {
    const w = mountMenu({ visible: false })

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(w.emitted('close')).toBeUndefined()

    await w.setProps({ visible: true })
    await w.setProps({ visible: false })
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(w.emitted('close')).toBeUndefined()

    await w.setProps({ visible: true })
    w.unmount()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(w.emitted('close')).toBeUndefined()
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
    expect(w.find('.z-\\[1006\\]').exists()).toBe(false)
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
    expect(w.get('[class*="min-w-[180px]"]').classes()).toContain('z-[1007]')
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

    Object.defineProperty(HTMLElement.prototype, 'offsetWidth', { configurable: true, get: () => 200 })
    Object.defineProperty(HTMLElement.prototype, 'offsetHeight', { configurable: true, get: () => 140 })

    const w = mountMenu({ x: 1000, y: 750, visible: true })

    await nextTick()
    await nextTick()

    expect((w.vm as any).adjustedX).toBe(800)
    expect((w.vm as any).adjustedY).toBe(610)
    expect(w.get('.context-menu-panel').attributes('style')).toContain('transform-origin: 200px 140px')
    await w.setProps({ visible: false })
    expect((w.vm as any).transformOrigin).toBe('200px 140px')
  })

})
