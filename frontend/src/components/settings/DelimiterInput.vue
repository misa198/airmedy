<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { X, Plus } from '@lucide/vue'

const { t } = useI18n()

type ChipColor = 'neutral' | 'primary' | 'success' | 'warning' | 'danger'

const props = withDefaults(defineProps<{
  modelValue: string[]
  label: string
  disabled?: boolean
  color?: ChipColor
}>(), {
  color: 'neutral',
})

const chipClasses: Record<ChipColor, { chip: string; remove: string }> = {
  neutral: { chip: 'bg-foreground/[0.06] text-foreground', remove: 'hover:bg-foreground/[0.12]' },
  primary: { chip: 'bg-primary/10 text-primary', remove: 'hover:bg-primary/20' },
  success: { chip: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400', remove: 'hover:bg-emerald-500/20' },
  warning: { chip: 'bg-amber-500/10 text-amber-600 dark:text-amber-400', remove: 'hover:bg-amber-500/20' },
  danger: { chip: 'bg-red-500/10 text-red-600 dark:text-red-400', remove: 'hover:bg-red-500/20' },
}

const chipColor = computed(() => chipClasses[props.color])

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void
}>()

const MAX_LEN = 5

const draft = ref('')
const error = ref('')

const chips = computed(() => props.modelValue)

const validate = (value: string): string => {
  const trimmed = value.trim()
  if (!trimmed) return t('settings.delimiters.error_empty')
  if (Array.from(trimmed).length > MAX_LEN) return t('settings.delimiters.error_too_long', { max: MAX_LEN })
  if (props.modelValue.includes(trimmed)) return t('settings.delimiters.error_duplicate')
  return ''
}

const addChip = () => {
  if (props.disabled) return
  const trimmed = draft.value.trim()
  const err = validate(trimmed)
  if (err) {
    error.value = err
    return
  }
  emit('update:modelValue', [...props.modelValue, trimmed])
  draft.value = ''
  error.value = ''
}

const removeChip = (index: number) => {
  if (props.disabled) return
  // Empty list is allowed: it disables splitting for this field.
  const next = props.modelValue.filter((_, i) => i !== index)
  emit('update:modelValue', next)
  error.value = ''
}

const onBackspace = () => {
  // Empty input + backspace removes the last chip.
  if (draft.value === '' && props.modelValue.length > 0) {
    removeChip(props.modelValue.length - 1)
  }
}

const onInput = () => {
  if (error.value) error.value = ''
}
</script>

<template>
  <div class="space-y-2">
    <label class="text-xs font-semibold tracking-wide text-foreground/70">{{ label }}</label>
    <div
      class="flex flex-wrap items-center gap-1.5 p-1.5 bg-foreground/[0.02] border border-foreground/[0.06] rounded-lg"
      :class="{ 'opacity-50 pointer-events-none': disabled }"
    >
      <span
        v-for="(chip, index) in chips"
        :key="`${chip}-${index}`"
        class="flex items-center gap-1 pl-2 pr-1 py-0.5 rounded-md text-xs font-medium font-mono tracking-tight"
        :class="chipColor.chip"
      >
        <span>{{ chip }}</span>
        <button
          type="button"
          @click="removeChip(index)"
          class="p-0.5 rounded transition-colors"
          :class="chipColor.remove"
          :title="t('settings.delimiters.remove')"
        >
          <X class="w-2.5 h-2.5" />
        </button>
      </span>
      <div class="flex items-center gap-1 flex-1 min-w-[100px]">
        <input
          v-model="draft"
          type="text"
          :maxlength="MAX_LEN"
          :placeholder="t('settings.delimiters.add_placeholder')"
          @keydown.enter.prevent="addChip"
          @keydown.backspace="onBackspace"
          @input="onInput"
          class="flex-1 min-w-0 bg-transparent text-xs font-mono px-1 py-0.5 outline-none placeholder:text-foreground/40 placeholder:font-sans"
        />
        <button
          type="button"
          @click="addChip"
          :disabled="!draft.trim()"
          class="p-0.5 text-foreground/60 hover:text-foreground hover:bg-foreground/[0.06] rounded-md transition-all disabled:opacity-40"
          :title="t('settings.delimiters.add')"
        >
          <Plus class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
    <p v-if="error" class="text-xs text-red-500 font-medium">{{ error }}</p>
  </div>
</template>
