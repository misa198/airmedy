<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { AudioLines, BarChart3, ChevronDown, ChevronUp, Clock, Disc, Flame, HardDrive, ListMusic, Music, RefreshCw, Timer, UserRound } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { Events } from '@wailsio/runtime'
import { formatTotalDuration } from '@airmedy/utils'
import * as AnalyticsService from '../../../bindings/airmedy/internal/infra/wails/analyticsservice'
import * as LibraryService from '../../../bindings/airmedy/internal/infra/wails/libraryservice'
import * as MobilePairingService from '../../../bindings/airmedy/internal/infra/wails/mobilepairingservice'
import type { TrackDTO } from '../../../bindings/airmedy/internal/domain/models'
import { Select, SelectContent, SelectItem, SelectLabel, SelectSeparator, SelectTrigger, SelectValue, TabSwitcher } from '@airmedy/ui'
import { usePlayerStore } from '@/stores/player'
import TrackTable from '@/components/TrackTable.vue'
import type { ColumnDef } from '@/composables/useTrackTableSettings'
import TopArtistsCarousel from './TopArtistsCarousel.vue'
import AudioQualityChart from './AudioQualityChart.vue'
import GenreDistributionChart from './GenreDistributionChart.vue'
import ListeningActivityChart from './ListeningActivityChart.vue'
import LibraryGrowthChart from './LibraryGrowthChart.vue'
import PlaybackOutcomesChart from './PlaybackOutcomesChart.vue'
import { useLibrarySync } from '@/composables/useLibrarySync'

const { t, locale } = useI18n()
const playerStore = usePlayerStore()
const libraryPeriod = ref('7d')
const listeningPeriod = ref('7d')
const sourceDeviceID = ref('all')
const loading = ref(false)
const libraryLoading = ref(false)
const insights = ref<any>({ activity: [], genres: [], top_artists: [], top_tracks: [] })
const libraryInsights = ref<any>({ library_growth: [], quality: [] })
const pairingStatus = ref<{ device_id: string; desktop_name: string } | null>(null)
const trustedDevices = ref<{ device_id: string; display_name: string }[]>([])
const topTrackQueue = shallowRef<TrackDTO[]>([])
const topTracksLoading = ref(false)
const topTracksExpanded = ref(false)
const topTracksPreviewLimit = 5
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
const desktopDeviceID = computed(() => pairingStatus.value?.device_id || 'desktop')
const visibleTopTracks = computed(() => topTracksExpanded.value
  ? topTrackQueue.value
  : topTrackQueue.value.slice(0, topTracksPreviewLimit))
const totalQuality = computed(() => libraryInsights.value.quality.reduce((sum: number, item: any) => sum + item.count, 0) || 0)
const totalGenres = computed(() => insights.value?.genres.reduce((sum: number, item: any) => sum + item.listened_seconds, 0) || 0)
const sortedQuality = computed(() => [...libraryInsights.value.quality].sort((a, b) => b.count - a.count))
const sortedGenres = computed(() => [...(insights.value?.genres ?? [])].sort((a, b) => {
  if (a.is_other !== b.is_other) return a.is_other ? 1 : -1
  return b.listened_seconds - a.listened_seconds
}))
const sortedPlaybackOutcomes = computed(() => [
  { key: 'completed', value: insights.value?.completed ?? 0 },
  { key: 'skipped', value: insights.value?.skipped ?? 0 },
  { key: 'stopped', value: insights.value?.stopped ?? 0 },
].sort((a, b) => b.value - a.value))
const hasListeningData = computed(() => (insights.value?.listened_seconds ?? 0) > 0)
const emptyLibraryInsights = () => ({
  library_tracks: 0,
  library_albums: 0,
  library_artists: 0,
  library_playlists: 0,
  library_bytes: 0,
  library_growth: [],
  quality: [],
})
const emptyInsights = () => ({
  listened_seconds: 0,
  plays: 0,
  streak_days: 0,
  activity: [],
  genres: [],
  top_artists: [],
  top_tracks: [],
})
const normalizeInsights = (result: any) => ({
  ...emptyInsights(),
  ...result,
  activity: result?.activity ?? [],
  genres: result?.genres ?? [],
  top_artists: result?.top_artists ?? [],
  top_tracks: result?.top_tracks ?? [],
})
const normalizeLibraryInsights = (result: any) => ({
  ...emptyLibraryInsights(),
  ...result,
  library_growth: result?.library_growth ?? [],
  quality: result?.quality ?? [],
})
const formatNumber = (value: number) => new Intl.NumberFormat(locale.value).format(value)
const formatTime = (seconds: number) => formatTotalDuration(seconds, t)
const formatBytes = (bytes: number) => new Intl.NumberFormat(locale.value, {
  style: 'unit', unit: 'gigabyte', unitDisplay: 'narrow', maximumFractionDigits: 1,
}).format(bytes / 1024 / 1024 / 1024)

