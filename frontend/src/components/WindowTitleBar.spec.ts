import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { Copy, Square } from '@lucide/vue'
import { Window } from '@wailsio/runtime'
import { createRouter, createWebHashHistory, type Router } from 'vue-router'
import WindowTitleBar from './WindowTitleBar.vue'

vi.mock('@/stores/device', () => ({ useDeviceStore: () => deviceStore }))
vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRouter: () => activeRouter,
}))
vi.mock('@wailsio/runtime', () => ({
  Window: { Minimise: vi.fn(), Close: vi.fn() },
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
const deviceStore = reactive({ isWindowMaximized: false, toggleMaximize: vi.fn() })
const push = vi.fn()
const back = vi.fn()
const forward = vi.fn()
const removeAfterEach = vi.fn()
const afterEach = vi.fn(() => removeAfterEach)
const routerMock = { push, back, forward, afterEach }
let activeRouter: Pick<Router, 'push' | 'back' | 'forward' | 'afterEach'> = routerMock

function windowButtons(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('button').slice(-3)
}

describe('WindowTitleBar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    deviceStore.isWindowMaximized = false
    activeRouter = routerMock
    window.history.replaceState(null, '', '/')
  })

  it.each(['light', 'dark', 'black'])('keeps the themed 32px drag region in %s theme', async (theme) => {
    const wrapper = mount(WindowTitleBar, { attrs: { class: theme } })

    expect(wrapper.classes()).toEqual(expect.arrayContaining(['h-8', 'shrink-0', 'bg-background']))
    expect(wrapper.element.style.getPropertyValue('--wails-draggable')).toBe('drag')
    const logo = wrapper.get('span[aria-hidden="true"]')
    expect(wrapper.element.firstElementChild).toBe(logo.element)
    expect(logo.classes()).toEqual(expect.arrayContaining(['bg-foreground', 'size-6', 'self-center']))
    expect(logo.attributes('style')).toContain('mask: url(/airmedy.svg) center / contain no-repeat')
    expect(wrapper.get('div.flex.h-full').attributes('style')).toContain('--wails-draggable: no-drag')
    const buttons = windowButtons(wrapper)
    for (const button of buttons) {
      expect(button.classes()).toContain('text-foreground')
      expect(button.classes()).not.toContain('text-foreground/40')
      expect(button.classes()).toContain('cursor-default')
      expect(button.get('svg').attributes('stroke-width')).toBe('1.5')
    }
    expect(buttons[0].get('svg').classes()).toContain('size-3.5')
    expect(buttons[1].get('svg').classes()).toContain('size-3')
    expect(buttons[2].get('svg').classes()).toContain('size-4')
    expect(buttons[0].classes()).toEqual(buttons[1].classes())
    expect(buttons[0].classes()).toContain('hover:bg-foreground/[0.04]')
    expect(buttons[2].classes()).toContain('hover:bg-window-close')
    expect(buttons[2].classes()).toContain('hover:text-window-close-foreground')
    const navigation = wrapper.findAll('button').slice(0, 2)
    expect(logo.element.nextElementSibling).toBe(navigation[0].element.parentElement)
    expect(navigation[0].element.parentElement?.classList.contains('mr-auto')).toBe(true)
    for (const button of navigation) {
      expect(button.classes()).toEqual(expect.arrayContaining(['text-foreground', 'disabled:opacity-30']))
      expect((button.element as HTMLButtonElement).disabled).toBe(true)
    }
    for (const button of wrapper.findAll('button').slice(2, 5)) {
      expect(button.classes().some(name => name.startsWith('hover:bg-'))).toBe(false)
    }

    await wrapper.trigger('dblclick')
    expect(deviceStore.toggleMaximize).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('connects accessible controls to window actions without bubbling double-clicks', async () => {
    const wrapper = mount(WindowTitleBar)
    const buttons = windowButtons(wrapper)
    expect(buttons.map(button => button.attributes('aria-label'))).toEqual([
      'common.minimize', 'common.maximize', 'common.close',
    ])
    for (const button of buttons) {
      expect(button.attributes('title')).toBe(button.attributes('aria-label'))
      await button.trigger('dblclick')
    }
    expect(deviceStore.toggleMaximize).not.toHaveBeenCalled()

    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    await buttons[2].trigger('click')
    expect(Window.Minimise).toHaveBeenCalledOnce()
    expect(deviceStore.toggleMaximize).toHaveBeenCalledOnce()
    expect(Window.Close).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('switches the maximize icon and label to restore when maximized', async () => {
    const wrapper = mount(WindowTitleBar)
    expect(wrapper.findComponent(Square).exists()).toBe(true)

    deviceStore.isWindowMaximized = true
    await wrapper.vm.$nextTick()

    expect(wrapper.findComponent(Square).exists()).toBe(false)
    expect(wrapper.findComponent(Copy).exists()).toBe(true)
    expect(windowButtons(wrapper)[1].attributes('aria-label')).toBe('common.restore')
    wrapper.unmount()
  })

  it.each([
    [0, 'Minimise', 'Failed to minimize window'],
    [2, 'Close', 'Failed to close window'],
  ] as const)('reports a rejected window action from button %s', async (index, method, message) => {
    const error = new Error('Unavailable')
    vi.mocked(Window[method]).mockRejectedValueOnce(error)
    const log = vi.spyOn(console, 'error').mockImplementation(() => {})
    const wrapper = mount(WindowTitleBar)

    await windowButtons(wrapper)[index].trigger('click')
    expect(log).toHaveBeenCalledWith(message, error)
    wrapper.unmount()
    log.mockRestore()
  })

  it('places the shortcuts before window controls and opens their settings pages', async () => {
    const wrapper = mount(WindowTitleBar)
    expect(wrapper.findAll('button')).toHaveLength(8)
    const shortcuts = wrapper.findAll('button').slice(2, 5)
    expect(shortcuts.map(button => button.attributes('aria-label'))).toEqual([
      'settings.categories.mobile_devices',
      'settings.categories.library', 'settings.title',
    ])
    for (const button of shortcuts) {
      expect(button.attributes('title')).toBe(button.attributes('aria-label'))
      await button.trigger('dblclick')
      await button.trigger('click')
    }
    expect(push.mock.calls.map(([path]) => path)).toEqual([
      '/settings/mobile-devices', '/settings/library', '/settings',
    ])
    expect(deviceStore.toggleMaximize).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('disables unavailable navigation and removes the router hook on unmount', async () => {
    const wrapper = mount(WindowTitleBar)
    const navigation = wrapper.findAll('button').slice(0, 2)
    expect(navigation.map(button => button.attributes('aria-label'))).toEqual(['common.back', 'common.forward'])
    for (const button of navigation) {
      expect(button.attributes('title')).toBe(button.attributes('aria-label'))
      await button.trigger('click')
    }
    expect(back).not.toHaveBeenCalled()
    expect(forward).not.toHaveBeenCalled()
    wrapper.unmount()
    expect(removeAfterEach).toHaveBeenCalledOnce()
  })

  it('connects available navigation without maximizing on double-click', async () => {
    window.history.replaceState({ back: '/', forward: '/albums' }, '')
    const wrapper = mount(WindowTitleBar)
    for (const button of wrapper.findAll('button').slice(0, 2)) {
      expect((button.element as HTMLButtonElement).disabled).toBe(false)
      await button.trigger('dblclick')
      await button.trigger('click')
    }
    expect(back).toHaveBeenCalledOnce()
    expect(forward).toHaveBeenCalledOnce()
    expect(deviceStore.toggleMaximize).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('follows real hash history through back, forward, replace, branching and remount', async () => {
    const history = createWebHashHistory()
    const router = createRouter({
      history,
      routes: ['/', '/albums', '/tracks', '/settings'].map(path => ({ path, component: { template: '<div />' } })),
    })
    activeRouter = router
    let wrapper: ReturnType<typeof mount> | undefined
    const expectNavigation = (canBack: boolean, canForward: boolean) => {
      expect(wrapper!.findAll('button').slice(0, 2).map(button => (button.element as HTMLButtonElement).disabled))
        .toEqual([!canBack, !canForward])
    }
    try {
      await router.push('/')
      await router.isReady()
      wrapper = mount(WindowTitleBar)
      expectNavigation(false, false)
      await router.push('/albums')
      expectNavigation(true, false)
      await wrapper.get('[aria-label="common.back"]').trigger('click')
      await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/'))
      expectNavigation(false, true)
      await wrapper.get('[aria-label="common.forward"]').trigger('click')
      await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/albums'))
      expectNavigation(true, false)
      await router.replace('/tracks')
      await router.push('/tracks')
      expectNavigation(true, false)
      router.back()
      await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/'))
      expectNavigation(false, true)
      wrapper.unmount()
      wrapper = mount(WindowTitleBar)
      expectNavigation(false, true)
      await router.push('/settings')
      expectNavigation(true, false)
      router.back()
      await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/'))
      expectNavigation(false, true)
    } finally {
      wrapper?.unmount()
      history.destroy()
    }
  })

})
