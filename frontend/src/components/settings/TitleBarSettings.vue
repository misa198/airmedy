<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppWindow, GripVertical } from '@lucide/vue'
import { Checkbox, Switch } from '@airmedy/ui'
import VirtualList from 'vue-virtual-sortable'
import { useAppStore } from '@/stores/app'
import { TITLE_BAR_ACTIONS, type TitleBarAction } from '@/lib/titleBar'
import SettingSection from './SettingSection.vue'
import SettingRow from './SettingRow.vue'

const { t } = useI18n()
const appStore = useAppStore()
const saving = ref(false)
const saveError = ref(false)

function endHold() {
  document.documentElement.classList.remove('title-bar-action-dragging')
  window.removeEventListener('mouseup', endHold, true)
  window.removeEventListener('blur', endHold)
}

function startHold() {
  if (saving.value) return
  document.documentElement.classList.add('title-bar-action-dragging')
  window.addEventListener('mouseup', endHold, true)
  window.addEventListener('blur', endHold)
}

onUnmounted(endHold)

async function save(showNavigation: boolean, actions: TitleBarAction[]) {
  if (saving.value) return
  saving.value = true
  saveError.value = false
  try {
    await appStore.updateTitleBar(showNavigation, actions)
  } catch (error) {
    saveError.value = true
    console.error('Failed to save title bar settings:', error)
  } finally {
    saving.value = false
  }
}

function toggleAction(id: TitleBarAction['id'], visible: boolean) {
  return save(appStore.titleBarShowNavigation, appStore.titleBarActions.map(action =>
    action.id === id ? { ...action, visible } : action,
  ))
}

function moveAction(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= appStore.titleBarActions.length) return
  const actions = [...appStore.titleBarActions]
  const [action] = actions.splice(index, 1)
  actions.splice(target, 0, action!)
  return save(appStore.titleBarShowNavigation, actions)
}
</script>

<template>
  <SettingSection :icon="AppWindow" :label="t('settings.title_bar.title')" data-test="title-bar-settings">
    <SettingRow :title="t('settings.title_bar.navigation')" :description="t('settings.title_bar.navigation_desc')">
      <Switch class="opacity-100!" style="cursor: pointer" :model-value="appStore.titleBarShowNavigation" :disabled="saving"
        :aria-label="t('settings.title_bar.navigation')"
        @update:model-value="value => save(value, appStore.titleBarActions)" />
    </SettingRow>
    <div class="p-6">
      <p class="text-sm font-medium text-foreground">{{ t('settings.title_bar.actions') }}</p>
      <p class="mt-1 mb-4 text-xs text-[color:var(--text-muted)]">{{ t('settings.title_bar.actions_desc') }}</p>
      <VirtualList :model-value="appStore.titleBarActions" data-key="id" :size="56" :keeps="5"
        handle=".title-bar-action-handle" chosen-class="title-bar-action-held" :ghost-style="{ display: 'none' }"
        :disabled="saving" :auto-scroll="false" :animation="150"
        class="h-[280px]" @update:model-value="actions => save(appStore.titleBarShowNavigation, actions)">
        <template #item="{ record: action, index }">
          <div class="flex h-14 items-center gap-3 rounded-xl pl-2 pr-3 hover:bg-foreground/[0.04] transition-colors duration-300 ease-[cubic-bezier(0.4,0,0.2,1)] select-none"
            :data-action="action.id">
            <button type="button" class="title-bar-action-handle cursor-grab text-foreground/40 hover:text-foreground/70 focus-visible:outline focus-visible:outline-foreground"
              :disabled="saving" :aria-label="t('settings.title_bar.reorder', { action: t(TITLE_BAR_ACTIONS[action.id as TitleBarAction['id']].label) })"
              @mousedown.left="startHold"
              @keydown.up.prevent="moveAction(index, -1)" @keydown.down.prevent="moveAction(index, 1)">
              <GripVertical class="size-4" aria-hidden="true" />
            </button>
            <component :is="TITLE_BAR_ACTIONS[action.id as TitleBarAction['id']].icon" class="size-4 text-foreground/70" aria-hidden="true" />
            <span class="flex-1 text-sm text-foreground">{{ t(TITLE_BAR_ACTIONS[action.id as TitleBarAction['id']].label) }}</span>
            <Checkbox class="opacity-100!" style="cursor: pointer" :checked="action.visible" :disabled="saving" role="checkbox" :tabindex="saving ? -1 : 0"
              :aria-checked="action.visible" :aria-disabled="saving"
              :aria-label="t(TITLE_BAR_ACTIONS[action.id as TitleBarAction['id']].label)"
              @update:checked="visible => toggleAction(action.id, visible)"
              @keydown.space.prevent.stop="!saving && toggleAction(action.id, !action.visible)" />
          </div>
        </template>
      </VirtualList>
      <p v-if="saveError" role="alert" class="mt-3 text-sm text-[color:var(--text-muted)]">{{ t('settings.title_bar.save_error') }}</p>
    </div>
  </SettingSection>
</template>

<style scoped>
:global(.title-bar-action-dragging),
:global(.title-bar-action-dragging *) {
  cursor: grabbing !important;
}
:deep(.title-bar-action-held) {
  background-color: color-mix(in srgb, var(--text-main) 8%, transparent);
}
</style>
