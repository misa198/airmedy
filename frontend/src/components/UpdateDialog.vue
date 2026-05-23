<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { UpdateInfo } from '../../bindings/airmedy/internal/app/updater/models'
import { useAppStore } from '@/stores/app'
import { Sparkles } from 'lucide-vue-next'
import Modal from '@/components/ui/Modal.vue'

const { t } = useI18n()
const appStore = useAppStore()

const props = defineProps<{
  open: boolean
  updateInfo: UpdateInfo | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

async function handleUpdate() {
  try {
    await appStore.applyUpdate()
  } catch (err) {
    // Error handled in store
  }
}

function handleRestart() {
  appStore.restartApp()
}

function close() {
  if (appStore.isUpdating) return
  emit('update:open', false)
}
</script>

<template>
  <Modal :open="open" :title="t('app.update_available') + ' (v' + updateInfo?.version + ')'" width-class="w-[500px]" @close="close">
    <template #default>
      <div class="max-h-[80vh] flex flex-col">
        <div class="flex-1 overflow-y-auto min-h-0 my-4 text-sm text-foreground/80 leading-relaxed whitespace-pre-wrap custom-scrollbar">
          <template v-if="appStore.updateApplied">
            <div class="h-full flex flex-col items-center justify-center text-center py-8">
              <div class="w-16 h-16 bg-green-500/10 text-green-500 rounded-full flex items-center justify-center mb-4">
                <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>
              </div>
              <p class="text-base font-medium text-foreground mb-2">{{ t('settings.about.update_applied') }}</p>
            </div>
          </template>
          <template v-else>
            {{ updateInfo?.release_notes }}
          </template>
        </div>

        <div class="flex justify-end gap-3 pt-2">
          <button
            v-if="!appStore.updateApplied"
            @click="close"
            class="px-4 py-2 text-sm font-medium rounded-lg hover:bg-foreground/5 transition-colors disabled:opacity-50"
            :disabled="appStore.isUpdating"
          >
            {{ t('common.later') }}
          </button>
          <button
            v-if="!appStore.updateApplied"
            @click="handleUpdate()"
            class="px-4 py-2 text-sm font-medium rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center gap-2"
            :disabled="appStore.isUpdating"
          >
            <div v-if="appStore.isUpdating" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            {{ appStore.isUpdating ? t('app.updating') : t('app.update_now') }}
          </button>
          <template v-if="appStore.updateApplied">
            <button
              @click="close"
              class="px-4 py-2 text-sm font-medium rounded-lg hover:bg-foreground/5 transition-colors"
            >
              {{ t('common.later') }}
            </button>
            <button
              @click="handleRestart()"
              class="px-4 py-2 text-sm font-medium rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors flex items-center gap-2"
            >
              {{ t('app.restart_now') }}
            </button>
          </template>
        </div>
      </div>
    </template>
  </Modal>
</template>

<style scoped>
</style>
