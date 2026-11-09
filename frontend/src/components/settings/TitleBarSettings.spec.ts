import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { Events } from '@wailsio/runtime'
import { Checkbox, Switch } from '@airmedy/ui'
import VirtualList from 'vue-virtual-sortable'
import * as SettingsService from '../../../bindings/airmedy/internal/infra/wails/settingsservice'
import { useAppStore } from '@/stores/app'
import { defaultTitleBarActions } from '@/lib/titleBar'
import TitleBarSettings from './TitleBarSettings.vue'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('../../../bindings/airmedy/internal/infra/wails/settingsservice', () => ({
  SaveSettings: vi.fn(), GetSettings: vi.fn(),
}))
vi.mock('../../../bindings/airmedy/internal/infra/wails/windowservice', () => ({ SetTitleBarTheme: vi.fn().mockResolvedValue(undefined) }))
vi.mock('../../../bindings/airmedy/internal/infra/wails/analysisservice', () => ({ GetWorkerCountInfo: vi.fn().mockResolvedValue({ count: 2, max: 4 }) }))
vi.mock('vue-virtual-sortable', () => ({ default: defineComponent({
  props: ['modelValue', 'handle', 'dataKey', 'size', 'keeps', 'disabled', 'ghostStyle', 'chosenClass', 'animation'],
  emits: ['update:modelValue'],
  setup(props, { slots }) {
    return () => h('div', props.modelValue.map((record: { id: string }, index: number) =>
      h('div', { key: record.id }, slots.item?.({ record, index })),
    ))
  },
}) }))

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => vi.fn()) },
  Create: {
    Nullable: (create: (value: unknown) => unknown) => (value: unknown) => value == null ? null : create(value),
    Array: (create: (value: unknown) => unknown) => (values: unknown[]) => (values ?? []).map(create),
    Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value),
    Map: () => (value: unknown) => value,
    Any: (value: unknown) => value,
  },
  Call: { ByID: vi.fn() },
}))

let store: ReturnType<typeof useAppStore>
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
  setActivePinia(createPinia())
  store = useAppStore()
  vi.mocked(SettingsService.SaveSettings).mockResolvedValue(undefined)
})
afterEach(() => { store.dispose(); vi.unstubAllGlobals() })