async function loadListeningInsights(silent = false) {
  request?.cancel()
  if (!silent) {
    loading.value = true
  }
  const pending = AnalyticsService.GetListeningInsights(listeningPeriod.value, sourceDeviceID.value === 'all' ? '' : sourceDeviceID.value)
  request = pending
  try {
    const result = await pending
    if (request !== pending) return
    insights.value = normalizeInsights(result)
    topTracksExpanded.value = false
    void loadTopTrackQueue(insights.value.top_tracks)
  } catch (err) { if (request === pending) console.error('Failed to load listening insights:', err) } finally { if (request === pending) { loading.value = false; request = null } }
}
async function loadLibraryInsights(silent = false) {
  libraryRequest?.cancel()
  if (!silent) libraryLoading.value = true
  const pending = AnalyticsService.GetLibraryInsights(libraryPeriod.value)
  libraryRequest = pending
  try {
    const result = await pending
    if (libraryRequest !== pending) return
    libraryInsights.value = normalizeLibraryInsights(result)
  } catch (err) { if (libraryRequest === pending) console.error('Failed to load library insights:', err) } finally { if (libraryRequest === pending) { libraryLoading.value = false; libraryRequest = null } }
}
let request: ReturnType<typeof AnalyticsService.GetListeningInsights> | null = null
let libraryRequest: ReturnType<typeof AnalyticsService.GetLibraryInsights> | null = null
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

function load() {
  return loadListeningInsights()
}

async function loadDevices() {
  try {
    const [status, devices] = await Promise.all([MobilePairingService.GetStatus(), MobilePairingService.GetTrustedDevices()])
    pairingStatus.value = status as { device_id: string; desktop_name: string }
    trustedDevices.value = (devices ?? []) as { device_id: string; display_name: string }[]
    if (sourceDeviceID.value !== 'all' && sourceDeviceID.value !== desktopDeviceID.value && !trustedDevices.value.some(device => device.device_id === sourceDeviceID.value)) {
      sourceDeviceID.value = 'all'
    }
  } catch (err) {
    console.error('Failed to load analytics devices:', err)
  }
}

watch(libraryPeriod, () => {
  void loadLibraryInsights()
}, { immediate: true })
watch([listeningPeriod, sourceDeviceID], () => {
  void loadListeningInsights()
}, { immediate: true })
// Rehydrate top-track DTOs and library summaries after metadata edits without
// replacing the currently visible analytics UI with a loading state.
useLibrarySync(() => {
  void loadLibraryInsights(true)
  void loadListeningInsights(true)
})
let offTrustedDevicesChanged: (() => void) | null = null
onMounted(() => {
  void loadDevices()
  offTrustedDevicesChanged = Events.On('pairing:trusted-devices-changed', loadDevices)
})
onUnmounted(() => {
  request?.cancel()
  libraryRequest?.cancel()
  topTrackRequest?.cancel()
  offTrustedDevicesChanged?.()
})
</script>

