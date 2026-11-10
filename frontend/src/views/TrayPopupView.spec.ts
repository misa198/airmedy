import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { Events } from '@wailsio/runtime'
import TrayPopupView from './TrayPopupView.vue'
import { MarqueeText } from '@airmedy/ui'
import LivingArtworkBackground from '@/components/LivingArtworkBackground.vue'
import { RepeatMode } from '../../bindings/airmedy/internal/domain/models'

const player = reactive({
  currentTrack: null as null | { id: string; title: string; artists: { name: string }[]; is_favorite: boolean },
  artworkUrlSm: null as string | null,
  theme: null as null | { backdrop: string },
  duration: 0,
  position: 0,
  progressPercent: 0,
  isPlaying: false,
  shuffle: false,
  repeatMode: RepeatMode.RepeatModeOff,
  seek: vi.fn(),
  setShuffle: vi.fn(),
  previous: vi.fn(),
  togglePlayPause: vi.fn(),
  next: vi.fn(),
  cycleRepeat: vi.fn(),
})

const toggleFavorite = vi.fn()
const app = reactive({ showPlayerIndicator: false, trayBackgroundMode: 'living' })

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/stores/player', () => ({ usePlayerStore: () => player }))
vi.mock('@/stores/app', () => ({ useAppStore: () => app }))
vi.mock('../../bindings/airmedy/internal/infra/wails/windowservice', () => ({
  ShowCurrent: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/stores/favorites', () => ({
  useFavoritesStore: () => ({
    isFavorite: (track: { is_favorite: boolean }) => track.is_favorite,
    toggle: toggleFavorite,
  }),
}))
vi.mock('@wailsio/runtime', () => ({
  Events: { Emit: vi.fn(), On: vi.fn(() => vi.fn()) },
  Create: {
    Nullable: (fn: (value: unknown) => unknown) => (value: unknown) => value == null ? null : fn(value),
    Array: (fn: (value: unknown) => unknown) => (values: unknown[]) => (values ?? []).map(fn),
    Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value),
    Map: () => (value: unknown) => value,
  },
}))

vi.mock('@/components/LivingArtworkBackground.vue', () => ({
  default: { name: 'LivingArtworkBackground', props: ['theme', 'isPlaying'], template: '<div />' },
}))

