<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Checkbox, Input, Modal, RangeSlider, Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@airmedy/ui'
import RuleBuilder from './RuleBuilder.vue'
import MoodHeatmap from './MoodHeatmap.vue'
import { useAppStore } from '../stores/app'
import {
  countRules,
  emptyConfig,
  emptyGroup,
  isGroupValid,
  normalizeConfigForEditor,
  SMART_LIMIT_BY_OPTIONS,
  type SmartPlaylistConfig,
} from '../lib/smartPlaylistFields'
import { boxFromMoodConfig, defaultMoodBox, isMoodBoxValid, moodConfigFromBox, type MoodBox } from '../lib/moodPlaylistFields'
import { GetMoodDensityGrid } from '../../bindings/airmedy/internal/infra/wails/moodradioservice'
import type { MoodDensityGrid } from '../../bindings/airmedy/internal/domain/models'

const appStore = useAppStore()

const { t } = useI18n()

const props = defineProps<{
  open: boolean
  initialName?: string
  initialDescription?: string
  initialConfig?: SmartPlaylistConfig
  title?: string
  confirmLabel?: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  confirm: [payload: { name: string; description: string; config: SmartPlaylistConfig }]
}>()

// A stored config's rules feed whichever tab actually produced them, never
// both: energy/danceability rules aren't in SMART_PLAYLIST_FIELDS (Mood tab
// builds them directly, bypassing that picker), so handing a mood-shaped
// config's rules to the Filters tab's RuleBuilder would show rows for
// fields it can't resolve. Limit/live-updating are shared UI regardless of
// tab, though, so those always carry over from the stored config as-is —
// only the rule tree itself is swapped for an empty one when routing to Mood.
function configForTabs(initialConfig: SmartPlaylistConfig | undefined) {
  const moodBox = boxFromMoodConfig(initialConfig)
  if (moodBox && initialConfig) {
    return { tab: 'mood' as const, config: { ...initialConfig, root: emptyGroup() }, moodBox }
  }
  return {
    tab: 'filters' as const,
    config: initialConfig ? normalizeConfigForEditor(initialConfig) : emptyConfig(),
    moodBox: defaultMoodBox(),
  }
}

const initialRoute = configForTabs(props.initialConfig)
const activeTab = ref<'filters' | 'mood'>(initialRoute.tab)
const name = ref(props.initialName ?? '')
const description = ref(props.initialDescription ?? '')
const config = ref<SmartPlaylistConfig>(initialRoute.config)

const showErrors = ref(false)

const moodBox = ref<MoodBox>(initialRoute.moodBox)
const moodGrid = ref<MoodDensityGrid | null>(null)
const moodGridLoading = ref(false)
let moodGridFetched = false

// Called both right after opening (if landing directly on the mood tab) and
// on every later switch to it. `watch(activeTab, ...)` alone isn't enough:
// it only fires on a value *change*, so reopening straight onto 'mood' when
// the dialog was already left on 'mood' (this component stays mounted
// across opens, per usePlaylistContextMenu's pattern) sets the same value
// and never fires — the heatmap would then silently keep stale/no data.
async function ensureMoodGridLoaded() {
  if (moodGridFetched || !appStore.libraryAnalysisEnabled) return
  moodGridFetched = true
  moodGridLoading.value = true
  try {
    moodGrid.value = await GetMoodDensityGrid(32)
  } finally {
    moodGridLoading.value = false
  }
}

watch(() => props.open, (val) => {
  if (!val) return
  const route = configForTabs(props.initialConfig)
  activeTab.value = route.tab
  name.value = props.initialName ?? ''
  description.value = props.initialDescription ?? ''
  config.value = route.config
  moodBox.value = route.moodBox
  moodGridFetched = false
  showErrors.value = false
  if (route.tab === 'mood') ensureMoodGridLoaded()
})

watch(activeTab, (tab) => {
  if (tab === 'mood') ensureMoodGridLoaded()
})

function toggleLimitEnabled(enabled: boolean) {
  config.value = { ...config.value, limit: { ...config.value.limit, enabled } }
}

function setLimitCount(raw: string) {
  const count = Math.max(1, Number(raw) || 1)
  config.value = { ...config.value, limit: { ...config.value.limit, count } }
}

function setLimitBy(by: string) {
  config.value = { ...config.value, limit: { ...config.value.limit, by: by as SmartPlaylistConfig['limit']['by'] } }
}

function toggleLiveUpdating(live: boolean) {
  config.value = { ...config.value, live_updating: live }
}

const ruleCount = computed(() => countRules(config.value.root))
const hasRules = computed(() => ruleCount.value > 0)
const rulesValid = computed(() => isGroupValid(config.value.root))
const brightnessRange = computed<[number, number]>({
  get: () => [moodBox.value.brightnessMin, moodBox.value.brightnessMax] as [number, number],
  set: ([brightnessMin, brightnessMax]) => {
    moodBox.value = { ...moodBox.value, brightnessMin, brightnessMax }
  },
})
const canSubmit = computed(() => {
  if (!name.value.trim()) return false
  if (activeTab.value === 'mood') return appStore.libraryAnalysisEnabled && isMoodBoxValid(moodBox.value)
  return hasRules.value && rulesValid.value
})

