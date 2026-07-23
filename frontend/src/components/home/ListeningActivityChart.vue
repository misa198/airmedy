<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatTotalDuration, hexToRgba } from '@airmedy/utils'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import type { EChartsOption } from 'echarts'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import { useAppStore } from '@/stores/app'

use([BarChart, GridComponent, TooltipComponent, SVGRenderer])

const props = defineProps<{
  activity: { date: string; listened_seconds: number }[]
}>()

const { locale, t } = useI18n()
const appStore = useAppStore()

const formatDuration = (seconds: number) => formatTotalDuration(seconds, t)

const dayLabel = (date: string) => {
  const parsed = new Date(date.length === 7 ? `${date}-01T12:00:00` : `${date}T12:00:00`)
  return date.length === 7
    ? new Intl.DateTimeFormat(locale.value, { month: 'short' }).format(parsed)
    : props.activity.length <= 7
      ? new Intl.DateTimeFormat(locale.value, { weekday: 'short' }).format(parsed)
      : new Intl.DateTimeFormat(locale.value, { day: 'numeric' }).format(parsed)
}
const tooltipDate = (date: string) => {
  const parsed = new Date(date.length === 7 ? `${date}-01T12:00:00` : `${date}T12:00:00`)
  return new Intl.DateTimeFormat(locale.value, date.length === 7 ? { month: 'long', year: 'numeric' } : { month: 'short', day: 'numeric', year: 'numeric' }).format(parsed)
}
const chartColors = computed(() => {
  const primary = appStore.primaryColor
  return { primary, area: hexToRgba(primary, 0.22), glow: hexToRgba(primary, 0.38) }
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
      return `${tooltipDate(props.activity[item.dataIndex].date)}<br/><strong>${formatDuration(Number(item.data))}</strong>`
    },
  },
  xAxis: {
    type: 'category',
    boundaryGap: true,
    data: props.activity.map(point => dayLabel(point.date)),
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
    type: 'bar',
    data: props.activity.map(point => point.listened_seconds),
    barMaxWidth: 24,
    barWidth: '54%',
    itemStyle: {
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: chartColors.value.primary },
          { offset: 1, color: chartColors.value.area },
        ],
      },
      borderRadius: [5, 5, 1, 1],
    },
    emphasis: { itemStyle: { shadowBlur: 14, shadowColor: chartColors.value.glow } },
  }],
}))
</script>

<template>
  <div class="h-full min-h-36 min-w-0" data-testid="listening-activity-chart">
    <VChart class="h-full w-full" :option="chartOption" :init-options="{ renderer: 'svg' }" autoresize />
  </div>
</template>