describe('TrayPopupView', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
    player.currentTrack = null
    player.artworkUrlSm = null
    player.theme = null
    player.duration = 0
    player.position = 0
    player.progressPercent = 0
    player.isPlaying = false
    player.shuffle = false
    player.repeatMode = RepeatMode.RepeatModeOff
    app.showPlayerIndicator = false
    app.trayBackgroundMode = 'living'
    vi.clearAllMocks()
  })

  it('updates the solid tint when the track palette changes', async () => {
    const frames: FrameRequestCallback[] = []
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => frames.push(callback))
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    app.trayBackgroundMode = 'solid'
    player.theme = { backdrop: '#112233' }
    const wrapper = mount(TrayPopupView)
    const visibility = vi.mocked(Events.On).mock.calls.find(([name]) => name === 'tray-popup:visibility')?.[1]
    expect(visibility).toBeDefined()
    expect(wrapper.classes()).not.toContain('tray-solid-tint-ready')
    visibility?.({ data: true } as Events.WailsEvent)
    await nextTick()
    frames.shift()?.(0)
    frames.shift()?.(16)
    await nextTick()
    expect(wrapper.classes()).toContain('tray-solid-tint-ready')
    player.theme = { backdrop: '#445566' }
    await nextTick()
    expect(wrapper.attributes('style')).toContain('rgba(68, 85, 102, 0.4)')
    expect(wrapper.classes()).toContain('tray-solid-tint-ready')
    visibility?.({ data: false } as Events.WailsEvent)
    await nextTick()
    expect(wrapper.classes()).not.toContain('tray-solid-tint-ready')
    player.theme = { backdrop: '#778899' }
    await nextTick()
    visibility?.({ data: true } as Events.WailsEvent)
    await nextTick()
    expect(wrapper.attributes('style')).toContain('rgba(119, 136, 153, 0.4)')
    expect(wrapper.classes()).not.toContain('tray-solid-tint-ready')
    frames.shift()?.(32)
    frames.shift()?.(48)
    await nextTick()
    expect(wrapper.classes()).toContain('tray-solid-tint-ready')
    wrapper.unmount()
    vi.unstubAllGlobals()
  })

  it('shows an empty state with disabled controls', () => {
    const wrapper = mount(TrayPopupView)
    expect(wrapper.text()).toContain('player.not_playing')
    expect(wrapper.findAll('button').every(button => button.attributes('disabled') !== undefined)).toBe(true)
    expect(wrapper.find('input[type="range"]').exists()).toBe(false)
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(Events.Emit).toHaveBeenCalledWith('tray-popup:hide')
    wrapper.unmount()
  })

  it('shows track information and wires the five controls', async () => {
    player.currentTrack = { id: 'one', title: 'Song', artists: [{ name: 'Artist' }], is_favorite: false }
    player.duration = 120
    player.position = 30
    player.progressPercent = 25
    const wrapper = mount(TrayPopupView)
    expect(wrapper.text()).toContain('Song')
    expect(wrapper.text()).toContain('Artist')
    expect(wrapper.findAllComponents(MarqueeText).map(component => component.props('text'))).toEqual(['Song', 'Artist'])
    expect(wrapper.get('[data-test="tray-time-labels"]').findAll('span').map(label => label.text())).toEqual(['0:30', '2:00'])
    expect(wrapper.get('[data-test="tray-progress"]').element.children[1]).toBe(wrapper.get('[data-test="tray-time-labels"]').element)
    expect(wrapper.get('button[aria-label="context_menu.add_to_favorites"]').classes()).toEqual(expect.arrayContaining(['h-7', 'w-7']))

    await wrapper.get('button[aria-label="player.shuffle"]').trigger('click')
    await wrapper.get('button[aria-label="player.previous"]').trigger('click')
    await wrapper.get('button[aria-label="player.play"]').trigger('click')
    await wrapper.get('button[aria-label="player.next"]').trigger('click')
    await wrapper.get('button[aria-label="player.repeat"]').trigger('click')
    expect(player.setShuffle).toHaveBeenCalledWith(true)
    expect(player.previous).toHaveBeenCalledOnce()
    expect(player.togglePlayPause).toHaveBeenCalledOnce()
    expect(player.next).toHaveBeenCalledOnce()
    expect(player.cycleRepeat).toHaveBeenCalledOnce()

    await wrapper.get('button[aria-label="context_menu.add_to_favorites"]').trigger('click')
    expect(toggleFavorite).toHaveBeenCalledWith('one')
    wrapper.unmount()
  })

  it('shows living artwork while focused and releases it when hidden', async () => {
    player.theme = { backdrop: '#112233' }
    player.isPlaying = true
    const wrapper = mount(TrayPopupView)
    window.dispatchEvent(new Event('focus'))
    await nextTick()
    const background = wrapper.getComponent(LivingArtworkBackground)
    expect(background.props('theme')).toEqual(player.theme)
    expect(background.props('isPlaying')).toBe(true)
    app.trayBackgroundMode = 'solid'
    await nextTick()
    expect(wrapper.findComponent(LivingArtworkBackground).exists()).toBe(false)
    expect(wrapper.get('.tray-artwork-background').attributes('style')).toContain('rgba(17, 34, 51, 0.4)')
    app.trayBackgroundMode = 'living'
    await nextTick()
    expect(wrapper.findComponent(LivingArtworkBackground).exists()).toBe(true)
    window.dispatchEvent(new Event('blur'))
    await nextTick()
    expect(wrapper.findComponent(LivingArtworkBackground).exists()).toBe(false)
    wrapper.unmount()
  })

  it('uses the app theme without artwork layers in monochrome mode', async () => {
    player.theme = { backdrop: '#112233' }
    const wrapper = mount(TrayPopupView)
    window.dispatchEvent(new Event('focus'))
    app.trayBackgroundMode = 'monochrome'
    await nextTick()
    expect(wrapper.classes()).toContain('tray-monochrome')
    expect(wrapper.findComponent(LivingArtworkBackground).exists()).toBe(false)
    expect(wrapper.attributes('style') ?? '').not.toContain('--fullscreen-player-artwork-tint')
    app.trayBackgroundMode = 'living'
    await nextTick()
    expect(wrapper.classes()).not.toContain('tray-monochrome')
    expect(wrapper.findComponent(LivingArtworkBackground).exists()).toBe(true)
    wrapper.unmount()
  })

  it('seeks and reflects playing, shuffle and repeat-one state', async () => {
    player.currentTrack = { id: 'one', title: 'Song', artists: [], is_favorite: true }
    player.duration = 120
    player.isPlaying = true
    player.shuffle = true
    player.repeatMode = RepeatMode.RepeatModeOne
    player.theme = { backdrop: '#336699' }
    app.showPlayerIndicator = true
    const wrapper = mount(TrayPopupView)
    expect(wrapper.get('.tray-artwork-background').attributes('style')).toContain('rgba(51, 102, 153, 0.4)')
    expect(wrapper.find('button[aria-label="player.pause"]').exists()).toBe(true)
    expect(wrapper.get('button[aria-label="player.shuffle"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('button[aria-label="player.repeat"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('button[aria-label="player.shuffle"] span').classes()).toContain('bg-mini-player-pill-active')
    expect(wrapper.get('button[aria-label="player.repeat"] span').classes()).toContain('bg-mini-player-pill-active')
    expect(wrapper.get('button[aria-label="context_menu.remove_from_favorites"]').attributes('aria-pressed')).toBe('true')

    const range = wrapper.get('input[type="range"]')
    expect(range.attributes('aria-label')).toBe('player.seek')
    await range.trigger('mousedown')
    await range.setValue('50')
    await range.trigger('mouseup')
    expect(player.seek).toHaveBeenCalledWith(60)
    vi.clearAllMocks()
    await range.setValue('25')
    expect(player.seek).toHaveBeenCalledWith(30)
    wrapper.unmount()
  })
})
