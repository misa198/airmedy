<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { AudioLines, Wrench, Gauge, Volume2, Blend } from '@lucide/vue'
import { Events } from '@wailsio/runtime'
import { GetProgress } from '../../../bindings/airmedy/internal/infra/wails/analysisservice'
import EQPanel from '@/components/EQPanel.vue'
import { Switch, Select, SelectTrigger, SelectValue, SelectContent, SelectItem, Slider } from '@airmedy/ui'
import { useAppStore, NORMALIZATION_TARGET_LUFS_MIN, NORMALIZATION_TARGET_LUFS_MAX, CROSSFADE_MAX_SECONDS } from '@/stores/app'
import { MAX_QUEUE_SIZE_OPTIONS, type MaxQueueSize } from '@/lib/queue'
import SettingSection from './SettingSection.vue'
import SettingRow from './SettingRow.vue'

const { t } = useI18n()
const appStore = useAppStore()

// Live value while dragging; only persisted (via the store, which clamps +
// calls NormalizationService.SetTarget) on release — same pattern as EQPanel's
// band sliders, avoids spamming the backend per drag-frame and needs no Enter/blur.
const lufsLive = ref(appStore.normalizationTargetLufs)
const lufsDragging = ref(false)
watch(() => appStore.normalizationTargetLufs, (val) => {
  if (!lufsDragging.value) lufsLive.value = val
})
const onLufsInput = (val: number) => {
  lufsDragging.value = true
  lufsLive.value = val
}
const onLufsRelease = () => {
  appStore.updateNormalizationTargetLufs(lufsLive.value)
  lufsDragging.value = false
}

// Crossfade is split into an on/off switch and a duration slider. Backend still
// stores a single crossfade_seconds where 0 = off, so the switch just toggles
// between 0 and the remembered duration.
const CROSSFADE_DEFAULT_SECONDS = 6
const crossfadeEnabled = computed(() => appStore.crossfadeSeconds > 0)
// Remember the last non-zero duration so flipping the switch off then on
// restores the previous value instead of resetting to 0.
const lastCrossfadeSeconds = ref(
  appStore.crossfadeSeconds > 0 ? appStore.crossfadeSeconds : CROSSFADE_DEFAULT_SECONDS
)
watch(() => appStore.crossfadeSeconds, (val) => {
  if (val > 0) lastCrossfadeSeconds.value = val
})
const onCrossfadeToggle = (on: boolean) => {
  appStore.updateCrossfadeSeconds(on ? lastCrossfadeSeconds.value : 0)
}

// Duration slider: live value while dragging, persisted on release —
// same pattern as the LUFS slider below. While disabled it shows the
// remembered value so the switch's off state doesn't zero the slider.
const crossfadeLive = ref(lastCrossfadeSeconds.value)
const crossfadeDragging = ref(false)
watch(() => appStore.crossfadeSeconds, (val) => {
  if (!crossfadeDragging.value && val > 0) crossfadeLive.value = val
})
const crossfadeSliderValue = computed(() =>
  crossfadeEnabled.value ? crossfadeLive.value : lastCrossfadeSeconds.value
)
const onCrossfadeInput = (val: number) => {
  crossfadeDragging.value = true
  crossfadeLive.value = val
}
const onCrossfadeRelease = () => {
  appStore.updateCrossfadeSeconds(crossfadeLive.value)
  crossfadeDragging.value = false
}

const LUFS_MARKS = [
  { value: -20, labelKey: 'settings.normalization.mark_quiet' },
  { value: -14, labelKey: 'settings.normalization.mark_balanced' },
  { value: -10, labelKey: 'settings.normalization.mark_loud' },
  { value: -8, labelKey: 'settings.normalization.mark_very_loud' },
] as const

const lufsMarkPct = (value: number) =>
  ((value - NORMALIZATION_TARGET_LUFS_MIN) / (NORMALIZATION_TARGET_LUFS_MAX - NORMALIZATION_TARGET_LUFS_MIN)) * 100

const analysisDone = ref(0)
const analysisTotal = ref(0)
const analysisState = ref<'analyzing' | 'paused' | 'done'>('done')
const libraryDone = ref(0)
const libraryTotal = ref(0)

// Session progress: how far the current analysis run has gotten through the
// tracks it found pending when it started.
const analysisPercent = computed(() =>
  analysisTotal.value > 0 ? Math.round((analysisDone.value / analysisTotal.value) * 100) : 100
)
// Library readiness: how much of the whole library has ever been analyzed.
// Distinct from analysisPercent — adding new tracks drops this but resets
// analysisPercent's own session to 0%, so the two must not share one number.
const readinessPercent = computed(() =>
  libraryTotal.value > 0 ? Math.round((libraryDone.value / libraryTotal.value) * 100) : 100
)

