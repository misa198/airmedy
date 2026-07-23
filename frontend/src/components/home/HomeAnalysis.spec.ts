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
          ListeningActivityChart: true,
          TabSwitcher: true,
          TopArtistsCarousel: true,
          TrackTable: true,
        },
      },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="analytics-listening-empty"]').text()).toContain('No listening data for this period yet.')
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
})
