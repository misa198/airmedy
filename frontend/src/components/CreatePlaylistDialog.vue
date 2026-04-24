<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { Input } from '@/components/ui/input'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const props = defineProps<{
  open: boolean
  initialName?: string
  title?: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  confirm: [name: string]
}>()
const name = ref(props.initialName ?? '')
const inputRef = ref<any>(null)

watch(() => props.open, (val) => {
  if (val) {
    name.value = props.initialName ?? ''
  }
})

function focusInput() {
  inputRef.value?.$el?.focus()
}

function submit() {
  if (!name.value.trim()) return
  emit('confirm', name.value.trim())
  emit('update:open', false)
}

function cancel() {
  emit('update:open', false)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade" @after-enter="focusInput">
      <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center" @click.self="cancel">
        <div class="absolute inset-0 bg-background/60 backdrop-blur-sm" @click="cancel" />
        <div class="relative z-10 w-80 rounded-xl bg-[#1A1A1A] ring-1 ring-foreground/[0.08] shadow-2xl p-5"
          @keydown.esc="cancel" @keydown.enter="submit">
          <h3 class="text-sm font-semibold text-foreground mb-4">{{ title ?? t('sidebar.new_playlist') }}</h3>
          <Input ref="inputRef" v-model="name" :placeholder="t('sidebar.playlist_name')"
            class="bg-foreground/[0.05] border-foreground/[0.08] text-foreground placeholder:text-foreground/30 focus-visible:ring-foreground/20"
            autofocus />
          <div class="flex justify-end gap-2 mt-4">
            <button
              class="px-3 py-1.5 text-sm text-foreground/50 hover:text-foreground rounded-lg hover:bg-foreground/[0.05] transition-colors"
              @click="cancel">{{ t('common.cancel') }}</button>
            <button
              class="px-3 py-1.5 text-sm text-foreground bg-foreground/[0.12] hover:bg-foreground/[0.18] rounded-lg transition-colors font-medium disabled:opacity-40"
              :disabled="!name.trim()" @click="submit">{{ title === t('sidebar.rename_playlist_title') ? t('sidebar.rename') : t('common.create') }}</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s linear;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.fade-enter-active .relative {
  transition: transform 0.25s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.fade-enter-from .relative {
  transform: scale(0.96);
}
</style>
