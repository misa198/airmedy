<script setup lang="ts">
import { computed, onUnmounted, ref, shallowRef, watch } from 'vue'
import { AudioLines, BarChart3, Clock, Disc, Flame, HardDrive, ListMusic, Music, RefreshCw, UserRound } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { formatTotalDuration } from '@airmedy/utils'
import * as AnalyticsService from '../../../bindings/airmedy/internal/infra/wails/analyticsservice'
import * as LibraryService from '../../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { TrackDTO } from '../../../bindings/airmedy/internal/domain/models'
import { TabSwitcher } from '@airmedy/ui'
import { usePlayerStore } from '@/stores/player'
import TrackTable from '@/components/TrackTable.vue'
import type { ColumnDef } from '@/composables/useTrackTableSettings'
import TopArtistsCarousel from './TopArtistsCarousel.vue'
import AudioQualityChart from './AudioQualityChart.vue'
import GenreDistributionChart from './GenreDistributionChart.vue'
import ListeningActivityChart from './ListeningActivityChart.vue'
import LibraryGrowthChart from './LibraryGrowthChart.vue'

const { t, locale } = useI18n()
const playerStore = usePlayerStore()
const period = ref('7d')
const loading = ref(false)
const error = ref(false)
const insights = ref<any>(null)
const topTrackQueue = shallowRef<TrackDTO[]>([])
const topTracksLoading = ref(false)
const topTrackColumns: ColumnDef[] = [{
  key: 'listened_seconds',
  labelKey: 'analytics.total_time',
  gridWidth: '96px',
  minWidthPx: 96,
  alwaysVisible: false,
  sortable: true,
  sortFn: (a, b) => (a.listened_seconds || 0) - (b.listened_seconds || 0),
  formatValue: track => formatTime(track.listened_seconds || 0),
  draggable: false,
}]
const periodOptions = computed(() => [
  { value: '7d', label: t('analytics.range_7d') }, { value: '30d', label: t('analytics.range_30d') }, { value: 'all', label: t('analytics.range_all') },
])
const totalQuality = computed(() => insights.value?.quality.reduce((sum: number, item: any) => sum + item.count, 0) || 0)
const totalGenres = computed(() => insights.value?.genres.reduce((sum: number, item: any) => sum + item.listened_seconds, 0) || 0)
const hasListeningData = computed(() => (insights.value?.listened_seconds ?? 0) > 0)
const emptyInsights = () => ({
  listened_seconds: 0,
  plays: 0,
  streak_days: 0,
  library_tracks: 0,
  library_albums: 0,
  library_artists: 0,
  library_playlists: 0,
  library_bytes: 0,
  library_growth: [],
  activity: [],
  quality: [],
  genres: [],
  top_artists: [],
  top_tracks: [],
})
const normalizeInsights = (result: any) => ({
  ...emptyInsights(),
  ...result,
  library_growth: result?.library_growth ?? [],
  activity: result?.activity ?? [],
  quality: result?.quality ?? [],
  genres: result?.genres ?? [],
  top_artists: result?.top_artists ?? [],
  top_tracks: result?.top_tracks ?? [],
})
const formatNumber = (value: number) => new Intl.NumberFormat(locale.value).format(value)
const formatTime = (seconds: number) => formatTotalDuration(seconds, t)
const formatBytes = (bytes: number) => new Intl.NumberFormat(locale.value, {
  style: 'unit', unit: 'gigabyte', unitDisplay: 'narrow', maximumFractionDigits: 1,
}).format(bytes / 1024 / 1024 / 1024)

async function load() {
  request?.cancel()
  loading.value = true; error.value = false
  const pending = AnalyticsService.GetInsights(period.value)
  request = pending
  try {
    const result = await pending
    if (request !== pending) return
    insights.value = normalizeInsights(result)
    void loadTopTrackQueue(insights.value.top_tracks)
  } catch (err) { if (request === pending) { console.error('Failed to load analytics:', err); error.value = true } } finally { if (request === pending) { loading.value = false; request = null } }
}
let request: ReturnType<typeof AnalyticsService.GetInsights> | null = null
let topTrackRequest: ReturnType<typeof LibraryService.GetTracksByIDs> | null = null

