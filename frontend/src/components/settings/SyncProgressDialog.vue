<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Loader2, CheckCircle2 } from 'lucide-vue-next'
import type { SyncProgress } from '../../../bindings/airmedy/internal/domain/models'

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
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="open" class="fixed inset-0 z-[70] flex items-center justify-center">
        <div class="absolute inset-0 bg-background/60 backdrop-blur-sm" />
        <div class="relative z-10 w-100 rounded-xl bg-glass-elevated backdrop-blur-xl ring-1 ring-border-glass shadow-2xl p-6">
          <div class="flex flex-col items-center gap-4 text-center">
            <CheckCircle2 v-if="complete" class="w-10 h-10 text-primary" />
            <Loader2 v-else class="w-10 h-10 animate-spin text-primary" />
            <p class="font-bold text-base">{{ title() }}</p>

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
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-fade-enter-active, .modal-fade-leave-active { transition: opacity 0.15s; }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }
</style>
