import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { h, onUnmounted } from 'vue'
import { createTestingPinia } from '@pinia/testing'
import FullScreenPlayer from './FullScreenPlayer.vue'
import { usePlayerStore } from '../stores/player'
import { PlaybackState, RepeatMode } from '../../bindings/airmedy/internal/domain/models'

const mocks = vi.hoisted(() => ({
  quickSettingsOpen: vi.fn(),
  trackContextOpen: vi.fn(),
  queueScrollToCurrent: vi.fn(),
  lyricsUnmounted: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(), Off: vi.fn() },
  Create: {
    Nullable: (fn: any) => (v: any) => (v == null ? null : fn(v)),
    Array: (fn: any) => (arr: any[]) => (arr ?? []).map(fn),
    Struct: (ctor: any) => (v: any) => (v == null ? null : new ctor(v)),
    Map: (k: any, v: any) => (val: any) => val,
  },
  Call: { ByID: vi.fn().mockResolvedValue(null) },
}))

vi.mock('./player/PlayerQuickSettingsMenu.vue', () => ({
  default: {
    setup(_: unknown, { expose }: { expose: (value: object) => void }) {
      expose({ open: mocks.quickSettingsOpen })
      return () => null
    },
  },
}))

vi.mock('./TrackContextMenu.vue', () => ({
  default: {
    setup(_: unknown, { expose }: { expose: (value: object) => void }) {
      expose({ open: mocks.trackContextOpen })
      return () => null
    },
  },
}))