<template>
  <section data-testid="home-analysis" :aria-busy="loading || libraryLoading" class="space-y-12 pb-12">
    <div class="@container space-y-8">
      <section class="space-y-6">
        <header class="flex flex-wrap items-center justify-between gap-3">
          <h2 class="text-xl font-semibold">{{ t('analytics.library_section') }}</h2>
          <TabSwitcher v-model="libraryPeriod" :options="periodOptions" variant="label" mandatory data-testid="analytics-library-range-tabs" />
        </header>
        <div class="grid gap-4 @3xl:grid-cols-2 @5xl:grid-cols-4" data-testid="analytics-library-summary">
          <article
            class="rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px]">
            <div class="flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <HardDrive class="h-4 w-4" />{{ t('analytics.library_size') }}
            </div>
            <p class="mt-3 text-2xl font-semibold">{{ formatBytes(libraryInsights.library_bytes) }}</p>
          </article>
          <article
            class="rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px]">
            <div class="flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <Disc class="h-4 w-4" />{{ t('analytics.total_albums') }}
            </div>
            <p class="mt-3 text-2xl font-semibold">{{ formatNumber(libraryInsights.library_albums) }}</p>
          </article>
          <article
            class="rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px]">
            <div class="flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <UserRound class="h-4 w-4" />{{ t('analytics.total_artists') }}
            </div>
            <p class="mt-3 text-2xl font-semibold">{{ formatNumber(libraryInsights.library_artists) }}</p>
          </article>
          <article
            class="rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px]">
            <div class="flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <ListMusic class="h-4 w-4" />{{ t('analytics.total_playlists') }}
            </div>
            <p class="mt-3 text-2xl font-semibold">{{ formatNumber(libraryInsights.library_playlists) }}</p>
          </article>
        </div>
        <div class="grid gap-4 @5xl:grid-cols-8">
          <article
            data-testid="analytics-library-growth-card"
            class="@container relative flex min-h-[17rem] flex-col overflow-hidden rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-6 backdrop-blur-[30px] @5xl:col-span-5">
            <div class="relative mb-6 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <HardDrive class="h-4 w-4" />{{ t('analytics.library_growth') }}
            </div>
            <template v-if="libraryInsights.library_growth.length">
              <div class="relative grid flex-1 grid-cols-1 items-stretch gap-4 @md:grid-cols-[auto_minmax(0,1fr)] @md:gap-6">
                <div>
                  <p class="text-4xl font-bold tracking-[-0.03em]">{{ formatNumber(libraryInsights.library_tracks) }}</p>
                  <p class="mt-2 text-xs text-[color:var(--text-muted)]">{{ t('analytics.library_tracks') }}</p>
                </div>
                <LibraryGrowthChart class="h-full w-full self-stretch" :growth="libraryInsights.library_growth" />
              </div>
            </template>
            <div v-else data-testid="analytics-library-growth-empty" class="relative flex flex-1 flex-col items-center justify-center text-center text-[color:var(--text-muted)]">
              <BarChart3 class="mb-2 h-6 w-6 opacity-60" />
              <p class="text-sm">{{ t('analytics.no_library_growth') }}</p>
            </div>
          </article>
          <article
            class="@container flex min-h-[17rem] flex-col rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px] @5xl:col-span-3">
            <h2 class="mb-4 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <AudioLines class="h-4 w-4" />{{ t('analytics.quality') }}
            </h2>
            <div v-if="libraryInsights.quality.length" class="flex flex-1 flex-col gap-5 @md:flex-row @md:items-center @md:gap-8">
              <AudioQualityChart :quality="sortedQuality" />
              <div data-testid="analytics-quality-breakdown" class="min-w-0 flex-1 space-y-2.5">
                <div v-for="item in sortedQuality" :key="item.kind"
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
      </section>
      <section class="space-y-6 border-t border-[var(--border-glass)] pt-8">
        <header class="flex flex-wrap items-center justify-between gap-3">
          <h2 class="text-xl font-semibold">{{ t('analytics.listening_section') }}</h2>
          <div class="flex flex-wrap items-center justify-end gap-2">
            <Select v-model="sourceDeviceID">
              <SelectTrigger data-testid="analytics-device-filter" class="h-10 w-[180px] border border-foreground/[0.08] bg-foreground/[0.05] text-sm">
                <SelectValue :placeholder="t('analytics.all_devices')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('analytics.all_devices') }}</SelectItem>
                <SelectItem :value="desktopDeviceID">{{ t('analytics.this_device') }}</SelectItem>
                <template v-if="trustedDevices.length">
                  <SelectSeparator />
                  <SelectLabel>{{ t('analytics.trusted_devices') }}</SelectLabel>
                  <SelectItem v-for="device in trustedDevices" :key="device.device_id" :value="device.device_id">{{ device.display_name }}</SelectItem>
                </template>
              </SelectContent>
            </Select>
            <button data-testid="analytics-refresh" type="button" :aria-label="t('analytics.refresh')" :title="t('analytics.refresh')" :disabled="loading"
              class="flex h-10 w-10 items-center justify-center rounded-full border border-foreground/[0.08] bg-foreground/[0.05] text-sm text-foreground opacity-80 transition-colors hover:bg-foreground/[0.1] hover:text-foreground" @click="load">
              <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': loading }" />
            </button>
            <TabSwitcher v-model="listeningPeriod" :options="periodOptions" variant="label" mandatory data-testid="analytics-listening-range-tabs" />
          </div>
        </header>
        <div class="grid gap-4 @5xl:grid-cols-8">
          <article
            data-testid="analytics-total-time-card"
            class="@container relative flex min-h-[17rem] flex-col overflow-hidden rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-6 backdrop-blur-[30px] @5xl:col-span-6">
            <div class="pointer-events-none absolute inset-0] to-transparent opacity-70" />
            <div class="relative mb-6 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <Clock class="h-4 w-4" />{{ t('analytics.total_time') }}
              <span class="ml-auto inline-flex items-center gap-1 rounded-full border border-foreground/[0.08] bg-foreground/[0.05] px-2 py-1 tabular-nums text-foreground/70">
                <Music class="h-3.5 w-3.5" />{{ formatNumber(insights.plays) }} {{ t('analytics.plays') }}
              </span>
            </div>
            <template v-if="hasListeningData">
              <div class="relative grid flex-1 grid-cols-1 items-stretch gap-4 @md:grid-cols-[auto_minmax(0,1fr)] @md:gap-6">
                <div class="flex items-baseline gap-3">
                  <p class="text-4xl font-bold tracking-[-0.03em]">{{ formatTime(insights.listened_seconds) }}</p>
                  <p v-if="insights.change_percent !== undefined" class="text-xs tabular-nums text-[color:var(--text-muted)]">{{
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
            class="streak-card group relative flex min-h-[17rem] flex-col overflow-hidden rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-6 backdrop-blur-[30px] @5xl:col-span-2">
            <div data-testid="analytics-streak-glow" aria-hidden="true" class="streak-glow pointer-events-none absolute -right-16 -bottom-16 h-52 w-52 rounded-full" />
            <Flame aria-hidden="true" class="streak-watermark pointer-events-none absolute -right-10 -bottom-8 h-40 w-40" stroke-width="0.25" fill="currentColor" />
            <div class="relative mb-6 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
              <Flame class="h-4 w-4" />{{ t('analytics.streak') }}
            </div>
            <div class="relative flex flex-1 flex-col justify-center">
              <p class="streak-value text-4xl font-bold tracking-[-0.03em]">{{ formatNumber(insights.streak_days) }}</p>
              <p class="mt-2 text-xs text-[color:var(--text-muted)]">{{ t('analytics.days') }}</p>
            </div>
          </article>
        </div>
          <div class="grid gap-4 @5xl:grid-cols-8">
            <article
              class="@container flex min-h-[17rem] flex-col rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px] @5xl:col-span-3">
              <div class="mb-4 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
                <ListMusic class="h-4 w-4" />{{ t('analytics.genre_distribution') }}
              </div>
              <div v-if="insights.genres?.length"
                class="flex flex-1 flex-col gap-5 @md:flex-row @md:items-center @md:gap-6">
                <GenreDistributionChart :genres="sortedGenres" />
                <div data-testid="analytics-genre-breakdown" class="min-w-0 flex-1 space-y-2.5">
                  <div v-for="genre in sortedGenres" :key="genre.is_other ? 'other' : genre.name"
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
            <article data-testid="analytics-playback-outcomes-card"
              class="@container flex min-h-[17rem] flex-col rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px] @5xl:col-span-3">
              <div class="mb-4 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
                <Music class="h-4 w-4" />{{ t('analytics.playback_outcomes') }}
              </div>
              <div v-if="(insights.completed + insights.skipped + insights.stopped) > 0" class="flex flex-1 flex-col items-center gap-5 @md:flex-row @md:gap-6">
                <PlaybackOutcomesChart :completed="insights.completed" :skipped="insights.skipped" :stopped="insights.stopped" />
                <div data-testid="analytics-playback-outcomes-breakdown" class="min-w-0 flex-1 space-y-2.5 text-xs">
                  <div v-for="item in sortedPlaybackOutcomes" :key="item.key" class="grid grid-cols-[minmax(0,1fr)_auto_auto] gap-x-3">
                    <span class="truncate text-[color:var(--text-muted)]">{{ t(`analytics.outcome_${item.key}`) }}</span>
                    <span class="tabular-nums text-[color:var(--text-muted)]">{{ formatNumber(item.value) }}</span>
                    <span class="w-9 text-right tabular-nums text-[color:var(--text-muted)]">{{ Math.round(item.value / (insights.completed + insights.skipped + insights.stopped) * 100) }}%</span>
                  </div>
                </div>
              </div>
              <div v-else class="flex flex-1 items-center justify-center text-sm text-[color:var(--text-muted)]">{{ t('analytics.no_playback_outcomes') }}</div>
            </article>
            <article data-testid="analytics-average-session-card"
              class="flex min-h-[17rem] flex-col rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px] @5xl:col-span-2">
              <div class="flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
                <Timer class="h-4 w-4" />{{ t('analytics.average_session') }}
              </div>
              <div class="flex flex-1 flex-col justify-center">
                <p class="text-3xl font-semibold tracking-[-0.03em]">{{ insights.average_session_seconds ? formatTime(insights.average_session_seconds) : '—' }}</p>
                <p class="mt-2 text-xs text-[color:var(--text-muted)]">{{ t('analytics.per_playback_attempt') }}</p>
              </div>
            </article>
          </div>
          <TopArtistsCarousel :artists="insights.top_artists" />
          <div class="grid gap-4">
            <article
              class="flex min-w-0 min-h-[17rem] flex-col rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px]">
              <h2 class="mb-4 flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]">
                <Music class="h-4 w-4" />{{ t('analytics.top_tracks') }}
                <button v-if="topTrackQueue.length > topTracksPreviewLimit" data-testid="analytics-top-tracks-toggle"
                  type="button" :aria-expanded="topTracksExpanded" class="ml-auto inline-flex items-center gap-1 rounded-md px-2 py-1 text-[color:var(--text-muted)]"
                  @click="topTracksExpanded = !topTracksExpanded">
                  <span>{{ t(topTracksExpanded ? 'analytics.show_less' : 'analytics.show_more') }}</span>
                  <ChevronUp v-if="topTracksExpanded" class="h-3.5 w-3.5" aria-hidden="true" />
                  <ChevronDown v-else class="h-3.5 w-3.5" aria-hidden="true" />
                </button>
              </h2>
              <div v-if="topTrackQueue.length" class="mt-3 min-w-0">
                <TrackTable :tracks="visibleTopTracks" :show-artwork="true" simple-mode
                  :simple-columns="['index', 'title', 'artist', 'listened_seconds', 'play_count']"
                  :additional-columns="topTrackColumns" :virtual-scroll="false" variant="glass"
                  auto-height
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
      </section>
    </div>
  </section>
</template>

<style scoped>
.streak-card {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.streak-glow {
  background: radial-gradient(circle, rgb(251 146 60 / 0.28), rgb(244 63 94 / 0.1) 42%, transparent 70%);
  filter: blur(12px);
  opacity: 0.7;
  transform: scale(0.9);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.streak-watermark {
  color: rgb(251 146 60 / 0.12);
  transform: rotate(-12deg);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.streak-card:hover {
  border-color: rgb(251 146 60 / 0.3);
}

.streak-card:hover .streak-glow {
  opacity: 1;
  transform: scale(1.12);
}

.streak-card:hover .streak-watermark {
  color: rgb(253 186 116 / 0.2);
  transform: rotate(-5deg) scale(1.04);
}

.streak-card:hover .streak-value {
  text-shadow: 0 0 24px rgb(251 146 60 / 0.35);
}

@media (prefers-reduced-motion: reduce) {
  .streak-card,
  .streak-glow,
  .streak-watermark {
    transition: none;
  }
}
</style>
