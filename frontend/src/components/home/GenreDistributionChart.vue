<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatTotalDuration, hexToRgba } from '@airmedy/utils'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import type { EChartsOption } from 'echarts'
import { PieChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import { useAppStore } from '@/stores/app'

use([PieChart, TooltipComponent, SVGRenderer])

const props = defineProps<{
  genres: { name: string; listened_seconds: number; is_other: boolean }[]
}>()

const { t, locale } = useI18n()
const appStore = useAppStore()
const total = computed(() => props.genres.reduce((sum, genre) => sum + genre.listened_seconds, 0))
const palette = computed(() => [
  appStore.primaryColor,
  '#a5b4fc',
  '#67e8f9',
  '#fda4af',
  '#fcd34d',
  '#94a3b8',
])
const tooltipSurface = computed(() => {
  void appStore.theme
  const root = document.documentElement
  if (root.classList.contains('black')) return { background: 'rgba(25, 25, 25, 0.96)', border: 'rgba(255, 255, 255, 0.12)', text: '#ffffff' }
  if (root.classList.contains('dark')) return { background: 'rgba(55, 55, 60, 0.96)', border: 'rgba(255, 255, 255, 0.12)', text: '#ffffff' }
  return { background: 'rgba(255, 255, 255, 0.96)', border: 'rgba(0, 0, 0, 0.12)', text: '#0a0a0a' }
})

const formatDuration = (seconds: number) => formatTotalDuration(seconds, t)

const chartOption = computed<EChartsOption>(() => ({
  animationDuration: 650,
  animationDurationUpdate: 400,
  animationEasing: 'cubicOut',
  tooltip: {
    trigger: 'item',
    backgroundColor: tooltipSurface.value.background,
    borderColor: tooltipSurface.value.border,
    borderWidth: 1,
    extraCssText: 'border-radius: 12px; backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.35);',
    padding: [8, 10],
    textStyle: { color: tooltipSurface.value.text, fontSize: 12 },
    formatter: (params) => {
      const item = Array.isArray(params) ? params[0] : params
      return `${item.name}<br/><strong>${formatDuration(Number(item.value))} · ${item.percent}%</strong>`
    },
  },
  series: [{
    type: 'pie',
    radius: ['62%', '82%'],
    center: ['50%', '50%'],
    startAngle: 90,
    itemStyle: { borderColor: 'var(--bg-glass)', borderWidth: 3, borderRadius: 5 },
    label: { show: false },
    labelLine: { show: false },
    emphasis: { scale: true, scaleSize: 5, itemStyle: { shadowBlur: 16, shadowColor: hexToRgba(appStore.primaryColor, 0.38) } },
    data: props.genres.map((genre, index) => ({
      value: genre.listened_seconds,
      name: genre.is_other ? t('analytics.genre_other') : genre.name,
      itemStyle: { color: palette.value[index] || palette.value.at(-1) },
    })),
  }],
}))
</script>

<template>
  <div class="relative h-[clamp(9rem,18vw,12rem)] w-[clamp(9rem,18vw,12rem)] shrink-0" data-testid="genre-distribution-chart">
    <VChart class="h-full w-full" :option="chartOption" :init-options="{ renderer: 'svg' }" autoresize />
    <div class="pointer-events-none absolute inset-0 flex items-center justify-center">
      <span class="text-xl font-semibold tracking-tight">{{ formatDuration(total) }}</span>
    </div>
  </div>
</template>
