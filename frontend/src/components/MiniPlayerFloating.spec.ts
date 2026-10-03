import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MiniPlayerFloating from './MiniPlayerFloating.vue'
import RomanizationToggle from './RomanizationToggle.vue'

const mocks = vi.hoisted(() => ({
  setMiniPlayerExpanded: vi.fn().mockResolvedValue(undefined),
  moodRadioStore: { active: false },
  inspectRomanization: vi.fn(),
  lyricsLoading: true,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key === 'player.lyrics' ? 'Lyrics' : 'Queue' }),
}))

vi.mock('@/stores/player', () => ({
  usePlayerStore: () => ({
    artworkCrossfade: null,
    currentTrack: null,
    artworkUrl: '',
    duration: 0,
    position: 0,
    progressPercent: 0,
    muted: false,
    volume: 1,
    shuffle: false,
    repeatMode: 0,
    isPlaying: false,
    theme: { vibrant: '#ff0000', muted: '#556677', dominant: '#0000ff', backdrop: '#556677' },
    lyrics: { content: '[00:00.00]First line' },
    lyricsLoading: mocks.lyricsLoading,
    seek: vi.fn(),
    setVolume: vi.fn(),
    setShuffle: vi.fn(),
    cycleRepeat: vi.fn(),
    previous: vi.fn(),
    next: vi.fn(),
    togglePlayPause: vi.fn(),
  }),
}))

vi.mock('@/stores/app', () => ({
  useAppStore: () => ({ blendArtworkDuringCrossfade: false, showPlayerIndicator: false, romanizationEnabled: true }),
}))

vi.mock('@/stores/moodRadio', () => ({
  useMoodRadioStore: () => mocks.moodRadioStore,
}))

vi.mock('@/stores/device', () => ({
  useDeviceStore: () => ({ isWindows: false }),
}))

vi.mock('@/composables/useArtworkCrossfadeOpacity', () => ({
  useArtworkCrossfadeOpacity: () => ({ outgoingOpacity: 1, incomingOpacity: 1 }),
}))

vi.mock('../../bindings/airmedy/internal/infra/wails/windowservice', () => ({
  CloseMiniPlayer: vi.fn(),
  GetMiniState: vi.fn().mockResolvedValue({ always_on_top: false }),
  SetMiniAlwaysOnTop: vi.fn(),
  SetMiniPlayerExpanded: mocks.setMiniPlayerExpanded,
}))

vi.mock('../../bindings/airmedy/internal/infra/wails', () => ({
  LyricsService: {
    InspectRomanization: mocks.inspectRomanization,
    RomanizeLyrics: vi.fn(),
  },
}))

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => vi.fn()) },
  Create: {
    Nullable: (fn: (value: unknown) => unknown) => (value: unknown) => value == null ? null : fn(value),
    Array: (fn: (value: unknown) => unknown) => (value: unknown[]) => (value ?? []).map(fn),
    Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value),
    Map: () => (value: unknown) => value,
  },
}))

