<script setup lang="ts">
import Modal from './ui/Modal.vue'
import { useI18n } from 'vue-i18n'

defineProps<{
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  danger?: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'confirm': []
  'cancel': []
}>()

const { t } = useI18n()

function handleCancel() {
  emit('update:open', false)
  emit('cancel')
}

function handleConfirm() {
  emit('update:open', false)
  emit('confirm')
}
</script>

<template>
  <Modal :open="open" :title="title" @close="handleCancel">
    <div class="space-y-4">
      <p class="text-sm text-foreground/70 leading-relaxed">
        {{ message }}
      </p>
      <div class="flex gap-2 justify-end pt-2">
        <button 
          @click="handleCancel"
          class="px-3 py-1.5 text-sm font-medium rounded-lg hover:bg-foreground/5 transition-colors"
        >
          {{ cancelLabel ?? t('common.cancel') }}
        </button>
        <button 
          @click="handleConfirm"
          :class="[
            'px-3 py-1.5 text-sm font-medium rounded-lg transition-colors',
            danger ? 'bg-red-500 text-white hover:bg-red-600' : 'bg-primary text-white hover:bg-primary/90'
          ]"
        >
          {{ confirmLabel ?? t('common.confirm') }}
        </button>
      </div>
    </div>
  </Modal>
</template>
