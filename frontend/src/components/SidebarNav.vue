<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { usePlayerStore } from '../stores/player'
import { SIDEBAR_ITEMS, type SidebarItemId } from '../lib/sidebar'
import { useAppStore } from '../stores/app'
import SidebarItem from './SidebarItem.vue'

const { t } = useI18n()
const playerStore = usePlayerStore()
const appStore = useAppStore()

const navItems = computed(() => appStore.sidebarItems
  .filter(item => item.visible)
  .map(item => ({
    id: item.id,
    name: t(SIDEBAR_ITEMS[item.id as SidebarItemId].label),
    icon: SIDEBAR_ITEMS[item.id as SidebarItemId].icon,
    to: SIDEBAR_ITEMS[item.id as SidebarItemId].path,
  })))

const handleItemDblClick = async (item: any) => {
  if (item.to === '/tracks') {
    try {
      const tracks = await LibraryService.GetAllTracks()
      const validTracks = tracks.filter((t): t is TrackDTO => t !== null)
      if (validTracks.length > 0) {
        await playerStore.shuffleTracks(validTracks)
      }
    } catch (err) {
      console.error('Failed to shuffle all tracks:', err)
    }
  }
}
</script>

<template>
  <nav class="px-3 py-2 space-y-0.5">
    <SidebarItem v-for="item in navItems" :key="item.id" :to="item.to" :icon="item.icon" :label="item.name"
      @dblclick="handleItemDblClick(item)" />
  </nav>
</template>