async function loadTopTrackQueue(topTracks: { id: string; play_count: number; listened_seconds: number }[]) {
  topTrackRequest?.cancel()
  const ids = topTracks.map(track => track.id)
  if (!ids.length) { topTrackQueue.value = []; topTracksLoading.value = false; return }
  topTracksLoading.value = true
  const pending = LibraryService.GetTracksByIDs(ids)
  topTrackRequest = pending
  try {
    const tracks = (await pending).filter((track): track is TrackDTO => track !== null)
    if (topTrackRequest !== pending) return
    const byID = new Map(tracks.map(track => [track.id, track]))
    const analyticsByID = new Map(topTracks.map(track => [track.id, track]))
    topTrackQueue.value = ids.flatMap((id) => {
      const track = byID.get(id)
      const analytics = analyticsByID.get(id)
      return track && analytics ? [{
        ...track,
        play_count: analytics.play_count,
        listened_seconds: analytics.listened_seconds,
      }] : []
    })
  } catch (err) {
    if (topTrackRequest === pending) console.error('Failed to load top tracks:', err)
  } finally {
    if (topTrackRequest === pending) { topTrackRequest = null; topTracksLoading.value = false }
  }
}

watch(period, load, { immediate: true })
onUnmounted(() => {
  request?.cancel()
  topTrackRequest?.cancel()
})
</script>

