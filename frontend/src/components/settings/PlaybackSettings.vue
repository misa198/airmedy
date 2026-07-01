<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { AudioLines, Wrench, Gauge, Volume2 } from 'lucide-vue-next'
import { Events } from '@wailsio/runtime'
import EQPanel from '@/components/EQPanel.vue'
import { Switch, Select, SelectTrigger, SelectValue, SelectContent, SelectItem, Slider } from '@airmedy/ui'
import { useAppStore, NORMALIZATION_TARGET_LUFS_MIN, NORMALIZATION_TARGET_LUFS_MAX } from '@/stores/app'

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

const analysisPercent = computed(() =>
  analysisTotal.value > 0 ? Math.round((analysisDone.value / analysisTotal.value) * 100) : 100
)

const handleAnalysisProgress = (ev: Events.WailsEvent) => {
  const data = ev.data as { done: number; total: number; state: 'analyzing' | 'paused' | 'done' }
  console.debug('[analysis:progress]', data)
  analysisDone.value = data.done
  analysisTotal.value = data.total
  analysisState.value = data.state
}

let offAnalysisProgress: (() => void) | null = null

onMounted(() => {
  offAnalysisProgress = Events.On('analysis:progress', handleAnalysisProgress)
})

onUnmounted(() => {
  offAnalysisProgress?.()
})
</script>

<template>
  <div class="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground opacity-60 select-none">
        <Wrench class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.playback.general') }}</h2>
      </div>
      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.playback.prevent_sleep') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.playback.prevent_sleep_desc') }}</p>
          </div>
          <Switch
            :model-value="appStore.preventSleepWhilePlaying"
            @update:model-value="appStore.updatePreventSleepWhilePlaying"
          />
        </div>
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.playback.show_player_indicator') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.playback.show_player_indicator_desc') }}</p>
          </div>
          <Switch
            :model-value="appStore.showPlayerIndicator"
            @update:model-value="appStore.updateShowPlayerIndicator"
          />
        </div>
      </div>
    </section>

    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground opacity-60 select-none">
        <AudioLines class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.equalizer.title') }}</h2>
      </div>
      <div class="bg-card rounded-2xl border border-foreground/[0.06] p-6">
        <EQPanel />
      </div>
    </section>

    <section>
      <div class="flex items-center justify-between gap-2 mb-6 select-none">
        <div class="flex items-center gap-2 text-foreground opacity-60">
          <Gauge class="w-4 h-4" />
          <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.library_analysis.title') }}</h2>
        </div>
        <p v-if="appStore.libraryAnalysisEnabled" class="text-xs text-foreground opacity-50">
          {{ t('settings.normalization.readiness', { percent: analysisPercent }) }}
        </p>
      </div>
      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.library_analysis.enable') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.library_analysis.enable_desc') }}</p>
          </div>
          <Switch
            :model-value="appStore.libraryAnalysisEnabled"
            @update:model-value="appStore.updateLibraryAnalysisEnabled"
          />
        </div>
        <div v-if="appStore.libraryAnalysisEnabled && analysisState !== 'done'" class="px-5 py-3 text-xs text-foreground opacity-60">
          {{ t('settings.normalization.analyzing', { done: analysisDone, total: analysisTotal, percent: analysisPercent }) }}
        </div>
      </div>
    </section>

    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground opacity-60 select-none">
        <Volume2 class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.normalization.title') }}</h2>
      </div>
      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <p v-if="!appStore.libraryAnalysisEnabled" class="px-5 py-3 text-xs text-foreground opacity-50">
          {{ t('settings.library_analysis.requires_enable') }}
        </p>
        <div
          class="p-5 flex items-center justify-between gap-x-2 transition-opacity"
          :class="!appStore.libraryAnalysisEnabled && 'opacity-40 pointer-events-none'"
        >
          <div>
            <p class="text-sm font-semibold">{{ t('settings.normalization.enable') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.normalization.enable_desc') }}</p>
          </div>
          <Switch
            :model-value="appStore.normalizationEnabled"
            @update:model-value="appStore.updateNormalizationEnabled"
          />
        </div>
        <div
          class="p-5 flex items-center justify-between gap-x-2 transition-opacity"
          :class="(!appStore.libraryAnalysisEnabled || !appStore.normalizationEnabled) && 'opacity-40 pointer-events-none'"
        >
          <div>
            <p class="text-sm font-semibold">{{ t('settings.normalization.mode') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.normalization.mode_track_desc') }}</p>
            <p class="text-xs text-foreground opacity-60">{{ t('settings.normalization.mode_album_desc') }}</p>
          </div>
          <Select
            :model-value="appStore.normalizationMode"
            @update:model-value="val => appStore.updateNormalizationMode(val as 'track' | 'album')"
          >
            <SelectTrigger class="w-[160px] bg-foreground/[0.04] border-0 h-9 text-sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="track">{{ t('settings.normalization.track_mode') }}</SelectItem>
              <SelectItem value="album">{{ t('settings.normalization.album_mode') }}</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div
          class="p-5 transition-opacity"
          :class="(!appStore.libraryAnalysisEnabled || !appStore.normalizationEnabled) && 'opacity-40 pointer-events-none'"
        >
          <div class="flex items-center justify-between gap-x-2 mb-3">
            <p class="text-sm font-semibold">{{ t('settings.normalization.target_lufs') }}</p>
            <p class="text-sm font-semibold tabular-nums">{{ lufsLive }} LUFS</p>
          </div>
          <div class="flex items-center gap-x-3">
            <span class="text-xs text-foreground opacity-60 tabular-nums">{{ NORMALIZATION_TARGET_LUFS_MIN }}</span>
            <div class="relative flex-1">
              <Slider
                :model-value="lufsLive"
                :min="NORMALIZATION_TARGET_LUFS_MIN"
                :max="NORMALIZATION_TARGET_LUFS_MAX"
                :step="1"
                @update:model-value="onLufsInput"
                @mouseup="onLufsRelease"
                @touchend="onLufsRelease"
              />
              <!-- Tick marks: quiet/balanced/loud/very-loud reference points -->
              <div class="relative h-3 mt-1">
                <div
                  v-for="mark in LUFS_MARKS"
                  :key="mark.value"
                  class="absolute top-0 -translate-x-1/2 flex flex-col items-center"
                  :style="{ left: `${lufsMarkPct(mark.value)}%` }"
                >
                  <span class="w-px h-1.5 bg-foreground/20" />
                  <span class="text-[10px] text-foreground opacity-50 whitespace-nowrap mt-0.5">{{ t(mark.labelKey) }}</span>
                </div>
              </div>
            </div>
            <span class="text-xs text-foreground opacity-60 tabular-nums">{{ NORMALIZATION_TARGET_LUFS_MAX }}</span>
          </div>
        </div>
        <div
          class="p-5 flex items-center justify-between gap-x-2 transition-opacity"
          :class="(!appStore.libraryAnalysisEnabled || !appStore.normalizationEnabled) && 'opacity-40 pointer-events-none'"
        >
          <div>
            <p class="text-sm font-semibold">{{ t('settings.normalization.prevent_clip') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.normalization.prevent_clip_desc') }}</p>
          </div>
          <Switch
            :model-value="appStore.normalizationPreventClip"
            @update:model-value="appStore.updateNormalizationPreventClip"
          />
        </div>
      </div>
    </section>
  </div>
</template>