describe('MiniPlayerFloating', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mocks.lyricsLoading = true
    mocks.inspectRomanization.mockImplementation(() => Object.assign(Promise.resolve({ supported: false, mandarinDefault: false }), { cancel: vi.fn().mockResolvedValue(undefined) }))
  })

  it('keeps the artwork as the window drag region', () => {
    const wrapper = mount(MiniPlayerFloating, {
      global: { stubs: { LazyImg: true, Slider: true, MarqueeText: true, PlayerControlButton: true, MiniPlayerLyrics: true, QueueTrackList: true } },
    })

    expect(wrapper.get('[data-test="mini-player-artwork"]').attributes('style'))
      .toContain('--wails-draggable: drag')
  })

  it('hides the controls when the mini-player window loses focus', async () => {
    const wrapper = mount(MiniPlayerFloating, {
      global: { stubs: { LazyImg: true, Slider: true, MarqueeText: true, PlayerControlButton: true, MiniPlayerLyrics: true, QueueTrackList: true } },
    })

    await wrapper.find('.aspect-square').trigger('mouseenter')
    expect(wrapper.get('[data-test="mini-player-actions-pill"]').classes()).toContain('opacity-100')
    window.dispatchEvent(new Event('blur'))
    await flushPromises()
    expect(wrapper.get('[data-test="mini-player-actions-pill"]').classes()).toContain('opacity-0')
    window.dispatchEvent(new Event('focus'))
    await flushPromises()
    expect(wrapper.get('[data-test="mini-player-actions-pill"]').classes()).toContain('opacity-100')
  })

  it('shows the radio indicator while Mood Radio is active', async () => {
    mocks.moodRadioStore.active = true
    const wrapper = mount(MiniPlayerFloating, {
      global: { stubs: { LazyImg: true, Slider: true, MarqueeText: true, PlayerControlButton: true, MiniPlayerLyrics: true, QueueTrackList: true } },
    })

    await wrapper.get('[data-test="mini-player-queue"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="mini-player-mood-radio"]').exists()).toBe(true)
    mocks.moodRadioStore.active = false
  })

  it('fades the romanization action independently of the mini-player panel', async () => {
    mocks.lyricsLoading = false
    mocks.inspectRomanization.mockImplementation(() => Object.assign(Promise.resolve({ supported: true, mandarinDefault: false }), { cancel: vi.fn().mockResolvedValue(undefined) }))
    const wrapper = mount(MiniPlayerFloating, {
      global: {
        mocks: { $t: (key: string) => key },
        stubs: { LazyImg: true, Slider: true, MarqueeText: true, PlayerControlButton: true, MiniPlayerLyrics: true, QueueTrackList: true, Transition: false },
      },
    })

    await wrapper.get('[data-test="mini-player-lyrics"]').trigger('click')
    await flushPromises()
    const action = wrapper.get('[data-test="mini-player-romanization-action"]')
    expect(action.classes()).toEqual(expect.arrayContaining(['absolute', 'right-full']))
    expect(action.element.parentElement?.classList.contains('flex')).toBe(true)
    expect(wrapper.get('[data-test="mini-player-romanization-toggle"]').classes()).toContain('size-8')
    expect(wrapper.get('[data-test="mini-player-actions-pill"]').classes()).toContain('h-8')
    expect(wrapper.get('[data-test="mini-player-romanization-toggle"]').classes()).toContain('opacity-0')
    expect(wrapper.getComponent(RomanizationToggle).props()).not.toHaveProperty('loading')
    expect(wrapper.get('[data-test="mini-player-romanization-toggle"]').attributes('aria-busy')).toBeUndefined()
    expect(wrapper.find('[data-test="mini-player-romanization-toggle"] .lucide-languages').exists()).toBe(true)

    await wrapper.find('.aspect-square').trigger('mouseenter')
    const toggle = wrapper.get('[data-test="mini-player-romanization-toggle"]')
    const pillActions = wrapper.get('[data-test="mini-player-lyrics"]').element.parentElement!
    expect(toggle.classes()).toContain('opacity-100')
    expect(toggle.classes()).toContain('duration-100!')
    expect(pillActions.classList.contains('duration-100')).toBe(true)

    await wrapper.get('[data-test="mini-player-volume"]').trigger('click')
    expect(toggle.classes()).toContain('opacity-0')
    expect(pillActions.classList.contains('opacity-0')).toBe(true)

    vi.useFakeTimers()
    await wrapper.get('[data-test="mini-player-volume"]').trigger('click')
    vi.advanceTimersByTime(200)
    await wrapper.vm.$nextTick()
    expect(toggle.classes()).toContain('opacity-100')
    expect(pillActions.classList.contains('opacity-100')).toBe(true)
    vi.useRealTimers()

    await wrapper.get('[data-test="mini-player-queue"]').trigger('click')
    expect(wrapper.get('[data-test="mini-player-romanization-action"]').classes()).toContain('mini-romanization-leave-active')
    wrapper.unmount()
  })

  it('keeps its pills dark while lyrics inherit the mini-player window theme', () => {
    const wrapper = mount(MiniPlayerFloating, {
      global: {
        stubs: {
          LazyImg: true,
          Slider: true,
          MarqueeText: true,
          PlayerControlButton: true,
          MiniPlayerLyrics: true,
          QueueTrackList: { props: ['scrollToCurrentOnMount'], template: '<div data-test="mini-player-queue-content">Queue</div>' },
        },
      },
    })

    expect(wrapper.classes()).not.toContain('dark')
    expect(wrapper.findAll('div').filter((element) => element.classes().includes('bg-mini-player-pill-background')))
      .toHaveLength(2)
    expect(wrapper.get('[data-test="mini-player-lyrics"]').classes()).toContain('text-mini-player-pill-foreground')
  })

  it('opens the volume pill on click and closes it after inactivity', async () => {
    vi.useFakeTimers()
    const wrapper = mount(MiniPlayerFloating, {
      global: { stubs: { LazyImg: true, Slider: true, MarqueeText: true, PlayerControlButton: true, MiniPlayerLyrics: true, QueueTrackList: true } },
    })

    await wrapper.get('[data-test="mini-player-volume"]').trigger('click')
    expect(wrapper.get('[data-test="mini-player-actions-pill"]').classes()).toContain('w-[122px]')
    expect(wrapper.get('[data-test="mini-player-volume-pill"]').classes()).toContain('opacity-100')

    vi.advanceTimersByTime(3000)
    await flushPromises()
    expect(wrapper.find('[data-test="mini-player-actions-pill"]').exists()).toBe(true)
    vi.useRealTimers()
  })

  it('switches the placeholder panel and collapses when the active option is clicked again', async () => {
    const wrapper = mount(MiniPlayerFloating, {
      global: {
        stubs: {
          LazyImg: true,
          Slider: true,
          MarqueeText: true,
          PlayerControlButton: true,
          MiniPlayerLyrics: {
            props: ['lyrics', 'loading', 'currentPosition'],
            template: '<div data-test="mini-player-lyrics-content">{{ lyrics }} {{ loading }} {{ currentPosition }}</div>',
          },
          QueueTrackList: { props: ['scrollToCurrentOnMount'], template: '<div data-test="mini-player-queue-content">Queue</div>' },
        },
      },
    })

    let finishResize!: () => void
    mocks.setMiniPlayerExpanded.mockImplementationOnce(() => new Promise<void>((resolve) => {
      finishResize = resolve
    }))
    await wrapper.get('[data-test="mini-player-queue"]').trigger('click')
    expect(wrapper.find('[data-test="mini-player-panel"]').exists()).toBe(false)
    finishResize()
    await flushPromises()
    expect(wrapper.find('[data-test="mini-player-queue-content"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="mini-player-scroll-to-current"]').exists()).toBe(true)
    expect(mocks.setMiniPlayerExpanded).toHaveBeenLastCalledWith(true)
    expect(wrapper.find('[data-test="mini-player-queue-content"]').exists()).toBe(true)
    expect(wrapper.get('[data-test="mini-player-panel-indicator"]').classes()).toContain('translate-x-[26px]')
    expect(wrapper.get('[data-test="mini-player-panel-indicator"]').classes()).toContain('transition-opacity')

    await wrapper.get('[data-test="mini-player-lyrics"]').trigger('click')
    expect(wrapper.get('[data-test="mini-player-panel"]').text()).toContain('[00:00.00]First line')
    expect(wrapper.get('[data-test="mini-player-panel"]').classes()).toContain('mini-player-lyrics-panel')
    expect(wrapper.get('[data-test="mini-player-panel"]').classes()).not.toContain('mini-player-lyrics-tint-ready')
    expect(wrapper.get('[data-test="mini-player-panel"]').classes()).toContain('backdrop-blur-[30px]')
    expect(wrapper.get('[data-test="mini-player-panel"]').attributes('style')).toContain('background-color: var(--mini-player-lyrics-background)')
    expect(wrapper.get('[data-test="mini-player-panel"]').attributes('style')).toContain('--mini-player-lyrics-tint: rgba(85, 102, 119, 0.4)')
    expect(wrapper.get('[data-test="mini-player-panel"]').attributes('style')).toContain('--mini-player-lyrics-tint-duration: 1500ms')
    expect(mocks.setMiniPlayerExpanded).toHaveBeenLastCalledWith(true)
    expect(wrapper.get('[data-test="mini-player-panel"]').text()).toContain('[00:00.00]First line')
    expect(wrapper.get('[data-test="mini-player-panel-indicator"]').classes()).toContain('translate-x-0')
    expect(wrapper.get('[data-test="mini-player-panel-indicator"]').classes()).toContain('transition-all')
    expect(wrapper.get('[data-test="mini-player-lyrics-content"]').text()).toContain('[00:00.00]First line')

    await wrapper.get('[data-test="mini-player-lyrics"]').trigger('click')
    await flushPromises()
    expect(mocks.setMiniPlayerExpanded).toHaveBeenLastCalledWith(false)
    expect(wrapper.find('[data-test="mini-player-panel"]').exists()).toBe(false)
  })
})