describe('TitleBarSettings', () => {
  it.each(['light', 'dark', 'black'])('renders five themed rows with a dedicated drag handle in %s', (theme) => {
    const wrapper = mount(TitleBarSettings, { attrs: { class: theme } })
    expect(wrapper.findAll('[data-action]')).toHaveLength(5)
    expect(wrapper.findAll('[role="checkbox"]').map(box => box.attributes('aria-checked')))
      .toEqual(['true', 'true', 'true', 'false', 'false'])
    expect(wrapper.getComponent(VirtualList).props()).toMatchObject({ handle: '.title-bar-action-handle', dataKey: 'id', size: 56, keeps: 5, ghostStyle: { display: 'none' }, chosenClass: 'title-bar-action-held', animation: 150 })
    expect(wrapper.findAll('.title-bar-action-handle')).toHaveLength(5)
    expect(wrapper.get('[data-action]').classes()).toContain('hover:bg-foreground/[0.04]')
    expect(wrapper.get('[data-action]').classes()).toContain('transition-colors')
    expect(wrapper.get('[data-action]').classes()).not.toContain('transition-all')
    wrapper.unmount()
  })

  it('locks the drag cursor until mouseup outside the list and cleans up on blur/unmount', async () => {
    const wrapper = mount(TitleBarSettings)
    const grip = wrapper.get('.title-bar-action-handle')
    await grip.trigger('mousedown', { button: 2 })
    expect(document.documentElement.classList.contains('title-bar-action-dragging')).toBe(false)
    await grip.trigger('mousedown', { button: 0 })
    expect(document.documentElement.classList.contains('title-bar-action-dragging')).toBe(true)
    await wrapper.get('[role="checkbox"]').trigger('mousemove')
    expect(document.documentElement.classList.contains('title-bar-action-dragging')).toBe(true)
    window.dispatchEvent(new MouseEvent('mouseup'))
    expect(document.documentElement.classList.contains('title-bar-action-dragging')).toBe(false)
    await grip.trigger('mousedown', { button: 0 })
    window.dispatchEvent(new Event('blur'))
    expect(document.documentElement.classList.contains('title-bar-action-dragging')).toBe(false)
    await grip.trigger('mousedown', { button: 0 })
    wrapper.unmount()
    expect(document.documentElement.classList.contains('title-bar-action-dragging')).toBe(false)
  })

  it.each(['navigation', 'checkbox'])('keeps control appearance, cursors and DOM stable while %s is saving', async (control) => {
    let finishSave!: () => void
    vi.mocked(SettingsService.SaveSettings).mockReturnValueOnce(
      new Promise<void>(resolve => { finishSave = resolve }) as ReturnType<typeof SettingsService.SaveSettings>,
    )
    const wrapper = mount(TitleBarSettings)
    const navigationSwitch = wrapper.getComponent(Switch)
    const switchElement = navigationSwitch.element
    const checkboxes = wrapper.findAllComponents(Checkbox)
    const elements = checkboxes.map(checkbox => checkbox.element)
    const checked = checkboxes.map(checkbox => checkbox.props('checked'))
    if (control === 'navigation') {
      wrapper.getComponent(Switch).vm.$emit('update:modelValue', false)
    } else {
      checked[4] = !checked[4]
      await checkboxes[4]!.trigger('click')
    }
    await wrapper.vm.$nextTick()
    expect(navigationSwitch.props('disabled')).toBe(true)
    expect(navigationSwitch.classes()).toContain('opacity-100!')
    expect((navigationSwitch.element as HTMLElement).style.cursor).toBe('pointer')
    expect(wrapper.getComponent(Switch).element).toBe(switchElement)
    for (const checkbox of wrapper.findAllComponents(Checkbox)) {
      expect(checkbox.props('disabled')).toBe(true)
      expect(checkbox.classes()).toContain('opacity-100!')
      expect((checkbox.element as HTMLElement).style.cursor).toBe('pointer')
    }
    expect(wrapper.findAllComponents(Checkbox).map(checkbox => checkbox.element)).toEqual(elements)
    expect(wrapper.findAllComponents(Checkbox).map(checkbox => checkbox.props('checked'))).toEqual(checked)
    await checkboxes[4]!.trigger('click')
    await navigationSwitch.trigger('click')
    expect(SettingsService.SaveSettings).toHaveBeenCalledTimes(1)
    finishSave()
    await flushPromises()
    expect(wrapper.findAllComponents(Checkbox).map(checkbox => checkbox.props('disabled'))).toEqual([false, false, false, false, false])
    expect((checkboxes[4]!.element as HTMLElement).style.cursor).toBe('pointer')
    expect(navigationSwitch.props('disabled')).toBe(false)
    expect((navigationSwitch.element as HTMLElement).style.cursor).toBe('pointer')
    wrapper.unmount()
  })

  it('saves the navigation switch and checkbox changes immediately', async () => {
    const wrapper = mount(TitleBarSettings)
    wrapper.getComponent(Switch).vm.$emit('update:modelValue', false)
    await flushPromises()
    expect(store.titleBarShowNavigation).toBe(false)
    await wrapper.get('[data-action="remote"] [role="checkbox"]').trigger('click')
    await flushPromises()
    expect(store.titleBarActions.find(action => action.id === 'remote')?.visible).toBe(true)
    expect(SettingsService.SaveSettings).toHaveBeenLastCalledWith(expect.objectContaining({
      title_bar_show_navigation: false, title_bar_actions: store.titleBarActions,
    }))
    const bubbledKeydown = vi.fn()
    wrapper.element.addEventListener('keydown', bubbledKeydown)
    await wrapper.get('[data-action="remote"] [role="checkbox"]').trigger('keydown', { key: ' ', code: 'Space' })
    await flushPromises()
    expect(store.titleBarActions.find(action => action.id === 'remote')?.visible).toBe(false)
    expect(bubbledKeydown).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('persists library reorder events including hidden items, and supports keyboard reorder', async () => {
    const wrapper = mount(TitleBarSettings)
    const reordered = [...store.titleBarActions].reverse()
    wrapper.getComponent(VirtualList).vm.$emit('update:modelValue', reordered)
    await flushPromises()
    expect(store.titleBarActions.map(action => action.id)).toEqual(['remote', 'playback', 'settings', 'library', 'mobile'])
    expect(store.titleBarActions[0]?.visible).toBe(false)
    await wrapper.get('[data-action="remote"] button').trigger('keydown', { key: 'ArrowDown' })
    await flushPromises()
    expect(store.titleBarActions[1]?.id).toBe('remote')
    await wrapper.get('[data-action="remote"] button').trigger('keydown', { key: 'ArrowUp' })
    await flushPromises()
    const count = vi.mocked(SettingsService.SaveSettings).mock.calls.length
    await wrapper.get('[data-action="remote"] button').trigger('keydown', { key: 'ArrowUp' })
    expect(SettingsService.SaveSettings).toHaveBeenCalledTimes(count)
    wrapper.unmount()
  })

  it('restores the previous configuration and shows an error after a rejected save', async () => {
    const log = vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.mocked(SettingsService.SaveSettings).mockRejectedValueOnce(new Error('disk full'))
    const wrapper = mount(TitleBarSettings)
    await wrapper.get('[data-action="mobile"] [role="checkbox"]').trigger('click')
    await flushPromises()
    expect(store.titleBarActions).toEqual(defaultTitleBarActions())
    expect(wrapper.get('[role="alert"]').text()).toBe('settings.title_bar.save_error')
    await wrapper.get('[data-action="mobile"] [role="checkbox"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
    log.mockRestore()
  })

  it('loads persisted settings and preserves them when saving an unrelated setting', async () => {
    const actions = defaultTitleBarActions().reverse().map(action => ({ ...action, visible: false }))
    vi.mocked(SettingsService.GetSettings).mockResolvedValue({ title_bar_show_navigation: false, title_bar_actions: actions } as any)
    await store.loadSettings(true)
    expect(store.titleBarShowNavigation).toBe(false)
    expect(store.titleBarActions).toEqual(actions)
    await store.updateStartAtLogin(true)
    expect(SettingsService.SaveSettings).toHaveBeenLastCalledWith(expect.objectContaining({ title_bar_show_navigation: false, title_bar_actions: actions }))
  })

  it('receives cross-window changes without saving and removes the listener', () => {
    const index = vi.mocked(Events.On).mock.calls.findIndex(([name]) => name === 'settings:title-bar')
    const handler = vi.mocked(Events.On).mock.calls[index]![1]
    const off = vi.mocked(Events.On).mock.results[index]!.value
    const actions = defaultTitleBarActions().reverse()
    handler({ data: { show_navigation: false, actions } } as Events.WailsEvent)
    expect(store.titleBarShowNavigation).toBe(false)
    expect(store.titleBarActions).toEqual(actions)
    expect(SettingsService.SaveSettings).not.toHaveBeenCalled()
    store.dispose()
    expect(off).toHaveBeenCalledOnce()
  })
})
