<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Checkbox, Input, Modal, Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@airmedy/ui'
import RuleBuilder from './RuleBuilder.vue'
import { useAppStore } from '../stores/app'
import {
  countRules,
  emptyConfig,
  isGroupValid,
  normalizeConfigForEditor,
  SMART_LIMIT_BY_OPTIONS,
  type SmartPlaylistConfig,
} from '../lib/smartPlaylistFields'

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

const activeTab = ref<'filters' | 'mood'>('filters')
const name = ref(props.initialName ?? '')
const description = ref(props.initialDescription ?? '')
const config = ref<SmartPlaylistConfig>(props.initialConfig ? normalizeConfigForEditor(props.initialConfig) : emptyConfig())

const showErrors = ref(false)

watch(() => props.open, (val) => {
  if (!val) return
  activeTab.value = 'filters'
  name.value = props.initialName ?? ''
  description.value = props.initialDescription ?? ''
  config.value = props.initialConfig ? normalizeConfigForEditor(props.initialConfig) : emptyConfig()
  showErrors.value = false
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
const canSubmit = computed(() => name.value.trim().length > 0 && hasRules.value && rulesValid.value)

function submit() {
  if (!canSubmit.value) {
    showErrors.value = true
    return
  }
  emit('confirm', {
    name: name.value.trim(),
    description: description.value,
    config: config.value,
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

      <!-- Reserved for the future mood quadrant picker (valence x energy pad).
           Not implemented yet: mood-derived track features are still pending
           analysis, see catalog/playlists docs. -->
      <div v-else class="py-10 text-center text-sm text-foreground/40">
        {{ t('playlists.smart.mood_coming_soon') }}
      </div>

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
          class="px-3 py-1.5 text-sm bg-primary text-white rounded-lg transition-colors font-medium"
          :class="!name.trim() && 'opacity-40 pointer-events-none'"
          @click="submit">{{ confirmLabel ?? t('common.create') }}</button>
      </div>
    </template>
  </Modal>
</template>
