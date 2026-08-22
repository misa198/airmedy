import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import MiniPlayerFloating from './MiniPlayerFloating.vue'

const mocks = vi.hoisted(() => ({
  setMiniPlayerExpanded: vi.fn().mockResolvedValue(undefined),
  moodRadioStore: { active: false },
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
    theme: null,
    lyrics: { content: '[00:00.00]First line' },
    lyricsLoading: true,
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
  useAppStore: () => ({ blendArtworkDuringCrossfade: false, showPlayerIndicator: false }),
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

describe('MiniPlayerFloating', () => {
  it('shows the radio indicator while Mood Radio is active', async () => {
    mocks.moodRadioStore.active = true
    const wrapper = mount(MiniPlayerFloating, {
      global: { stubs: { LazyImg: true, Slider: true, MarqueeText: true, PlayerControlButton: true, MiniPlayerLyrics: true, QueueTrackList: true } },
    })

    await wrapper.get('[data-test="mini-player-queue"]').trigger('click')
    expect(wrapper.find('[data-test="mini-player-mood-radio"]').exists()).toBe(true)
    mocks.moodRadioStore.active = false
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

    await wrapper.get('[data-test="mini-player-lyrics"]').trigger('click')
    expect(wrapper.get('[data-test="mini-player-panel"]').text()).toContain('[00:00.00]First line')
    expect(mocks.setMiniPlayerExpanded).toHaveBeenLastCalledWith(true)
    expect(wrapper.get('[data-test="mini-player-panel"]').text()).toContain('[00:00.00]First line')
    expect(wrapper.get('[data-test="mini-player-panel-indicator"]').classes()).toContain('translate-x-0')
    expect(wrapper.get('[data-test="mini-player-lyrics-content"]').text()).toContain('[00:00.00]First line')

    await wrapper.get('[data-test="mini-player-lyrics"]').trigger('click')
    await flushPromises()
    expect(mocks.setMiniPlayerExpanded).toHaveBeenLastCalledWith(false)
    expect(wrapper.find('[data-test="mini-player-panel"]').exists()).toBe(false)
  })
})
