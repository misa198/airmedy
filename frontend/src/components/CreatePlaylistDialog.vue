<script setup lang="ts">
import { ref, watch } from 'vue'
import { Input } from '@/components/ui/input'
import Modal from '@/components/ui/Modal.vue'
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

watch(() => props.open, (val) => {
  if (val) name.value = props.initialName ?? ''
})

function submit() {
  if (!name.value.trim()) return
  emit('confirm', name.value.trim())
  emit('update:open', false)
}
</script>

<template>
  <Modal :open="open" :title="title ?? t('sidebar.new_playlist')" @close="emit('update:open', false)">
    <Input
      v-model="name"
      :placeholder="t('sidebar.playlist_name')"
      class="bg-foreground/[0.05] border-foreground/[0.08] text-foreground placeholder:text-foreground opacity-50 focus-visible:ring-foreground/20"
      autofocus
      @keydown.enter="submit" />
    <div class="flex justify-end gap-2 mt-4">
      <button
        class="px-3 py-1.5 text-sm text-foreground opacity-70 hover:text-foreground rounded-lg hover:bg-foreground/[0.05] transition-colors"
        @click="emit('update:open', false)">{{ t('common.cancel') }}</button>
      <button
        class="px-3 py-1.5 text-sm text-foreground bg-foreground/[0.12] hover:bg-foreground/[0.18] rounded-lg transition-colors font-medium disabled:opacity-40"
        :disabled="!name.trim()"
        @click="submit">{{ title === t('sidebar.rename_playlist_title') ? t('sidebar.rename') : t('common.create') }}</button>
    </div>
  </Modal>
</template>
