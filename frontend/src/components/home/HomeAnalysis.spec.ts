import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { describe, expect, it, vi } from 'vitest'
import HomeAnalysis from './HomeAnalysis.vue'
import * as AnalyticsService from '../../../bindings/airmedy/internal/infra/wails/analyticsservice'
import * as LibraryService from '../../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { TrackDTO } from '../../../bindings/airmedy/internal/domain/models'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    locale: { value: 'en' },
    t: (key: string) => ({
      'analytics.no_listening_data': 'No listening data for this period yet.',
      'analytics.no_audio_quality': 'No audio quality data yet.',
      'analytics.no_library_growth': 'No tracks in your library yet.',
      'analytics.average_session': 'Average Session',
      'analytics.per_playback_attempt': 'Per playback attempt',
      'common.min': 'min',
    })[key] ?? key,
  }),
}))

vi.mock('../../../bindings/airmedy/internal/infra/wails/analyticsservice', () => ({ GetInsights: vi.fn() }))
vi.mock('../../../bindings/airmedy/internal/infra/wails/libraryservice', () => ({ GetTracksByIDs: vi.fn() }))

function cancellable<T>(value: T) {
  return Object.assign(Promise.resolve(value), { cancel: vi.fn() })
}

describe('HomeAnalysis empty state', () => {
  it.each([
    ['insights are unavailable', null],
    ['no listening time has been recorded', { listened_seconds: 0 }],
  ])('shows a placeholder when %s', async (_, insights) => {
    vi.mocked(AnalyticsService.GetInsights).mockReturnValue(cancellable(insights) as unknown as ReturnType<typeof AnalyticsService.GetInsights>)

    const wrapper = mount(HomeAnalysis, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn })],
        stubs: {
          AudioQualityChart: true,
          GenreDistributionChart: true,
          LibraryGrowthChart: true,
          ListeningActivityChart: true,
          TabSwitcher: true,
          TopArtistsCarousel: true,
          TrackTable: true,
        },
      },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="analytics-listening-empty"]').text()).toContain('No listening data for this period yet.')
    expect(wrapper.get('[data-testid="analytics-library-growth-empty"]').text()).toContain('No tracks in your library yet.')
    expect(wrapper.get('[data-testid="analytics-quality-empty"]').text()).toContain('No audio quality data yet.')
    expect(wrapper.find('[data-testid="analytics-library-summary"]').exists()).toBe(true)
    expect(wrapper.text().indexOf('analytics.plays')).toBeLessThan(wrapper.text().indexOf('analytics.total_time'))
  })
})

describe('HomeAnalysis top tracks', () => {
  it('keeps the analytics ranking and displays period-specific listening metrics', async () => {
    vi.mocked(AnalyticsService.GetInsights).mockReturnValue(cancellable({
      listened_seconds: 660,
      plays: 8,
      library_tracks: 2,
      library_albums: 0,
      library_artists: 0,
      library_playlists: 0,
      library_bytes: 0,
      activity: [],
      quality: [],
      genres: [],
      top_artists: [],
      top_tracks: [
        { id: 'track-a', title: 'A', artist: 'Artist', play_count: 5, listened_seconds: 60 },
        { id: 'track-b', title: 'B', artist: 'Artist', play_count: 3, listened_seconds: 600 },
      ],
    }) as unknown as ReturnType<typeof AnalyticsService.GetInsights>)
    vi.mocked(LibraryService.GetTracksByIDs).mockReturnValue(cancellable([
      { id: 'track-b', title: 'B', play_count: 200 },
      { id: 'track-a', title: 'A', play_count: 100 },
    ] as TrackDTO[]) as unknown as ReturnType<typeof LibraryService.GetTracksByIDs>)

    const wrapper = mount(HomeAnalysis, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn })],
        stubs: {
          AudioQualityChart: true,
          GenreDistributionChart: true,
          LibraryGrowthChart: true,
          ListeningActivityChart: true,
          TabSwitcher: true,
          TopArtistsCarousel: true,
          TrackTable: { name: 'TrackTable', props: ['tracks'], template: '<div />' },
        },
      },
    })
    await flushPromises()

    const tracks = wrapper.findComponent({ name: 'TrackTable' }).props('tracks') as TrackDTO[]
    expect(tracks).toMatchObject([
      { id: 'track-a', play_count: 5, listened_seconds: 60 },
      { id: 'track-b', play_count: 3, listened_seconds: 600 },
    ])
  })

  it('previews five tracks and expands to the full ranking', async () => {
    const analyticsTracks = Array.from({ length: 50 }, (_, index) => ({
      id: `track-${index}`, title: `Track ${index}`, artist: 'Artist', play_count: 50 - index, listened_seconds: 60,
    }))
    vi.mocked(AnalyticsService.GetInsights).mockReturnValue(cancellable({ listened_seconds: 3000, top_tracks: analyticsTracks }) as unknown as ReturnType<typeof AnalyticsService.GetInsights>)
    vi.mocked(LibraryService.GetTracksByIDs).mockReturnValue(cancellable(analyticsTracks as unknown as TrackDTO[]) as unknown as ReturnType<typeof LibraryService.GetTracksByIDs>)

    const wrapper = mount(HomeAnalysis, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn })],
        stubs: {
          AudioQualityChart: true, GenreDistributionChart: true, LibraryGrowthChart: true,
          ListeningActivityChart: true, TabSwitcher: true, TopArtistsCarousel: true,
          TrackTable: { name: 'TrackTable', props: ['tracks'], template: '<div />' },
        },
      },
    })
    await flushPromises()

    const table = wrapper.findComponent({ name: 'TrackTable' })
    expect((table.props('tracks') as TrackDTO[])).toHaveLength(5)
    const toggle = wrapper.get('[data-testid="analytics-top-tracks-toggle"]')
    expect(toggle.attributes('aria-expanded')).toBe('false')

    await toggle.trigger('click')
    expect((table.props('tracks') as TrackDTO[])).toHaveLength(50)
    expect(toggle.attributes('aria-expanded')).toBe('true')

    await toggle.trigger('click')
    expect((table.props('tracks') as TrackDTO[])).toHaveLength(5)
  })
})

