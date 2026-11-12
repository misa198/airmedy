<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppWindow } from '@lucide/vue'
import { Switch } from '@airmedy/ui'
import { useAppStore } from '@/stores/app'
import { TITLE_BAR_ACTIONS, type TitleBarAction } from '@/lib/titleBar'
import SettingSection from './SettingSection.vue'
import SettingRow from './SettingRow.vue'
import SortableVisibilityList from './SortableVisibilityList.vue'

const { t } = useI18n()
const appStore = useAppStore()
const saving = ref(false)
const saveError = ref(false)
const actionOptions = computed(() => Object.fromEntries(Object.entries(TITLE_BAR_ACTIONS).map(([id, action]) => [id, {
  label: t(action.label), icon: action.icon,
}])))

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
      <SortableVisibilityList :items="appStore.titleBarActions" :options="actionOptions" :disabled="saving"
        :reorder-label="label => t('settings.title_bar.reorder', { action: label })"
        @update:items="actions => save(appStore.titleBarShowNavigation, actions as TitleBarAction[])" />
      <p v-if="saveError" role="alert" class="mt-3 text-sm text-[color:var(--text-muted)]">{{ t('settings.title_bar.save_error') }}</p>
    </div>
  </SettingSection>
</template>
