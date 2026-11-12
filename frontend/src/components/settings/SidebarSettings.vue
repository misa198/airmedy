<script setup lang="ts">
import { computed, ref } from 'vue'
import { PanelLeft } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import { SIDEBAR_ITEMS, type SidebarItem } from '@/lib/sidebar'
import SettingSection from './SettingSection.vue'
import SortableVisibilityList from './SortableVisibilityList.vue'

const { t } = useI18n()
const appStore = useAppStore()
const saving = ref(false)
const saveError = ref(false)
const itemOptions = computed(() => Object.fromEntries(Object.entries(SIDEBAR_ITEMS).map(([id, item]) => [id, {
  label: t(item.label), icon: item.icon,
}])))

async function save(items: SidebarItem[]) {
  if (saving.value) return
  saving.value = true
  saveError.value = false
  try {
    await appStore.updateSidebarItems(items)
  } catch (error) {
    saveError.value = true
    console.error('Failed to save sidebar settings:', error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <SettingSection :icon="PanelLeft" :label="t('settings.sidebar.title')" data-test="sidebar-settings">
    <div class="p-6">
      <p class="text-sm font-medium text-foreground">{{ t('settings.sidebar.items') }}</p>
      <p class="mt-1 mb-4 text-xs text-[color:var(--text-muted)]">{{ t('settings.sidebar.items_desc') }}</p>
      <SortableVisibilityList :items="appStore.sidebarItems" :options="itemOptions" :disabled="saving"
        :hidden-visibility-ids="['home']" :reorder-label="label => t('settings.sidebar.reorder', { item: label })"
        @update:items="items => save(items as SidebarItem[])" />
      <p v-if="saveError" role="alert" class="mt-3 text-sm text-[color:var(--text-muted)]">{{ t('settings.sidebar.save_error') }}</p>
    </div>
  </SettingSection>
</template>