type AnalysisProgressData = {
  done: number
  total: number
  state: 'analyzing' | 'paused' | 'done'
  libraryDone: number
  libraryTotal: number
}

const applyAnalysisProgress = (data: AnalysisProgressData) => {
  analysisDone.value = data.done
  analysisTotal.value = data.total
  analysisState.value = data.state
  libraryDone.value = data.libraryDone
  libraryTotal.value = data.libraryTotal
}

// Set once a live event has landed, so the initial GetProgress fetch (below)
// knows to discard its own result if it resolves after a fresher event
// already updated the refs — otherwise a slow IPC round-trip could overwrite
// newer data with a stale snapshot.
let receivedLiveEvent = false

const handleAnalysisProgress = (ev: Events.WailsEvent) => {
  const data = ev.data as AnalysisProgressData
  console.debug('[analysis:progress]', data)
  receivedLiveEvent = true
  applyAnalysisProgress(data)
}

let offAnalysisProgress: (() => void) | null = null

onMounted(() => {
  // Subscribe first so no event landing between the fetch and its resolution
  // is missed, then fetch the current snapshot immediately — otherwise the
  // UI starts from the zero-valued refs above (100% readiness, no
  // in-progress banner) until the next event happens to fire, which could be
  // long after a sync already left the library partially analyzed.
  offAnalysisProgress = Events.On('analysis:progress', handleAnalysisProgress)
  GetProgress()
    .then((data) => {
      if (!receivedLiveEvent) applyAnalysisProgress(data as unknown as AnalysisProgressData)
    })
    .catch((error) => console.error('[analysis:progress] initial fetch failed', error))
})

onUnmounted(() => {
  offAnalysisProgress?.()
})
</script>