describe('HomeAnalysis library growth', () => {
  it('renders cumulative library growth separately from total listening time', async () => {
    vi.mocked(AnalyticsService.GetInsights).mockReturnValue(cancellable({
      listened_seconds: 60,
	  streak_days: 3,
      library_tracks: 150,
      library_growth: [
        { date: '2026-07-23', track_count: 100 },
        { date: '2026-07-24', track_count: 150 },
      ],
    }) as unknown as ReturnType<typeof AnalyticsService.GetInsights>)

    const wrapper = mount(HomeAnalysis, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn })],
        stubs: {
          AudioQualityChart: true,
          GenreDistributionChart: true,
          LibraryGrowthChart: { name: 'LibraryGrowthChart', props: ['growth'], template: '<div />' },
          ListeningActivityChart: true,
          TabSwitcher: true,
          TopArtistsCarousel: true,
          TrackTable: true,
        },
      },
    })
    await flushPromises()

    expect(wrapper.findComponent({ name: 'LibraryGrowthChart' }).props('growth')).toEqual([
      { date: '2026-07-23', track_count: 100 },
      { date: '2026-07-24', track_count: 150 },
    ])
    expect(wrapper.get('[data-testid="analytics-library-growth-card"]').text()).toContain('150')
    expect(wrapper.get('[data-testid="analytics-total-time-card"]').classes()).toContain('@5xl:col-span-4')
    expect(wrapper.get('[data-testid="analytics-streak-card"]').text()).toContain('3')
    expect(wrapper.get('[data-testid="analytics-streak-glow"]').attributes('aria-hidden')).toBe('true')
    expect(wrapper.get('[data-testid="analytics-streak-card"]').classes()).toContain('streak-card')
  })
})

describe('HomeAnalysis playback outcomes', () => {
  it('renders a single outcomes donut when attempts exist', async () => {
    vi.mocked(AnalyticsService.GetInsights).mockReturnValue(cancellable({
      completed: 75, skipped: 20, stopped: 5, average_session_seconds: 180,
      library_growth: [], activity: [], quality: [], genres: [], top_artists: [], top_tracks: [],
    }) as unknown as ReturnType<typeof AnalyticsService.GetInsights>)
    const wrapper = mount(HomeAnalysis, { global: { plugins: [createTestingPinia({ createSpy: vi.fn })], stubs: { AudioQualityChart: true, GenreDistributionChart: true, LibraryGrowthChart: true, ListeningActivityChart: true, PlaybackOutcomesChart: { name: 'PlaybackOutcomesChart', props: ['completed', 'skipped', 'stopped'], template: '<div />' }, TabSwitcher: true, TopArtistsCarousel: true, TrackTable: true } } })
    await flushPromises()
    expect(wrapper.get('[data-testid="analytics-playback-outcomes-card"]').text()).toContain('75%')
    expect(wrapper.get('[data-testid="analytics-playback-outcomes-card"]').text()).toContain('20%')
    expect(wrapper.get('[data-testid="analytics-average-session-card"]').text()).toContain('3 min')
    expect(wrapper.getComponent({ name: 'PlaybackOutcomesChart' }).props()).toMatchObject({ completed: 75, skipped: 20, stopped: 5 })
  })

  it('sorts each donut breakdown descending and keeps Other at the bottom', async () => {
    vi.mocked(AnalyticsService.GetInsights).mockReturnValue(cancellable({
      quality: [
        { kind: 'lossless', count: 3 }, { kind: 'lossy', count: 20 }, { kind: 'hi_res', count: 10 },
      ],
      genres: [
        { name: 'Other', listened_seconds: 50, is_other: true },
        { name: 'Rock', listened_seconds: 10, is_other: false },
        { name: 'Pop', listened_seconds: 30, is_other: false },
      ],
      completed: 1, skipped: 9, stopped: 5,
      library_growth: [], activity: [], top_artists: [], top_tracks: [],
    }) as unknown as ReturnType<typeof AnalyticsService.GetInsights>)

    const wrapper = mount(HomeAnalysis, { global: { plugins: [createTestingPinia({ createSpy: vi.fn })], stubs: { AudioQualityChart: true, GenreDistributionChart: true, LibraryGrowthChart: true, ListeningActivityChart: true, PlaybackOutcomesChart: true, TabSwitcher: true, TopArtistsCarousel: true, TrackTable: true } } })
    await flushPromises()

    const rowText = (testId: string) => wrapper.get(`[data-testid="${testId}"]`).findAll(':scope > div').map(row => row.text())
    expect(rowText('analytics-quality-breakdown')).toMatchObject([
      expect.stringContaining('analytics.quality_lossy'),
      expect.stringContaining('analytics.quality_hi_res'),
      expect.stringContaining('analytics.quality_lossless'),
    ])
    expect(rowText('analytics-genre-breakdown')).toMatchObject([
      expect.stringContaining('Pop'), expect.stringContaining('Rock'), expect.stringContaining('analytics.genre_other'),
    ])
    expect(rowText('analytics-playback-outcomes-breakdown')).toMatchObject([
      expect.stringContaining('analytics.outcome_skipped'),
      expect.stringContaining('analytics.outcome_stopped'),
      expect.stringContaining('analytics.outcome_completed'),
    ])
  })
})