<template>
  <section data-testid="home-analysis" :aria-busy="loading" class="space-y-8 pb-12">
    <div class="flex justify-end">
      <div class="flex items-center gap-2">
        <button data-testid="analytics-refresh" type="button" :aria-label="t('analytics.refresh')"
          :title="t('analytics.refresh')" :disabled="loading"
          class="flex items-center justify-center w-10 h-10 rounded-full text-sm transition-colors border border-foreground/[0.08] bg-foreground/[0.05] text-foreground opacity-80 hover:bg-foreground/[0.1] hover:text-foreground"
          @click="load">
          <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': loading }" />
        </button>
        <TabSwitcher v-model="period" :options="periodOptions" variant="label" mandatory
          data-testid="analytics-range-tabs" />
      </div>
    </div>
    <div class="@container space-y-8">
      <div v-if="loading && !insights" class="grid gap-4 @3xl:grid-cols-3">
        <div v-for="i in 3" :key="i"
          class="h-48 rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] animate-pulse" />
      </div>
      <div v-else-if="error && !insights" class="py-20 text-center text-white/40">
        <p>{{ t('analytics.error') }}</p>
        <button class="mt-3 text-white/70" @click="load">{{ t('analytics.retry') }}</button>
      </div>
      <template v-else>
        <div class="grid gap-4 @5xl:grid-cols-5">
          <article
            data-testid="analytics-library-growth-card"
            class="@container relative flex min-h-[17rem] flex-col overflow-hidden rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-6 backdrop-blur-[30px] @5xl:col-span-3">
            <div class="pointer-events-none absolute inset-0 bg-gradient-to-br from-[var(--dynamic-surface)] to-transparent opacity-70" />
            <div class="relative mb-6 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <HardDrive class="h-4 w-4" />{{ t('analytics.library_growth') }}
            </div>
            <template v-if="insights.library_growth.length">
              <div class="relative grid flex-1 grid-cols-1 items-stretch gap-4 @md:grid-cols-[auto_minmax(0,1fr)] @md:gap-6">
                <div>
                  <p class="text-4xl font-bold tracking-[-0.03em]">{{ formatNumber(insights.library_tracks) }}</p>
                  <p class="mt-2 text-xs text-[color:var(--text-muted)]">{{ t('analytics.library_tracks') }}</p>
                </div>
                <LibraryGrowthChart class="h-full w-full self-stretch" :growth="insights.library_growth" />
              </div>
            </template>
            <div v-else data-testid="analytics-library-growth-empty" class="relative flex flex-1 flex-col items-center justify-center text-center text-[color:var(--text-muted)]">
              <BarChart3 class="mb-2 h-6 w-6 opacity-60" />
              <p class="text-sm">{{ t('analytics.no_library_growth') }}</p>
            </div>
          </article>
          <article
            class="@container flex min-h-[17rem] flex-col rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px] @5xl:col-span-2">
            <h2 class="mb-4 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <AudioLines class="h-4 w-4" />{{ t('analytics.quality') }}
            </h2>
            <div v-if="insights.quality?.length" class="flex flex-1 flex-col gap-5 @md:flex-row @md:items-center @md:gap-8">
              <AudioQualityChart :quality="insights.quality" />
              <div class="min-w-0 flex-1 space-y-2.5">
                <div v-for="item in insights.quality" :key="item.kind"
                  class="grid grid-cols-[minmax(0,1fr)_auto_auto] items-center gap-x-3 text-xs">
                  <span class="truncate text-[color:var(--text-muted)]">{{ t(`analytics.quality_${item.kind}`) }}</span>
                  <span class="text-right tabular-nums text-[color:var(--text-muted)]">{{ formatNumber(item.count) }}</span>
                  <span class="w-9 text-right tabular-nums text-[color:var(--text-muted)]">{{ totalQuality ? Math.round(item.count / totalQuality * 100) : 0 }}%</span>
                </div>
              </div>
            </div>
            <div v-else data-testid="analytics-quality-empty" class="flex flex-1 flex-col items-center justify-center text-center text-[color:var(--text-muted)]">
              <AudioLines class="mb-2 h-6 w-6 opacity-60" />
              <p class="text-sm">{{ t('analytics.no_audio_quality') }}</p>
            </div>
          </article>
        </div>
        <div class="grid gap-4 @3xl:grid-cols-2 @5xl:grid-cols-5" data-testid="analytics-library-summary">
          <article
            class="rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px]">
            <div class="flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <HardDrive class="h-4 w-4" />{{ t('analytics.library_size') }}
            </div>
            <p class="mt-3 text-2xl font-semibold">{{ formatBytes(insights.library_bytes) }}</p>
          </article>
          <article
            class="rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px]">
            <div class="flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <Disc class="h-4 w-4" />{{ t('analytics.total_albums') }}
            </div>
            <p class="mt-3 text-2xl font-semibold">{{ formatNumber(insights.library_albums) }}</p>
          </article>
          <article
            class="rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px]">
            <div class="flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <UserRound class="h-4 w-4" />{{ t('analytics.total_artists') }}
            </div>
            <p class="mt-3 text-2xl font-semibold">{{ formatNumber(insights.library_artists) }}</p>
          </article>
          <article
            class="rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px]">
            <div class="flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <ListMusic class="h-4 w-4" />{{ t('analytics.total_playlists') }}
            </div>
            <p class="mt-3 text-2xl font-semibold">{{ formatNumber(insights.library_playlists) }}</p>
          </article>
          <article
              class="rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px]">
            <div class="flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <Music class="h-4 w-4" />{{ t('analytics.plays') }}
            </div>
            <p class="mt-3 text-2xl font-semibold">{{ formatNumber(insights.plays) }}</p>
          </article>
        </div>
        <div class="grid gap-4 @5xl:grid-cols-5">
          <article
            data-testid="analytics-total-time-card"
            class="@container relative flex min-h-[17rem] flex-col overflow-hidden rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-6 backdrop-blur-[30px] @5xl:col-span-4">
            <div class="pointer-events-none absolute inset-0 bg-gradient-to-br from-[var(--dynamic-surface)] to-transparent opacity-70" />
            <div class="relative mb-6 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <Clock class="h-4 w-4" />{{ t('analytics.total_time') }}
            </div>
            <template v-if="hasListeningData">
              <div class="relative grid flex-1 grid-cols-1 items-stretch gap-4 @md:grid-cols-[auto_minmax(0,1fr)] @md:gap-6">
                <div>
                  <p class="text-4xl font-bold tracking-[-0.03em]">{{ formatTime(insights.listened_seconds) }}</p>
                  <p v-if="insights.change_percent !== undefined" class="mt-2 text-xs text-[color:var(--text-muted)]">{{
                    insights.change_percent >= 0 ? '+' : '' }}{{ insights.change_percent.toFixed(0) }}%</p>
                </div>
                <ListeningActivityChart class="h-full w-full self-stretch" :activity="insights.activity" />
              </div>
            </template>
            <div v-else data-testid="analytics-listening-empty" class="relative flex flex-1 flex-col items-center justify-center text-center text-[color:var(--text-muted)]">
              <BarChart3 class="mb-2 h-6 w-6 opacity-60" />
              <p class="text-sm">{{ t('analytics.no_listening_data') }}</p>
            </div>
          </article>
          <article
            data-testid="analytics-streak-card"
            class="relative flex min-h-[17rem] flex-col overflow-hidden rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-6 backdrop-blur-[30px] @5xl:col-span-1">
            <div class="pointer-events-none absolute inset-0 bg-gradient-to-br from-[var(--dynamic-surface)] to-transparent opacity-70" />
            <div class="relative mb-6 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <Flame class="h-4 w-4" />{{ t('analytics.streak') }}
            </div>
            <div class="relative flex flex-1 flex-col justify-center">
              <p class="text-4xl font-bold tracking-[-0.03em]">{{ formatNumber(insights.streak_days) }}</p>
              <p class="mt-2 text-xs text-[color:var(--text-muted)]">{{ t('analytics.days') }}</p>
            </div>
          </article>
        </div>
          <div class="grid gap-4 @5xl:grid-cols-5">
            <article
              class="@container flex min-h-[17rem] flex-col rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px] @5xl:col-span-2">
              <div class="mb-4 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
                <ListMusic class="h-4 w-4" />{{ t('analytics.genre_distribution') }}
              </div>
              <div v-if="insights.genres?.length"
                class="flex flex-1 flex-col gap-5 @md:flex-row @md:items-center @md:gap-6">
                <GenreDistributionChart :genres="insights.genres" />
                <div class="min-w-0 flex-1 space-y-2.5">
                  <div v-for="genre in insights.genres" :key="genre.is_other ? 'other' : genre.name"
                    class="grid grid-cols-[minmax(0,1fr)_auto_auto] items-center gap-x-3 text-xs">
                    <span class="truncate text-[color:var(--text-muted)]">{{ genre.is_other ? t('analytics.genre_other')
                      : genre.name }}</span>
                    <span class="text-right tabular-nums text-[color:var(--text-muted)]">{{
                      formatTime(genre.listened_seconds) }}</span>
                    <span class="w-9 text-right tabular-nums text-[color:var(--text-muted)]">{{ totalGenres ?
                      Math.round(genre.listened_seconds / totalGenres * 100) : 0 }}%</span>
                  </div>
                </div>
              </div>
              <div v-else class="flex flex-1 flex-col items-center justify-center text-center text-[color:var(--text-muted)]">
                <ListMusic class="mb-2 h-6 w-6 opacity-60" />
                <p class="text-sm">{{ t('analytics.no_genres') }}</p>
              </div>
            </article>
            <article
              class="flex min-h-[17rem] flex-col rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px] @5xl:col-span-3">
              <h2 class="mb-4 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
                <Music class="h-4 w-4" />{{ t('analytics.top_tracks') }}
              </h2>
              <div v-if="topTrackQueue.length" class="mt-3 h-96">
                <TrackTable :tracks="topTrackQueue" :show-artwork="true" simple-mode
                  :simple-columns="['index', 'title', 'artist', 'listened_seconds', 'play_count']"
                  :additional-columns="topTrackColumns" :virtual-scroll="false" variant="glass"
                  @play-track="(_, index, queue) => playerStore.playTracks(queue, index)" />
              </div>
              <div v-else-if="topTracksLoading" class="flex-1 animate-pulse rounded-lg bg-white/[0.04]" />
              <div v-else-if="insights.top_tracks.length"
                class="flex flex-1 items-center justify-center text-sm text-[color:var(--text-muted)]">{{
                  t('analytics.no_top_tracks') }}</div>
              <div v-else
                class="flex flex-1 flex-col items-center justify-center text-center text-[color:var(--text-muted)]">
                <Music class="mb-2 h-6 w-6 opacity-60" />
                <p class="text-sm">{{ t('analytics.no_top_tracks') }}</p>
              </div>
            </article>
          </div>
          <TopArtistsCarousel :artists="insights.top_artists" />
      </template>
    </div>
  </section>
</template>