<template>
  <div class="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <SettingSection :icon="Wrench" :label="t('settings.playback.general')">
      <SettingRow :title="t('settings.playback.prevent_sleep')"
        :description="t('settings.playback.prevent_sleep_desc')">
        <Switch :model-value="appStore.preventSleepWhilePlaying"
          @update:model-value="appStore.updatePreventSleepWhilePlaying" />
      </SettingRow>
      <SettingRow :title="t('settings.playback.show_player_indicator')"
        :description="t('settings.playback.show_player_indicator_desc')">
        <Switch :model-value="appStore.showPlayerIndicator" @update:model-value="appStore.updateShowPlayerIndicator" />
      </SettingRow>
      <SettingRow :title="t('settings.playback.max_queue_size')"
        :description="t('settings.playback.max_queue_size_desc')">
        <Select :model-value="String(appStore.maxQueueSize)"
          @update:model-value="val => appStore.updateMaxQueueSize(Number(val) as MaxQueueSize)">
          <SelectTrigger class="w-[120px] bg-foreground/[0.04] border-0 h-9 text-sm">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem v-for="size in MAX_QUEUE_SIZE_OPTIONS" :key="size" :value="String(size)">
              {{ size }}
            </SelectItem>
          </SelectContent>
        </Select>
      </SettingRow>
    </SettingSection>

    <SettingSection :icon="AudioLines" :label="t('settings.equalizer.title')" variant="panel">
      <EQPanel />
    </SettingSection>

    <SettingSection :icon="Blend" :label="t('settings.playback.song_transition')">
      <SettingRow :title="t('settings.playback.crossfade')" :description="t('settings.playback.crossfade_desc')">
        <Switch :model-value="crossfadeEnabled" @update:model-value="onCrossfadeToggle" />
      </SettingRow>
      <div class="p-5 transition-opacity" :class="!crossfadeEnabled && 'opacity-40 pointer-events-none'">
        <div class="flex items-center justify-between gap-x-2 mb-3">
          <p class="text-sm font-semibold">{{ t('settings.playback.crossfade_duration') }}</p>
          <p class="text-sm font-semibold tabular-nums">{{ crossfadeSliderValue }} s</p>
        </div>
        <div class="flex items-center gap-x-3">
          <span class="text-xs text-foreground opacity-60 tabular-nums">1</span>
          <Slider class="flex-1" :model-value="crossfadeSliderValue" :min="1" :max="CROSSFADE_MAX_SECONDS" :step="1"
            @update:model-value="onCrossfadeInput" @mouseup="onCrossfadeRelease" @touchend="onCrossfadeRelease" />
          <span class="text-xs text-foreground opacity-60 tabular-nums">{{ CROSSFADE_MAX_SECONDS }}</span>
        </div>
      </div>
    </SettingSection>

    <SettingSection :icon="Gauge" :label="t('settings.library_analysis.title')">
      <template #header-extra>
        <p v-if="appStore.libraryAnalysisEnabled" class="text-xs text-foreground opacity-50">
          {{ t('settings.normalization.readiness', { percent: readinessPercent }) }}
        </p>
      </template>
      <SettingRow :title="t('settings.library_analysis.enable')"
        :description="t('settings.library_analysis.enable_desc')">
        <Switch :model-value="appStore.libraryAnalysisEnabled"
          @update:model-value="appStore.updateLibraryAnalysisEnabled" />
      </SettingRow>
      <div v-if="appStore.libraryAnalysisEnabled && analysisState !== 'done'"
        class="px-5 py-3 text-xs text-foreground opacity-60">
        {{ t('settings.normalization.analyzing', { done: analysisDone, total: analysisTotal, percent: analysisPercent })
        }}
      </div>
    </SettingSection>

    <SettingSection :icon="Volume2" :label="t('settings.normalization.title')">
      <p v-if="!appStore.libraryAnalysisEnabled" class="px-5 py-3 text-xs text-foreground opacity-50">
        {{ t('settings.library_analysis.requires_enable') }}
      </p>
      <SettingRow class="transition-opacity"
        :class="!appStore.libraryAnalysisEnabled && 'opacity-40 pointer-events-none'"
        :title="t('settings.normalization.enable')" :description="t('settings.normalization.enable_desc')">
        <Switch :model-value="appStore.normalizationEnabled"
          @update:model-value="appStore.updateNormalizationEnabled" />
      </SettingRow>
      <SettingRow class="transition-opacity"
        :class="(!appStore.libraryAnalysisEnabled || !appStore.normalizationEnabled) && 'opacity-40 pointer-events-none'"
        :title="t('settings.normalization.mode')">
        <template #description>
          <p class="text-xs text-foreground opacity-60">{{ t('settings.normalization.mode_track_desc') }}</p>
          <p class="text-xs text-foreground opacity-60">{{ t('settings.normalization.mode_album_desc') }}</p>
        </template>
        <Select :model-value="appStore.normalizationMode"
          @update:model-value="val => appStore.updateNormalizationMode(val as 'track' | 'album')">
          <SelectTrigger class="w-[160px] bg-foreground/[0.04] border-0 h-9 text-sm">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="track">{{ t('settings.normalization.track_mode') }}</SelectItem>
            <SelectItem value="album">{{ t('settings.normalization.album_mode') }}</SelectItem>
          </SelectContent>
        </Select>
      </SettingRow>
      <div class="p-5 transition-opacity"
        :class="(!appStore.libraryAnalysisEnabled || !appStore.normalizationEnabled) && 'opacity-40 pointer-events-none'">
        <div class="flex items-center justify-between gap-x-2 mb-3">
          <p class="text-sm font-semibold">{{ t('settings.normalization.target_lufs') }}</p>
          <p class="text-sm font-semibold tabular-nums">{{ lufsLive }} LUFS</p>
        </div>
        <div class="flex items-center gap-x-3">
          <span class="text-xs text-foreground opacity-60 tabular-nums">{{ NORMALIZATION_TARGET_LUFS_MIN }}</span>
          <div class="relative flex-1">
            <Slider :model-value="lufsLive" :min="NORMALIZATION_TARGET_LUFS_MIN" :max="NORMALIZATION_TARGET_LUFS_MAX"
              :step="1" @update:model-value="onLufsInput" @mouseup="onLufsRelease" @touchend="onLufsRelease" />
            <!-- Tick marks: quiet/balanced/loud/very-loud reference points -->
            <div class="relative h-3 mt-1">
              <div v-for="mark in LUFS_MARKS" :key="mark.value"
                class="absolute top-0 -translate-x-1/2 flex flex-col items-center"
                :style="{ left: `${lufsMarkPct(mark.value)}%` }">
                <span class="w-px h-1.5 bg-foreground/20" />
                <span class="text-[10px] text-foreground opacity-50 whitespace-nowrap mt-0.5">{{ t(mark.labelKey)
                  }}</span>
              </div>
            </div>
          </div>
          <span class="text-xs text-foreground opacity-60 tabular-nums">{{ NORMALIZATION_TARGET_LUFS_MAX }}</span>
        </div>
      </div>
      <SettingRow class="transition-opacity"
        :class="(!appStore.libraryAnalysisEnabled || !appStore.normalizationEnabled) && 'opacity-40 pointer-events-none'"
        :title="t('settings.normalization.prevent_clip')" :description="t('settings.normalization.prevent_clip_desc')">
        <Switch :model-value="appStore.normalizationPreventClip"
          @update:model-value="appStore.updateNormalizationPreventClip" />
      </SettingRow>
    </SettingSection>
  </div>
</template>
