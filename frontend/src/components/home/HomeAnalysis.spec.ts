import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { describe, expect, it, vi } from 'vitest'
import HomeAnalysis from './HomeAnalysis.vue'
import * as AnalyticsService from '../../../bindings/airmedy/internal/infra/wails/analyticsservice'

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
  })
})