describe('FullScreenPlayer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function mountPlayer(storeState = {}, appState = {}) {
    return mount(FullScreenPlayer, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              player: {
                theme: null,
                queue: [
                  { id: 't1', title: 'Track 1', duration: 180, artists: [] },
                  { id: 't2', title: 'Track 2', duration: 200, artists: [] },
                ],
                currentTrack: { id: 't1', title: 'Track 1', duration: 180, artists: [] },
                status: {
                  track_id: 't1',
                  playback_state: PlaybackState.PlaybackStatePlaying,
                  position: 0,
                  duration: 180,
                  volume: 1,
                  muted: false,
                  repeat_mode: RepeatMode.RepeatModeOff,
                  shuffle: false,
                },
                isQueueOpen: true,
                isLyricsOpen: false,
                playerMode: 'fullscreen',
                lyrics: null,
                lyricsLoading: false,
                ...storeState,
              },
              device: {
                isMac: false,
                isWindowFullscreen: false,
              },
              app: {
                showPlayerIndicator: false,
                highContrastLyrics: true,
                livingArtworkBackground: true,
                ...appState,
              },
            },
          }),
        ],
        stubs: {
          LivingArtworkBackground: true,
          PlayerArtwork: {
            props: ['maxSize'],
            template: '<div data-test="artwork" :data-max-size="maxSize" />',
          },
          PlayerTrackInfo: true,
          PlayerSeekBar: true,
          PlayerPlaybackControls: true,
          PlayerVolumeControl: true,
          ...Object.fromEntries(['PlayerLyricsPanel', 'ImmersiveLyricsPanel'].map(name => [name, {
            setup() {
              onUnmounted(mocks.lyricsUnmounted)
              return () => h(name === 'PlayerLyricsPanel' ? 'player-lyrics-panel-stub' : 'immersive-lyrics-panel-stub')
            },
          }])),
          TabSwitcher: true,
          Transition: false,
          PlayerQueuePanel: {
            props: ['queue'],
            emits: ['close', 'play-track'],
            setup(_: unknown, { expose }: { expose: (value: object) => void }) {
              expose({ scrollToCurrentTrack: mocks.queueScrollToCurrent })
              return {}
            },
            template: '<button data-test="queue-play" @click="$emit(\'play-track\', 1)">play</button>',
          },
        },
      },
    })
  }

  it('routes fullscreen queue clicks through playQueueIndex', async () => {
    const wrapper = mountPlayer()
    const store = usePlayerStore()

    await wrapper.get('[data-test="queue-play"]').trigger('click')

    expect(store.playQueueIndex).toHaveBeenCalledOnce()
    expect(store.playQueueIndex).toHaveBeenCalledWith(1)
    expect(store.playTracks).not.toHaveBeenCalled()
  })

  it('scrolls to the current track when the queue opens', async () => {
    const wrapper = mountPlayer({ isQueueOpen: false })
    const store = usePlayerStore()

    store.isQueueOpen = true
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    expect(mocks.queueScrollToCurrent).toHaveBeenCalledOnce()
  })

  it('opens quick settings when right clicking an empty fullscreen area', async () => {
    const wrapper = mountPlayer()

    await wrapper.get('[data-test="fullscreen-player"]').trigger('contextmenu')

    expect(mocks.quickSettingsOpen).toHaveBeenCalledOnce()
  })

  it('returns to the sticky player when Escape is pressed', async () => {
    const wrapper = mountPlayer()
    const store = usePlayerStore()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }))

    expect(store.setPlayerMode).toHaveBeenCalledOnce()
    expect(store.setPlayerMode).toHaveBeenCalledWith('sticky')
    wrapper.unmount()
  })

  it('keeps the track context menu on artwork right click', async () => {
    const wrapper = mountPlayer()

    await wrapper.get('[data-test="artwork"]').trigger('contextmenu')

    expect(mocks.trackContextOpen).toHaveBeenCalledOnce()
    expect(mocks.quickSettingsOpen).not.toHaveBeenCalled()
  })

  it('moves fixed columns with compositor-only properties', async () => {
    const wrapper = mountPlayer({ isQueueOpen: false })
    const store = usePlayerStore()
    const left = wrapper.get('[data-test="fullscreen-player-left-column"]')
    const right = wrapper.get('[data-test="fullscreen-player-right-column"]')

    expect(left.classes()).toEqual(expect.arrayContaining([
      'w-1/2', 'translate-x-1/2', 'fullscreen-player-motion', 'transform-gpu',
    ]))
    expect(right.classes()).toEqual(expect.arrayContaining([
      'w-1/2', '[contain:layout]', 'pointer-events-none',
    ]))
    expect(wrapper.get('[data-test="fullscreen-player-content"]').classes()).toContain('overflow-visible')
    expect(wrapper.get('[data-test="artwork"]').attributes('data-max-size')).toBe('24')
    expect(wrapper.get('[data-test="fullscreen-queue-panel-motion"]').classes()).toContain('translate-x-1/2')
    expect(wrapper.find('[data-test="queue-play"]').exists()).toBe(false)

    store.isQueueOpen = true
    await wrapper.vm.$nextTick()

    expect(left.classes()).toContain('translate-x-0')
    expect(right.classes()).toContain('pointer-events-auto')
    expect(wrapper.get('[data-test="fullscreen-queue-panel-motion"]').classes()).toContain('translate-x-0')
  })

  it('moves the queue and artwork in the same render', async () => {
    const wrapper = mountPlayer()
    const store = usePlayerStore()
    const queue = () => wrapper.get('[data-test="fullscreen-queue-panel-motion"]')
    const left = wrapper.get('[data-test="fullscreen-player-left-column"]')

    store.isQueueOpen = false
    await wrapper.vm.$nextTick()
    expect(queue().classes()).toContain('translate-x-1/2')
    expect(left.classes()).toContain('translate-x-1/2')

    store.isQueueOpen = true
    await wrapper.vm.$nextTick()
    expect(queue().classes()).toContain('translate-x-0')
    expect(left.classes()).toContain('translate-x-0')
  })

  it.each([true, false])('keeps lyrics alive until leave finishes (high contrast: %s)', async (highContrastLyrics) => {
    const wrapper = mountPlayer({ isQueueOpen: false, isLyricsOpen: true }, { highContrastLyrics })
    const store = usePlayerStore()
    const selector = highContrastLyrics ? 'player-lyrics-panel-stub' : 'immersive-lyrics-panel-stub'
    const lyrics = () => wrapper.get('[data-test="fullscreen-lyrics-panel-motion"]')

    store.isLyricsOpen = false
    await wrapper.vm.$nextTick()
    expect(lyrics().classes()).toContain('translate-x-1/2')
    expect(mocks.lyricsUnmounted).not.toHaveBeenCalled()
    await lyrics().trigger('transitionend', { propertyName: 'opacity' })
    expect(mocks.lyricsUnmounted).toHaveBeenCalledOnce()
    expect(wrapper.find(selector).exists()).toBe(false)

    store.isLyricsOpen = true
    await wrapper.vm.$nextTick()
    store.isLyricsOpen = false
    await wrapper.vm.$nextTick()
    store.isLyricsOpen = true
    await wrapper.vm.$nextTick()
    await lyrics().trigger('transitionend', { propertyName: 'opacity' })
    expect(wrapper.find(selector).exists()).toBe(true)
    expect(mocks.lyricsUnmounted).toHaveBeenCalledOnce()
  })

  it('uses the high-contrast lyrics panel when enabled', () => {
    const wrapper = mountPlayer({ isQueueOpen: false, isLyricsOpen: true })

    expect(wrapper.find('player-lyrics-panel-stub').exists()).toBe(true)
    expect(wrapper.find('immersive-lyrics-panel-stub').exists()).toBe(false)
  })

  it('uses the immersive lyrics panel when high contrast is disabled', () => {
    const wrapper = mountPlayer({ isQueueOpen: false, isLyricsOpen: true }, { highContrastLyrics: false })

    expect(wrapper.find('player-lyrics-panel-stub').exists()).toBe(false)
    expect(wrapper.find('immersive-lyrics-panel-stub').exists()).toBe(true)
  })

  it('uses the living artwork background by default', () => {
    const wrapper = mountPlayer()

    expect(wrapper.find('living-artwork-background-stub').exists()).toBe(true)
    expect(wrapper.find('[data-test="solid-artwork-background"]').exists()).toBe(false)
  })

  it('uses the artwork tint and crossfade duration for the solid background', () => {
    const wrapper = mountPlayer({
      theme: { vibrant: '#ff0000', backdrop: '#556677' },
      artworkCrossfade: { transitionId: 1, durationMs: 600 },
    }, { livingArtworkBackground: false, blendArtworkDuringCrossfade: true })

    const background = wrapper.get('[data-test="solid-artwork-background"]')
    expect(background.attributes('style')).toContain('--fullscreen-player-artwork-tint: rgba(85, 102, 119, 0.4)')
    expect(background.attributes('style')).toContain('--fullscreen-player-artwork-tint-duration: 600ms')
    expect(wrapper.find('living-artwork-background-stub').exists()).toBe(false)
  })
})
