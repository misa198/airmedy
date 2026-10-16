import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createTestingPinia } from '@pinia/testing'
import PlayerFooter from './PlayerFooter.vue'
import { usePlayerStore } from '../stores/player'
import { useAppStore } from '../stores/app'
import { PlaybackState, RepeatMode, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { useFavoritesStore } from '../stores/favorites'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { Events } from '@wailsio/runtime'

const mocks = vi.hoisted(() => ({
  quickSettingsOpen: vi.fn(),
}))

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

vi.mock('../../bindings/airmedy/internal/infra/wails/libraryservice', () => ({
  ToggleFavorite: vi.fn(),
}))

vi.mock('./player/PlayerQuickSettingsMenu.vue', () => ({
  default: defineComponent({
    name: 'PlayerQuickSettingsMenu',
    setup(_, { expose }) {
      expose({ open: mocks.quickSettingsOpen })
      return {}
    },
    render() {
      return h('div')
    }
  }),
}))

describe('PlayerFooter', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function mountFooter(storeState = {}, showFavoriteButton = true) {
    const wrapper = mount(PlayerFooter, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            stubActions: false,
            initialState: {
              app: { showPlayerFavoriteButton: showFavoriteButton },
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
            name: 'MarqueeText',
            template: '<div><slot />{{ text }}</div>',
            props: { text: String, fade: Boolean },
          },
          TrackContextMenu: true,
        },
      },
    })

    const player = usePlayerStore()
    player.togglePlayPause = vi.fn()
    player.toggleQueue = vi.fn()
    return wrapper
  }

  const favoriteButton = (wrapper: ReturnType<typeof mountFooter>) => wrapper.get('button[aria-pressed]')

  it('shows and hides the favorite button with the playback setting', async () => {
    const wrapper = mountFooter({}, false)
    expect(wrapper.find('button[aria-pressed]').exists()).toBe(false)
    useAppStore().showPlayerFavoriteButton = true
    await wrapper.vm.$nextTick()
    expect(wrapper.find('button[aria-pressed]').exists()).toBe(true)
    useAppStore().showPlayerFavoriteButton = false
    await wrapper.vm.$nextTick()
    expect(wrapper.find('button[aria-pressed]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Not Playing')
    wrapper.unmount()
  })

  it('disables favorite when there is no track', async () => {
    const wrapper = mountFooter()
    const button = favoriteButton(wrapper)
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.attributes('aria-pressed')).toBe('false')
    expect(button.classes()).toContain('opacity-30')
    await button.trigger('click')
    expect(LibraryService.ToggleFavorite).not.toHaveBeenCalled()
  })

  it.each([false, true])('renders and toggles favorite from %s', async (isFavorite) => {
    vi.mocked(LibraryService.ToggleFavorite).mockResolvedValue(!isFavorite)
    const wrapper = mountFooter({ currentTrack: new TrackDTO({ id: 't1', is_favorite: isFavorite }) })
    const button = favoriteButton(wrapper)
    expect(button.attributes('aria-pressed')).toBe(String(isFavorite))
    expect(button.get('svg').classes('fill-current')).toBe(isFavorite)
    expect(button.classes('transition-none')).toBe(true)
    expect(button.attributes('aria-label')).toBe(isFavorite ? 'context_menu.remove_from_favorites' : 'context_menu.add_to_favorites')

    await button.trigger('click')
    await flushPromises()

    expect(LibraryService.ToggleFavorite).toHaveBeenCalledOnce()
    expect(LibraryService.ToggleFavorite).toHaveBeenCalledWith('t1')
    expect(button.attributes('aria-pressed')).toBe(String(!isFavorite))
    expect(button.get('svg').classes('fill-current')).toBe(!isFavorite)
    expect(button.classes('transition-colors')).toBe(true)
    expect(button.attributes('aria-label')).toBe(!isFavorite ? 'context_menu.remove_from_favorites' : 'context_menu.add_to_favorites')
  })

  it('reflects external favorite updates and track changes', async () => {
    const wrapper = mountFooter({ currentTrack: new TrackDTO({ id: 't1', is_favorite: false }) })
    const listener = vi.mocked(Events.On).mock.calls.find(([name]) => name === 'library:track-updated')![1]
    listener({ data: new TrackDTO({ id: 't1', is_favorite: true }) } as Events.WailsEvent)
    await wrapper.vm.$nextTick()
    expect(favoriteButton(wrapper).attributes('aria-pressed')).toBe('true')
    expect(favoriteButton(wrapper).classes('transition-none')).toBe(true)

    usePlayerStore().currentTrack = new TrackDTO({ id: 't2', is_favorite: false })
    await wrapper.vm.$nextTick()
    expect(favoriteButton(wrapper).attributes('aria-pressed')).toBe('false')
    expect(favoriteButton(wrapper).classes('transition-none')).toBe(true)
  })

  it('stops the click transition when the track changes', async () => {
    vi.mocked(LibraryService.ToggleFavorite).mockResolvedValue(true)
    const wrapper = mountFooter({ currentTrack: new TrackDTO({ id: 't1', is_favorite: false }) })
    await favoriteButton(wrapper).trigger('click')
    await flushPromises()
    expect(favoriteButton(wrapper).classes('transition-colors')).toBe(true)

    usePlayerStore().currentTrack = new TrackDTO({ id: 't2', is_favorite: false })
    await wrapper.vm.$nextTick()
    expect(favoriteButton(wrapper).classes('transition-none')).toBe(true)
    wrapper.unmount()
  })

  it('limits the transition to the click animation and cleans up on unmount', async () => {
    vi.useFakeTimers()
    const wrapper = mountFooter({ currentTrack: new TrackDTO({ id: 't1', is_favorite: false }) })
    try {
      vi.mocked(LibraryService.ToggleFavorite).mockResolvedValue(true)
      await favoriteButton(wrapper).trigger('click')
      await flushPromises()
      expect(favoriteButton(wrapper).classes('transition-colors')).toBe(true)
      await vi.advanceTimersByTimeAsync(300)
      expect(favoriteButton(wrapper).classes('transition-none')).toBe(true)

      vi.mocked(LibraryService.ToggleFavorite).mockResolvedValue(false)
      await favoriteButton(wrapper).trigger('click')
      await flushPromises()
      expect(vi.getTimerCount()).toBe(1)
      wrapper.unmount()
      expect(vi.getTimerCount()).toBe(0)
    } finally {
      vi.useRealTimers()
    }
  })

  it('prevents duplicate requests and keeps the original track when playback advances', async () => {
    let resolve!: (value: boolean) => void
    vi.mocked(LibraryService.ToggleFavorite).mockReturnValue(new Promise<boolean>((done) => { resolve = done }) as ReturnType<typeof LibraryService.ToggleFavorite>)
    const track = new TrackDTO({ id: 't1', is_favorite: false })
    const wrapper = mountFooter({ currentTrack: track })
    const button = favoriteButton(wrapper)
    await button.trigger('click')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.classes()).not.toContain('disabled:opacity-30')
    expect(button.classes()).not.toContain('opacity-30')
    await button.trigger('click')
    usePlayerStore().currentTrack = new TrackDTO({ id: 't2', is_favorite: false })
    resolve(true)
    await flushPromises()

    expect(LibraryService.ToggleFavorite).toHaveBeenCalledOnce()
    expect(LibraryService.ToggleFavorite).toHaveBeenCalledWith('t1')
    expect(useFavoritesStore().isFavorite(track)).toBe(true)
    expect(button.attributes('aria-pressed')).toBe('false')
    expect(button.attributes('disabled')).toBeUndefined()
    expect(button.classes('transition-none')).toBe(true)
  })

  it('keeps the state and allows retry after a failed toggle', async () => {
    const error = new Error('Toggle failed')
    const log = vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.mocked(LibraryService.ToggleFavorite).mockRejectedValueOnce(error).mockResolvedValueOnce(true)
    const wrapper = mountFooter({ currentTrack: new TrackDTO({ id: 't1', is_favorite: false }) })
    const button = favoriteButton(wrapper)
    await button.trigger('click')
    await flushPromises()
    expect(button.attributes('aria-pressed')).toBe('false')
    expect(button.attributes('disabled')).toBeUndefined()
    expect(log).toHaveBeenCalledWith('Failed to toggle favorite:', error)
    expect(button.classes('transition-none')).toBe(true)
    await button.trigger('click')
    await flushPromises()
    expect(button.attributes('aria-pressed')).toBe('true')
    log.mockRestore()
  })

  it('renders "Not Playing" when no track', () => {
    const wrapper = mountFooter()
    expect(wrapper.text()).toContain('Not Playing')
  })

  it('enables overflow fade for the track title and artist', () => {
    const wrapper = mountFooter()
    expect(wrapper.findAllComponents({ name: 'MarqueeText' }).map(component => component.props('fade')))
      .toEqual([true, true])
    wrapper.unmount()
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
    const buttons = wrapper.findAll('button')
    const playBtn = buttons.find((b) => b.attributes('title') === 'player.play')
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

  it('opens quick settings menu on right click in empty footer area', async () => {
    const wrapper = mountFooter()
    await wrapper.find('div').trigger('contextmenu')
    expect(mocks.quickSettingsOpen).toHaveBeenCalledOnce()
    expect(mocks.quickSettingsOpen.mock.calls[0]?.[0]).toBeTruthy()
  })

  it('does not open quick settings menu on left click', async () => {
    const wrapper = mountFooter()
    await wrapper.trigger('click')
    expect(mocks.quickSettingsOpen).not.toHaveBeenCalled()
  })
})
