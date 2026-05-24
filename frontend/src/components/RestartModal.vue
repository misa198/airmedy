<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import ConfirmDialog from './ConfirmDialog.vue'

defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const { t } = useI18n()
const appStore = useAppStore()

const handleRestart = async () => {
  await appStore.restartApp()
}
</script>

<template>
  <ConfirmDialog
    :open="open"
    @update:open="emit('update:open', $event)"
    :title="t('settings.behavior.restart_required', 'Restart Required')"
    :message="t('settings.behavior.restart_required_desc', 'A restart is required to apply changes. Restart now?')"
    :confirm-label="t('settings.behavior.restart_now', 'Restart Now')"
    :cancel-label="t('settings.behavior.restart_later', 'Later')"
    @confirm="handleRestart"
  />
</template>
