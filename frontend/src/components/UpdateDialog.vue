<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { UpdateInfo } from '../../bindings/airmedy/internal/app/updater/models'
import { useAppStore } from '@/stores/app'
import { Modal } from '@airmedy/ui'

const { t } = useI18n()
const appStore = useAppStore()

const props = defineProps<{
  open: boolean
  updateInfo: UpdateInfo | null
}>()

const renderedNotes = computed(() =>
  DOMPurify.sanitize(
    marked.parse(props.updateInfo?.release_notes ?? '', { async: false }) as string
  )
)

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
        <div class="flex-1 overflow-y-auto min-h-0 my-4 text-sm text-foreground/80 leading-relaxed custom-scrollbar">
          <template v-if="appStore.updateApplied">
            <div class="h-full flex flex-col items-center justify-center text-center py-8">
              <div class="w-16 h-16 bg-green-500/10 text-green-500 rounded-full flex items-center justify-center mb-4">
                <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>
              </div>
              <p class="text-base font-medium text-foreground mb-2">{{ t('settings.about.update_applied') }}</p>
            </div>
          </template>
          <div v-else class="markdown-body" v-html="renderedNotes" />
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
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  color: var(--foreground, currentColor);
  font-weight: 600;
  line-height: 1.3;
  margin: 1em 0 0.5em;
}
.markdown-body :deep(h1):first-child,
.markdown-body :deep(h2):first-child,
.markdown-body :deep(h3):first-child,
.markdown-body :deep(h4):first-child {
  margin-top: 0;
}
.markdown-body :deep(h1) { font-size: 1.25rem; }
.markdown-body :deep(h2) { font-size: 1.125rem; }
.markdown-body :deep(h3) { font-size: 1rem; }
.markdown-body :deep(h4) { font-size: 0.9375rem; }

.markdown-body :deep(p) { margin: 0.5em 0; }
.markdown-body :deep(p):first-child { margin-top: 0; }

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}
.markdown-body :deep(ul) { list-style: disc; }
.markdown-body :deep(ol) { list-style: decimal; }
.markdown-body :deep(li) { margin: 0.25em 0; }

.markdown-body :deep(a) {
  color: var(--primary, #3b82f6);
  text-decoration: underline;
}

.markdown-body :deep(strong) {
  color: var(--foreground, currentColor);
  font-weight: 600;
}

.markdown-body :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85em;
  background: rgba(127, 127, 127, 0.15);
  padding: 0.15em 0.35em;
  border-radius: 0.25rem;
}
.markdown-body :deep(pre) {
  background: rgba(127, 127, 127, 0.12);
  padding: 0.75em 1em;
  border-radius: 0.5rem;
  overflow-x: auto;
  margin: 0.75em 0;
}
.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid rgba(127, 127, 127, 0.4);
  padding-left: 0.75em;
  margin: 0.5em 0;
  opacity: 0.85;
}

.markdown-body :deep(hr) {
  border: 0;
  border-top: 1px solid rgba(127, 127, 127, 0.25);
  margin: 1em 0;
}
</style>
