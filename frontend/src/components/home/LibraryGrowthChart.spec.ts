import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import LibraryGrowthChart from './LibraryGrowthChart.vue'

vi.mock('vue-echarts', () => ({
  default: { name: 'VChart', props: ['option'], template: '<div />' },
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ locale: { value: 'en' } }),
}))

vi.mock('@/stores/app', () => ({
  useAppStore: () => ({ primaryColor: '#E11D48', theme: 'dark' }),
}))

describe('LibraryGrowthChart', () => {
  it('shows a visible marker when All has data for only one year', () => {
    const wrapper = mount(LibraryGrowthChart, {
      props: { growth: [{ date: '2026', track_count: 150 }] },
    })

    const option = wrapper.findComponent({ name: 'VChart' }).props('option') as any
    expect(option.series[0]).toMatchObject({
      showSymbol: true,
      symbolSize: 10,
      data: [150],
      itemStyle: { color: '#E11D48', borderColor: '#E11D48', borderWidth: 0 },
    })
  })

  it('keeps symbols hidden for multi-point growth series', () => {
    const wrapper = mount(LibraryGrowthChart, {
      props: { growth: [{ date: '2025', track_count: 100 }, { date: '2026', track_count: 150 }] },
    })

    const option = wrapper.findComponent({ name: 'VChart' }).props('option') as any
    expect(option.series[0]).toMatchObject({ showSymbol: false, symbolSize: 0 })
  })
})
