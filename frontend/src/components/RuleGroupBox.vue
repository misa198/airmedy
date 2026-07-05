<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Plus, X } from '@lucide/vue'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@airmedy/ui'
import RuleRow from './RuleRow.vue'
import { SMART_PLAYLIST_FIELDS, defaultRuleForField, isRuleValid, type SmartRule, type SmartRuleGroup } from '../lib/smartPlaylistFields'

const { t } = useI18n()

const props = defineProps<{
  group: SmartRuleGroup
  disableAddRule?: boolean
  // Reveal every invalid rule's error, not just ones the user has edited —
  // set once the user tries to submit the dialog.
  showAllErrors?: boolean
}>()

const emit = defineEmits<{
  'update:group': [value: SmartRuleGroup]
  remove: []
}>()

// A freshly-added rule starts with a placeholder value (e.g. an empty genre
// string) that's technically invalid but shouldn't show as an error until
// the user actually touches it. Rules are replaced (not mutated) on every
// edit, so marking the emitted object itself as touched — rather than
// tracking by index, which shifts on add/remove — survives reordering.
const touchedRules = new WeakSet<SmartRule>()

function updateMatch(match: 'all' | 'any') {
  emit('update:group', { ...props.group, match })
}

function addRule() {
  emit('update:group', { ...props.group, rules: [...props.group.rules, defaultRuleForField(SMART_PLAYLIST_FIELDS[0])] })
}

function updateRule(index: number, rule: SmartRule) {
  touchedRules.add(rule)
  const rules = [...props.group.rules]
  rules[index] = rule
  emit('update:group', { ...props.group, rules })
}

function ruleError(rule: SmartRule): string | undefined {
  if (isRuleValid(rule)) return undefined
  if (!touchedRules.has(rule) && !props.showAllErrors) return undefined
  return t('playlists.smart.rule_invalid')
}

function removeRule(index: number) {
  emit('update:group', { ...props.group, rules: props.group.rules.filter((_, i) => i !== index) })
}
</script>

<template>
  <div class="rounded-xl border border-foreground/15 bg-foreground/[0.02] p-3 space-y-2.5">
    <div class="flex items-center justify-between">
      <span class="text-xs font-semibold tracking-wide text-foreground/70">{{ t('playlists.smart.match_label') }}</span>
      <div class="flex items-center gap-1.5">
        <Select :model-value="group.match" @update:model-value="val => updateMatch(val as 'all' | 'any')">
          <SelectTrigger class="w-28 h-9 bg-foreground/[0.07] border-foreground/20 text-sm">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{{ t('playlists.smart.match_all') }}</SelectItem>
            <SelectItem value="any">{{ t('playlists.smart.match_any') }}</SelectItem>
          </SelectContent>
        </Select>
        <button
          type="button"
          @click="emit('remove')"
          class="p-1.5 text-foreground/50 hover:text-red-500 hover:bg-red-500/10 rounded-md transition-colors shrink-0"
          :title="t('playlists.smart.remove_group')"
        >
          <X class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>

    <div class="space-y-2">
      <RuleRow
        v-for="(rule, index) in group.rules"
        :key="index"
        :model-value="rule"
        :error="ruleError(rule)"
        @update:model-value="val => updateRule(index, val)"
        @remove="removeRule(index)"
      />
    </div>

    <p v-if="group.rules.length === 0" class="text-xs text-foreground/40">
      {{ t('playlists.smart.no_rules') }}
    </p>

    <button
      type="button"
      :disabled="disableAddRule"
      @click="addRule"
      class="flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium text-foreground/70 hover:text-foreground hover:bg-foreground/[0.05] rounded-lg transition-colors disabled:opacity-40 disabled:pointer-events-none"
    >
      <Plus class="w-3.5 h-3.5" />
      {{ t('playlists.smart.add_rule') }}
    </button>
  </div>
</template>
