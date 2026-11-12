import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { Events } from '@wailsio/runtime'
import VirtualList from 'vue-virtual-sortable'
import * as SettingsService from '../../../bindings/airmedy/internal/infra/wails/settingsservice'
import { useAppStore } from '@/stores/app'
import { defaultSidebarItems } from '@/lib/sidebar'
import SidebarSettings from './SidebarSettings.vue'

vi.mock('../../../bindings/airmedy/internal/infra/wails/settingsservice', () => ({ SaveSettings: vi.fn(), GetSettings: vi.fn() }))
vi.mock('../../../bindings/airmedy/internal/infra/wails/windowservice', () => ({ SetTitleBarTheme: vi.fn().mockResolvedValue(undefined) }))
vi.mock('../../../bindings/airmedy/internal/infra/wails/analysisservice', () => ({ GetWorkerCountInfo: vi.fn().mockResolvedValue({ count: 2, max: 4 }) }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('vue-virtual-sortable', () => ({ default: defineComponent({
  props: ['modelValue', 'handle', 'dataKey', 'size', 'keeps', 'disabled', 'ghostStyle', 'chosenClass', 'animation'],
  emits: ['update:modelValue'],
  setup(props, { slots }) {
    return () => h('div', props.modelValue.map((record: { id: string }, index: number) => h('div', { key: record.id }, slots.item?.({ record, index }))))
  },
}) }))
vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => vi.fn()) },
  Create: { Nullable: (create: (value: unknown) => unknown) => (value: unknown) => value == null ? null : create(value), Array: (create: (value: unknown) => unknown) => (values: unknown[]) => (values ?? []).map(create), Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value), Map: () => (value: unknown) => value, Any: (value: unknown) => value },
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

describe('SidebarSettings', () => {
  it('renders all seven navigation items without a visibility control for Home', () => {
    const wrapper = mount(SidebarSettings)
    expect(wrapper.findAll('[data-action]')).toHaveLength(7)
    expect(wrapper.find('[data-action="home"] [role="checkbox"]').exists()).toBe(false)
    expect(wrapper.findAll('[role="checkbox"]')).toHaveLength(6)
    expect(wrapper.getComponent(VirtualList).props()).toMatchObject({ handle: '.sortable-visibility-list-handle', keeps: 7 })
    wrapper.unmount()
  })

  it('persists visibility and reorder changes but never changes Home visibility', async () => {
    const wrapper = mount(SidebarSettings)
    await wrapper.get('[data-action="recently_added"] [role="checkbox"]').trigger('click')
    await flushPromises()
    expect(store.sidebarItems.find(item => item.id === 'recently_added')?.visible).toBe(false)
    const reordered = [...store.sidebarItems].reverse()
    wrapper.getComponent(VirtualList).vm.$emit('update:modelValue', reordered)
    await flushPromises()
    expect(store.sidebarItems.map(item => item.id)).toEqual(reordered.map(item => item.id))
    expect(SettingsService.SaveSettings).toHaveBeenLastCalledWith(expect.objectContaining({ sidebar_items: reordered }))
    wrapper.unmount()
  })

  it('restores the prior sidebar after a failed save', async () => {
    vi.mocked(SettingsService.SaveSettings).mockRejectedValueOnce(new Error('disk full'))
    const wrapper = mount(SidebarSettings)
    await wrapper.get('[data-action="artists"] [role="checkbox"]').trigger('click')
    await flushPromises()
    expect(store.sidebarItems).toEqual(defaultSidebarItems())
    expect(wrapper.get('[role="alert"]').text()).toBe('settings.sidebar.save_error')
    wrapper.unmount()
  })

  it('receives sidebar changes from another window without saving', () => {
    const index = vi.mocked(Events.On).mock.calls.findIndex(([name]) => name === 'settings:sidebar')
    const handler = vi.mocked(Events.On).mock.calls[index]![1]
    const items = defaultSidebarItems().reverse()
    handler({ data: items } as Events.WailsEvent)
    expect(store.sidebarItems).toEqual(items)
    expect(SettingsService.SaveSettings).not.toHaveBeenCalled()
  })
})
