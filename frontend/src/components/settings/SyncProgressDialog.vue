<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Loader2, CheckCircle2 } from 'lucide-vue-next'
import type { SyncProgress } from '../../../bindings/airmedy/internal/domain/models'
import Modal from '@/components/ui/Modal.vue'

const props = defineProps<{
  open: boolean
  type: 'sync' | 'optimize' | 'deleting'
  progress: SyncProgress | null
  complete: boolean
}>()

const { t } = useI18n()

const title = () => {
  if (props.complete) return t('settings.sync.sync_complete')
  if (props.type === 'deleting') return t('settings.sync.removing_folder')
  if (props.type === 'optimize') return t('settings.sync.optimizing_search')
  return t('settings.sync.syncing_library')
}

const progressPercent = () => {
  if (!props.progress) return 0
  return Math.round((props.progress.current / (props.progress.total || 1)) * 100)
}
</script>

<template>
  <Modal :open="open" :title="title()" width-class="w-100" @close="() => {}">
    <template #default>
      <div class="flex flex-col items-center gap-4">
        <CheckCircle2 v-if="complete" class="w-10 h-10 text-primary" />
        <Loader2 v-else class="w-10 h-10 animate-spin text-primary" />
        
        <template v-if="!complete && type !== 'deleting' && progress">
          <div class="w-full">
            <div class="flex justify-between text-xs text-foreground/60 mb-1.5 font-medium">
              <span v-if="type === 'optimize'">{{ progressPercent() }}%</span>
              <span v-else>{{ progress.current }} / {{ progress.total }}</span>
            </div>
            <div class="w-full bg-foreground/[0.06] rounded-full h-2 overflow-hidden">
              <div class="bg-primary h-full transition-all duration-300 ease-out"
                :style="{ width: `${type === 'optimize' ? progressPercent() : (progress.current / (progress.total || 1)) * 100}%` }" />
            </div>
            <p v-if="progress.path" class="text-[10px] text-foreground/50 truncate mt-2 font-medium">
              {{ progress.path }}
            </p>
          </div>
        </template>
      </div>
    </template>
  </Modal>
</template>

<style scoped>
</style>