function submit() {
  if (!canSubmit.value) {
    showErrors.value = true
    return
  }
  emit('confirm', {
    name: name.value.trim(),
    description: description.value,
    config: activeTab.value === 'mood'
      ? moodConfigFromBox(moodBox.value, config.value.limit, config.value.live_updating)
      : config.value,
  })
  emit('update:open', false)
}
</script>

<template>
  <Modal :open="open" :title="title ?? t('playlists.smart.new_smart_playlist')" width-class="w-[48rem]" @close="emit('update:open', false)">
    <div class="space-y-4">
      <Input
        v-model="name"
        :placeholder="t('sidebar.playlist_name')"
        class="bg-foreground/[0.05] border-foreground/10 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
        autofocus
      />

      <Select :model-value="activeTab" @update:model-value="val => activeTab = val as 'filters' | 'mood'">
        <SelectTrigger class="w-40 h-9 text-sm">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="filters">{{ t('playlists.smart.tab_filters') }}</SelectItem>
          <SelectItem value="mood">{{ t('playlists.smart.tab_mood') }}</SelectItem>
        </SelectContent>
      </Select>

      <template v-if="activeTab === 'filters'">
        <RuleBuilder
          :group="config.root"
          :show-all-errors="showErrors"
          @update:group="val => config = { ...config, root: val }"
        />
        <p v-if="showErrors && !hasRules" class="text-xs text-red-500 -mt-2">
          {{ t('playlists.smart.no_rules_error') }}
        </p>
      </template>

      <div v-else-if="!appStore.libraryAnalysisEnabled" class="py-10 text-center text-sm text-foreground/40">
        {{ t('playlists.smart.mood_requires_analysis') }}
      </div>

      <div v-else-if="moodGridLoading" class="py-10 text-center text-sm text-foreground/40">
        {{ t('common.loading') }}
      </div>

      <template v-else>
        <MoodHeatmap v-model="moodBox" :grid="moodGrid" />
        <div class="rounded-xl bg-glass-elevated backdrop-blur-xl border border-border-glass px-4 py-3">
          <div class="mb-2 flex items-center justify-between text-sm">
            <span class="text-foreground/70">{{ t('playlists.smart.mood_brightness') }}</span>
            <span class="text-foreground/40 tabular-nums">{{ brightnessRange[0].toFixed(2) }}–{{ brightnessRange[1].toFixed(2) }}</span>
          </div>
          <RangeSlider v-model="brightnessRange" :min="0" :max="1" :step="0.01" />
          <div class="mt-1 flex justify-between text-xs text-foreground/40">
            <span>{{ t('playlists.smart.mood_brightness_dark') }}</span>
            <span>{{ t('playlists.smart.mood_brightness_bright') }}</span>
          </div>
        </div>
      </template>

      <div class="border-t border-foreground/10 pt-3 space-y-3">
        <label class="flex items-center gap-2 text-sm text-foreground/80 cursor-pointer" @click="toggleLimitEnabled(!config.limit.enabled)">
          <Checkbox :checked="config.limit.enabled" />
          {{ t('playlists.smart.limit_label') }}
        </label>

        <div class="flex items-center gap-2 pl-6 flex-wrap" :class="!config.limit.enabled && 'opacity-40'">
          <Input
            type="number"
            min="1"
            :disabled="!config.limit.enabled"
            :model-value="String(config.limit.count)"
            @update:model-value="val => setLimitCount(val as string)"
            class="w-20 h-9 bg-foreground/[0.07] border-foreground/20 text-sm"
          />
          <span class="text-sm text-foreground/60">{{ t('playlists.smart.limit_selected_by') }}</span>
          <Select :model-value="config.limit.by" :disabled="!config.limit.enabled" @update:model-value="val => setLimitBy(val as string)">
            <SelectTrigger class="w-40 h-9 bg-foreground/[0.07] border-foreground/20 text-sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem v-for="opt in SMART_LIMIT_BY_OPTIONS" :key="opt.value" :value="opt.value">
                {{ t(opt.labelKey) }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <label class="flex items-center gap-2 text-sm text-foreground/80 cursor-pointer" @click="toggleLiveUpdating(!config.live_updating)">
          <Checkbox :checked="config.live_updating" />
          {{ t('playlists.smart.live_updating_label') }}
        </label>
        <p class="text-xs text-foreground/40 pl-6 -mt-2">{{ t('playlists.smart.live_updating_hint') }}</p>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button
          class="px-3 py-1.5 text-sm text-foreground opacity-70 hover:text-foreground rounded-lg hover:bg-foreground/[0.05] transition-colors"
          @click="emit('update:open', false)">{{ t('common.cancel') }}</button>
        <button
          class="px-3 py-1.5 text-sm bg-primary text-primary-foreground rounded-lg transition-colors font-medium"
          :class="!canSubmit && 'opacity-40 pointer-events-none'"
          @click="submit">{{ confirmLabel ?? t('common.create') }}</button>
      </div>
    </template>
  </Modal>
</template>
