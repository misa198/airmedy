import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useAppStore } from '@/stores/app'
import { defaultSidebarItems } from '@/lib/sidebar'
import SidebarNav from './SidebarNav.vue'

vi.mock('../../bindings/airmedy/internal/infra/wails/libraryservice', () => ({ GetAllTracks: vi.fn() }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => vi.fn()) },
  Create: { Nullable: (create: (value: unknown) => unknown) => (value: unknown) => value == null ? null : create(value), Array: (create: (value: unknown) => unknown) => (values: unknown[]) => (values ?? []).map(create), Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value), Map: () => (value: unknown) => value, Any: (value: unknown) => value },
  Call: { ByID: vi.fn() },
}))

describe('SidebarNav', () => {
  beforeEach(() => { setActivePinia(createPinia()) })

  it('renders the saved visible items in their saved order', () => {
    const appStore = useAppStore()
    appStore.sidebarItems = defaultSidebarItems().reverse().map(item => ({ ...item, visible: item.id !== 'genres' }))
    const wrapper = mount(SidebarNav, {
      global: { stubs: { SidebarItem: { props: ['label'], template: '<div data-item>{{ label }}</div>' } } },
    })
    expect(wrapper.findAll('[data-item]').map(item => item.text())).toEqual([
      'sidebar.composers', 'sidebar.tracks', 'sidebar.albums', 'sidebar.artists', 'sidebar.recently_added', 'sidebar.home',
    ])
  })
})
