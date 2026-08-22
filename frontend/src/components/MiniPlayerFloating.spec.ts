import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import MiniPlayerFloating from './MiniPlayerFloating.vue'

const mocks = vi.hoisted(() => ({
  setMiniPlayerExpanded: vi.fn().mockResolvedValue(undefined),
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
  it('switches the placeholder panel and collapses when the active option is clicked again', async () => {
    const wrapper = mount(MiniPlayerFloating, {
      global: {
        stubs: {
          LazyImg: true,
          Slider: true,
          MarqueeText: true,
          PlayerControlButton: true,
        },
      },
    })

    let finishResize!: () => void
    mocks.setMiniPlayerExpanded.mockImplementationOnce(() => new Promise<void>((resolve) => {
      finishResize = resolve
    }))
    await wrapper.get('[data-test="mini-player-queue"]').trigger('click')
    expect(wrapper.get('[data-test="mini-player-panel"]').text()).toBe('Queue')
    finishResize()
    await flushPromises()
    expect(mocks.setMiniPlayerExpanded).toHaveBeenLastCalledWith(true)
    expect(wrapper.get('[data-test="mini-player-panel"]').text()).toBe('Queue')
    expect(wrapper.get('[data-test="mini-player-panel-indicator"]').classes()).toContain('translate-x-[26px]')

    let finishCollapse!: () => void
    mocks.setMiniPlayerExpanded.mockImplementationOnce(() => new Promise<void>((resolve) => {
      finishCollapse = resolve
    }))
    await wrapper.get('[data-test="mini-player-lyrics"]').trigger('click')
    expect(wrapper.get('[data-test="mini-player-panel"]').text()).toBe('Lyrics')
    finishCollapse()
    await flushPromises()
    expect(mocks.setMiniPlayerExpanded).toHaveBeenLastCalledWith(true)
    expect(wrapper.get('[data-test="mini-player-panel"]').text()).toBe('Lyrics')
    expect(wrapper.get('[data-test="mini-player-panel-indicator"]').classes()).toContain('translate-x-0')

    await wrapper.get('[data-test="mini-player-lyrics"]').trigger('click')
    await flushPromises()
    expect(mocks.setMiniPlayerExpanded).toHaveBeenLastCalledWith(false)
    expect(wrapper.find('[data-test="mini-player-panel"]').exists()).toBe(false)
  })
})
