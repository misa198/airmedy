import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import FullScreenPlayer from './FullScreenPlayer.vue'
import { usePlayerStore } from '../stores/player'
import { PlaybackState, RepeatMode } from '../../bindings/airmedy/internal/domain/models'

const mocks = vi.hoisted(() => ({
  quickSettingsOpen: vi.fn(),
  trackContextOpen: vi.fn(),
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
          PlayerLyricsPanel: true,
          ImmersiveLyricsPanel: true,
          TabSwitcher: true,
          Transition: false,
          PlayerQueuePanel: {
            props: ['queue'],
            emits: ['close', 'play-track'],
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

  it('opens quick settings when right clicking an empty fullscreen area', async () => {
    const wrapper = mountPlayer()

    await wrapper.get('[data-test="fullscreen-player"]').trigger('contextmenu')

    expect(mocks.quickSettingsOpen).toHaveBeenCalledOnce()
  })

  it('keeps the track context menu on artwork right click', async () => {
    const wrapper = mountPlayer()

    await wrapper.get('[data-test="artwork"]').trigger('contextmenu')

    expect(mocks.trackContextOpen).toHaveBeenCalledOnce()
    expect(mocks.quickSettingsOpen).not.toHaveBeenCalled()
  })

  it.each([
    [false, 22],
    [true, 20],
  ])('sets the artwork maximum size to %irem when the right column is %s', (rightColumnOpen, expectedMaxSize) => {
    const wrapper = mountPlayer({
      isQueueOpen: rightColumnOpen,
      isLyricsOpen: false,
    })

    expect(wrapper.get('[data-test="artwork"]').attributes('data-max-size')).toBe(String(expectedMaxSize))
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
})
