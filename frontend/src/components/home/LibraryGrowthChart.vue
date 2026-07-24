<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import type { EChartsOption } from 'echarts'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import { hexToRgba } from '@airmedy/utils'
import { useAppStore } from '@/stores/app'

use([LineChart, GridComponent, TooltipComponent, SVGRenderer])

const props = defineProps<{
  growth: { date: string; track_count: number }[]
}>()

const { locale } = useI18n()
const appStore = useAppStore()
const formatNumber = (value: number) => new Intl.NumberFormat(locale.value).format(value)

const parseDate = (date: string) => new Date(date.length === 4 ? `${date}-01-01T12:00:00` : `${date}T12:00:00`)
const dayLabel = (date: string) => {
  if (date.length === 4) return date
  const parsed = parseDate(date)
  return props.growth.length <= 7
    ? new Intl.DateTimeFormat(locale.value, { weekday: 'short' }).format(parsed)
    : new Intl.DateTimeFormat(locale.value, { day: 'numeric' }).format(parsed)
}
const tooltipDate = (date: string) => new Intl.DateTimeFormat(locale.value,
  date.length === 4 ? { year: 'numeric' } : { month: 'short', day: 'numeric', year: 'numeric' },
).format(parseDate(date))
const chartColors = computed(() => {
  const primary = appStore.primaryColor
  return { primary, areaStart: hexToRgba(primary, 0.42), areaEnd: hexToRgba(primary, 0), glow: hexToRgba(primary, 0.4) }
})
const tooltipSurface = computed(() => {
  void appStore.theme
  const root = document.documentElement
  if (root.classList.contains('black')) return { background: 'rgba(25, 25, 25, 0.96)', border: 'rgba(255, 255, 255, 0.12)', text: '#ffffff' }
  if (root.classList.contains('dark')) return { background: 'rgba(55, 55, 60, 0.96)', border: 'rgba(255, 255, 255, 0.12)', text: '#ffffff' }
  return { background: 'rgba(255, 255, 255, 0.96)', border: 'rgba(0, 0, 0, 0.12)', text: '#0a0a0a' }
})

const chartOption = computed<EChartsOption>(() => ({
  animationDuration: 700,
  animationDurationUpdate: 450,
  animationEasing: 'cubicOut',
  grid: { top: 14, right: 8, bottom: 26, left: 8 },
  tooltip: {
    trigger: 'axis',
    backgroundColor: tooltipSurface.value.background,
    borderColor: tooltipSurface.value.border,
    borderWidth: 1,
    extraCssText: 'border-radius: 12px; backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.35);',
    padding: [8, 10],
    textStyle: { color: tooltipSurface.value.text, fontSize: 12 },
    formatter: (params) => {
      const item = Array.isArray(params) ? params[0] : params
      return `${tooltipDate(props.growth[item.dataIndex].date)}<br/><strong>${formatNumber(Number(item.data))}</strong>`
    },
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: props.growth.map(point => dayLabel(point.date)),
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: 'var(--text-muted)', fontSize: 10, margin: 12 },
  },
  yAxis: {
    type: 'value',
    show: false,
    min: 0,
    splitNumber: 3,
    splitLine: { show: true, lineStyle: { color: 'var(--border-glass)', type: 'dashed', opacity: 0.45 } },
  },
  series: [{
    type: 'line',
    data: props.growth.map(point => point.track_count),
    smooth: true,
    showSymbol: props.growth.length === 1,
    symbolSize: props.growth.length === 1 ? 10 : 0,
    lineStyle: { color: chartColors.value.primary, width: 2.5 },
    itemStyle: { color: chartColors.value.primary, borderColor: chartColors.value.primary, borderWidth: 0 },
    areaStyle: {
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: chartColors.value.areaStart },
          { offset: 1, color: chartColors.value.areaEnd },
        ],
      },
    },
    emphasis: { itemStyle: { color: chartColors.value.primary, borderColor: chartColors.value.primary, borderWidth: 0 } },
  }],
}))
</script>

<template>
  <div class="h-full min-h-36 min-w-0" data-testid="library-growth-chart">
    <VChart class="h-full w-full" :option="chartOption" :init-options="{ renderer: 'svg' }" autoresize />
  </div>
</template>
