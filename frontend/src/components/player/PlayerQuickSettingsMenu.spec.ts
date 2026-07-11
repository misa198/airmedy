import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import PlayerQuickSettingsMenu from './PlayerQuickSettingsMenu.vue'
import ContextMenu from '@/components/ContextMenu.vue'
import { useAppStore } from '@/stores/app'
import * as EQService from '../../../bindings/airmedy/internal/infra/wails/eqservice'
import type { ContextMenuItem } from '@/composables/useContextMenu'

vi.mock('../../../bindings/airmedy/internal/infra/wails/eqservice', () => ({
  GetAllProfiles: vi.fn(),
  GetActiveProfile: vi.fn(),
  ApplyProfile: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

const profiles = [
  { id: 'flat', name: 'Flat', is_active: true, is_default: true, bands: [] },
  { id: 'rock', name: 'Rock', is_active: false, is_default: true, bands: [] },
]

function mountMenu(appState = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/settings/:category?', name: 'settings', component: { template: '<div />' } }],
  })
  const wrapper = mount(PlayerQuickSettingsMenu, {
    global: {
      plugins: [
        router,
        createTestingPinia({
          createSpy: vi.fn,
          initialState: { app: appState },
        }),
      ],
      stubs: { Teleport: true },
    },
  })
  return { wrapper, router }
}

async function openMenu(wrapper: ReturnType<typeof mount>) {
  await (wrapper.vm as unknown as { open: (event: MouseEvent) => Promise<void> }).open(
    new MouseEvent('click', { clientX: 100, clientY: 100 }),
  )
  return wrapper.findComponent(ContextMenu).props('items') as ContextMenuItem[]
}

function getItem(items: ContextMenuItem[], label: string) {
  const item = items.find((entry) => entry.label === label)
  if (!item) throw new Error(`Missing menu item: ${label}`)
  return item
}

describe('PlayerQuickSettingsMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(EQService.GetAllProfiles).mockResolvedValue(profiles as never)
    vi.mocked(EQService.GetActiveProfile).mockResolvedValue(profiles[0] as never)
  })

  it('builds the quick settings and marks enabled values', async () => {
    const { wrapper } = mountMenu({
      preventSleepWhilePlaying: true,
      showPlayerIndicator: false,
      crossfadeSeconds: 4,
    })

    const items = await openMenu(wrapper)
    const preventSleepItem = getItem(items, 'settings.playback.prevent_sleep')
    const showIndicatorItem = getItem(items, 'settings.playback.show_player_indicator')
    const crossfadeItem = getItem(items, 'settings.playback.crossfade')
    const eqItem = getItem(items, 'settings.quick_menu.eq_presets')

    expect(items.map((item) => item.label).filter(Boolean)).toEqual([
      'settings.playback.prevent_sleep',
      'settings.playback.show_player_indicator',
      'settings.playback.crossfade',
      'settings.quick_menu.eq_presets',
    ])
    expect(preventSleepItem.iconRight).toBeTruthy()
    expect(showIndicatorItem.iconRight).toBeUndefined()
    expect(crossfadeItem.iconRight).toBeTruthy()
    expect(eqItem.children?.[0].iconRight).toBeTruthy() // enable_eq check
    expect(eqItem.children?.[2].iconRight).toBeTruthy() // flat profile check
  })

  it('uses app-store actions for the setting toggles and crossfade default', async () => {
    const { wrapper } = mountMenu({
      preventSleepWhilePlaying: false,
      showPlayerIndicator: true,
      crossfadeSeconds: 0,
    })
    const store = useAppStore()
    const items = await openMenu(wrapper)
    const preventSleepItem = getItem(items, 'settings.playback.prevent_sleep')
    const showIndicatorItem = getItem(items, 'settings.playback.show_player_indicator')
    const crossfadeItem = getItem(items, 'settings.playback.crossfade')

    await preventSleepItem.action?.()
    await showIndicatorItem.action?.()
    await crossfadeItem.action?.()

    expect(store.updatePreventSleepWhilePlaying).toHaveBeenCalledWith(true)
    expect(store.updateShowPlayerIndicator).toHaveBeenCalledWith(false)
    expect(store.updateCrossfadeSeconds).toHaveBeenCalledWith(4)
  })

  it('toggles the EQ enabled state when clicking the enable toggle', async () => {
    const { wrapper } = mountMenu({ eqEnabled: true })
    const store = useAppStore()
    const items = await openMenu(wrapper)
    const eqItems = getItem(items, 'settings.quick_menu.eq_presets').children!

    const toggleItem = eqItems[0]
    expect(toggleItem.label).toBe('settings.quick_menu.enable_eq')
    expect(toggleItem.iconRight).toBeTruthy()

    await toggleItem.action?.()
    expect(store.updateEQEnabled).toHaveBeenCalledWith(false)
  })

  it('applies an EQ profile, enables EQ, and links to the EQ section', async () => {
    const { wrapper, router } = mountMenu()
    const store = useAppStore()
    const push = vi.spyOn(router, 'push')
    const items = await openMenu(wrapper)
    const eqItems = getItem(items, 'settings.quick_menu.eq_presets').children!

    await eqItems[3].action?.() // Click Rock profile
    eqItems[5].action?.() // Click Go to settings

    expect(EQService.ApplyProfile).toHaveBeenCalledWith('rock')
    expect(store.updateEQEnabled).toHaveBeenCalledWith(true)
    expect(push).toHaveBeenCalledWith({
      name: 'settings',
      params: { category: 'playback' },
      query: { section: 'equalizer' },
    })
  })
})
