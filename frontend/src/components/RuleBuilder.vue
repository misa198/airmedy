<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { FolderPlus } from '@lucide/vue'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@airmedy/ui'
import RuleGroupBox from './RuleGroupBox.vue'
import { countRules, emptyGroup, MAX_RULES, type SmartRuleGroup } from '../lib/smartPlaylistFields'

const { t } = useI18n()

const props = defineProps<{
  group: SmartRuleGroup
  showAllErrors?: boolean
}>()

const totalRules = computed(() => countRules(props.group))
const atMaxRules = computed(() => totalRules.value >= MAX_RULES)

const emit = defineEmits<{
  'update:group': [value: SmartRuleGroup]
}>()

function updateMatch(match: 'all' | 'any') {
  emit('update:group', { ...props.group, match })
}

function addGroup() {
  emit('update:group', { ...props.group, groups: [...props.group.groups, emptyGroup('all')] })
}

function updateGroup(index: number, sub: SmartRuleGroup) {
  const groups = [...props.group.groups]
  groups[index] = sub
  emit('update:group', { ...props.group, groups })
}

function removeGroup(index: number) {
  emit('update:group', { ...props.group, groups: props.group.groups.filter((_, i) => i !== index) })
}
</script>

<template>
  <div class="space-y-3">
    <div v-if="group.groups.length > 1" class="flex items-center justify-between">
      <span class="text-xs font-semibold tracking-wide text-foreground/70">{{ t('playlists.smart.match_groups_label') }}</span>
      <Select :model-value="group.match" @update:model-value="val => updateMatch(val as 'all' | 'any')">
        <SelectTrigger class="w-28 h-9 bg-foreground/[0.07] border-foreground/20 text-sm">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">{{ t('playlists.smart.match_all') }}</SelectItem>
          <SelectItem value="any">{{ t('playlists.smart.match_any') }}</SelectItem>
        </SelectContent>
      </Select>
    </div>

    <template v-for="(sub, index) in group.groups" :key="index">
      <div v-if="index > 0" class="flex items-center gap-2">
        <div class="flex-1 h-px bg-foreground/10" />
        <span class="text-[10px] font-semibold tracking-widest text-foreground/40 uppercase">
          {{ group.match === 'any' ? t('playlists.smart.match_any') : t('playlists.smart.match_all') }}
        </span>
        <div class="flex-1 h-px bg-foreground/10" />
      </div>
      <RuleGroupBox
        :group="sub"
        :disable-add-rule="atMaxRules"
        :show-all-errors="showAllErrors"
        @update:group="val => updateGroup(index, val)"
        @remove="removeGroup(index)"
      />
    </template>

    <p v-if="group.groups.length === 0" class="text-xs text-foreground/40">
      {{ t('playlists.smart.no_rules') }}
    </p>

    <div class="flex items-center justify-between">
      <button
        type="button"
        @click="addGroup"
        class="flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium text-foreground/70 hover:text-foreground hover:bg-foreground/[0.05] rounded-lg transition-colors"
      >
        <FolderPlus class="w-3.5 h-3.5" />
        {{ t('playlists.smart.add_group') }}
      </button>
      <span class="text-xs" :class="atMaxRules ? 'text-red-500' : 'text-foreground/40'">
        {{ t('playlists.smart.rule_count', { count: totalRules, max: MAX_RULES }) }}
      </span>
    </div>
  </div>
</template>
