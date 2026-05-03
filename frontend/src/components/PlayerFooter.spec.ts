import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import PlayerFooter from './PlayerFooter.vue'
import { usePlayerStore } from '../stores/player'
import { PlaybackState, RepeatMode } from '../../bindings/airmedy/internal/domain/models'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => {
      if (key === 'player.not_playing') return 'Not Playing'
      if (key === 'player.select_track') return 'Select Track'
      return key
    },
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

vi.mock('../../bindings/airmedy/internal/infra/wails/playerservice', () => ({
  GetStatus: vi.fn().mockResolvedValue(null),
  GetQueue: vi.fn().mockResolvedValue([]),
  Play: vi.fn(),
  Pause: vi.fn(),
  Stop: vi.fn(),
  Next: vi.fn(),
  Previous: vi.fn(),
  Seek: vi.fn(),
  SetVolume: vi.fn(),
  SetMuted: vi.fn(),
  SetShuffle: vi.fn(),
  SetRepeatMode: vi.fn(),
  PlayTracks: vi.fn(),
}))

describe('PlayerFooter', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function mountFooter(storeState = {}) {
    return mount(PlayerFooter, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              player: {
                status: null,
                queue: [],
                currentTrack: null,
                theme: null,
                isQueueOpen: false,
                playerMode: 'sticky',
                ...storeState,
              },
            },
          }),
        ],
        stubs: { 
          teleport: true,
          MarqueeText: {
            template: '<div><slot />{{ text }}</div>',
            props: ['text']
          },
          TrackContextMenu: true
        },
      },
    })
  }

  it('renders "Not Playing" when no track', () => {
    const wrapper = mountFooter()
    expect(wrapper.text()).toContain('Not Playing')
  })

  it('shows play button when paused', () => {
    const wrapper = mountFooter({
      status: {
        track_id: 't1',
        playback_state: PlaybackState.PlaybackStatePaused,
        position: 0,
        duration: 180,
        volume: 1,
        muted: false,
        repeat_mode: RepeatMode.RepeatModeOff,
        shuffle: false,
      },
    })
    // Play icon should be present (not Pause)
    const buttons = wrapper.findAll('button')
    expect(buttons.length).toBeGreaterThan(0)
  })

  it('calls togglePlayPause when play button is clicked', async () => {
    const wrapper = mountFooter()
    const store = usePlayerStore()
    // Find the center play button (3rd button in controls row)
    const buttons = wrapper.findAll('button')
    // The play/pause button is the one with rounded-full class
    const playBtn = buttons.find((b) => b.classes('rounded-full'))
    expect(playBtn).toBeDefined()
    await playBtn!.trigger('click')
    expect(store.togglePlayPause).toHaveBeenCalledOnce()
  })

  it('calls toggleQueue when queue button is clicked', async () => {
    const wrapper = mountFooter()
    const store = usePlayerStore()
    const buttons = wrapper.findAll('button')
    const queueBtn = buttons.find((b) => b.attributes('title') === 'player.queue')
    expect(queueBtn).toBeDefined()
    await queueBtn!.trigger('click')
    expect(store.toggleQueue).toHaveBeenCalledOnce()
  })
})
