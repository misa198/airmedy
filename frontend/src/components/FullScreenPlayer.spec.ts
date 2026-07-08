import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import FullScreenPlayer from './FullScreenPlayer.vue'
import { usePlayerStore } from '../stores/player'
import { PlaybackState, RepeatMode } from '../../bindings/airmedy/internal/domain/models'

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

describe('FullScreenPlayer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function mountPlayer(storeState = {}) {
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
              },
            },
          }),
        ],
        stubs: {
          LivingArtworkBackground: true,
          PlayerArtwork: true,
          PlayerTrackInfo: true,
          PlayerSeekBar: true,
          PlayerPlaybackControls: true,
          PlayerVolumeControl: true,
          PlayerLyricsPanel: true,
          TrackContextMenu: true,
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
})
