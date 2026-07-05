<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { X } from '@lucide/vue'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue, Input } from '@airmedy/ui'
import {
  SMART_PLAYLIST_FIELDS,
  SMART_RULE_OPERATOR_LABEL_KEYS,
  getFieldSpec,
  defaultRuleForField,
  type SmartRule,
} from '../lib/smartPlaylistFields'

const { t } = useI18n()

const props = defineProps<{
  modelValue: SmartRule
  error?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: SmartRule]
  remove: []
}>()

const spec = computed(() => getFieldSpec(props.modelValue.field))

function onFieldChange(fieldId: string) {
  const nextSpec = getFieldSpec(fieldId)
  if (!nextSpec) return
  emit('update:modelValue', defaultRuleForField(nextSpec))
}

function onOpChange(op: string) {
  const s = spec.value
  if (!s) return
  let value = props.modelValue.value
  // Switching to/from "between" changes the value shape (scalar vs [min, max]).
  if (op === 'between' && !Array.isArray(value)) value = [0, 0]
  if (op !== 'between' && Array.isArray(value)) value = value[0] ?? 0
  emit('update:modelValue', { ...props.modelValue, op, value })
}

function onValueChange(value: unknown) {
  emit('update:modelValue', { ...props.modelValue, value })
}

function onRangeChange(index: 0 | 1, raw: string) {
  const current = Array.isArray(props.modelValue.value) ? [...props.modelValue.value] : [0, 0]
  current[index] = Number(raw)
  onValueChange(current)
}
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <div class="flex items-center gap-2">
      <Select :model-value="modelValue.field" @update:model-value="val => onFieldChange(val as string)">
        <SelectTrigger class="w-36 h-9 bg-foreground/[0.07] border-foreground/20 text-sm shrink-0">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="f in SMART_PLAYLIST_FIELDS" :key="f.id" :value="f.id">
            {{ t(f.labelKey) }}
          </SelectItem>
        </SelectContent>
      </Select>

      <Select v-if="spec" :model-value="modelValue.op" @update:model-value="val => onOpChange(val as string)">
        <SelectTrigger class="w-32 h-9 bg-foreground/[0.07] border-foreground/20 text-sm shrink-0">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="op in spec.operators" :key="op" :value="op">
            {{ t(SMART_RULE_OPERATOR_LABEL_KEYS[op]) }}
          </SelectItem>
        </SelectContent>
      </Select>

      <template v-if="spec?.type === 'string'">
        <Input
          :model-value="modelValue.value as string"
          @update:model-value="val => onValueChange(val)"
          class="flex-1 h-9 bg-foreground/[0.07] border-foreground/20 text-sm"
        />
      </template>

      <template v-else-if="spec?.type === 'number' && modelValue.op === 'between'">
        <Input
          type="number"
          :model-value="String((modelValue.value as number[])?.[0] ?? 0)"
          @update:model-value="val => onRangeChange(0, val as string)"
          class="w-20 h-9 bg-foreground/[0.07] border-foreground/20 text-sm"
        />
        <span class="text-xs text-foreground/50">{{ t('playlists.smart.range_and') }}</span>
        <Input
          type="number"
          :model-value="String((modelValue.value as number[])?.[1] ?? 0)"
          @update:model-value="val => onRangeChange(1, val as string)"
          class="w-20 h-9 bg-foreground/[0.07] border-foreground/20 text-sm"
        />
      </template>

      <template v-else-if="spec?.type === 'number'">
        <Input
          type="number"
          :model-value="String(modelValue.value ?? 0)"
          @update:model-value="val => onValueChange(Number(val))"
          class="w-24 h-9 bg-foreground/[0.07] border-foreground/20 text-sm"
        />
      </template>

      <template v-else-if="spec?.type === 'boolean'">
        <Select :model-value="String(modelValue.value)" @update:model-value="val => onValueChange(val === 'true')">
          <SelectTrigger class="w-28 h-9 bg-foreground/[0.07] border-foreground/20 text-sm">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="true">{{ t('common.yes') }}</SelectItem>
            <SelectItem value="false">{{ t('common.no') }}</SelectItem>
          </SelectContent>
        </Select>
      </template>

      <button
        type="button"
        @click="emit('remove')"
        class="p-1.5 text-foreground/50 hover:text-red-500 hover:bg-red-500/10 rounded-md transition-colors shrink-0"
        :title="t('common.delete')"
      >
        <X class="w-3.5 h-3.5" />
      </button>
    </div>
    <p v-if="error" class="text-xs text-red-500 font-medium">{{ error }}</p>
  </div>
</template>
