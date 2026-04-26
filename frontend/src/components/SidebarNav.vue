<script setup lang="ts">
import {
  Home,
  Clock,
  Users,
  Disc,
  Music,
  ListMusic,
  PenTool,
  Search,
  Settings,
} from 'lucide-vue-next'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SidebarItem from './SidebarItem.vue'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { usePlayerStore } from '../stores/player'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

const { t } = useI18n()
const playerStore = usePlayerStore()

const navItems = computed(() => [
  { name: t('sidebar.home'), icon: Home, to: '/' },
  { name: t('sidebar.recently_added'), icon: Clock, to: '/recently-added' },
  { name: t('sidebar.artists'), icon: Users, to: '/artists' },
  { name: t('sidebar.albums'), icon: Disc, to: '/albums' },
  { name: t('sidebar.tracks'), icon: Music, to: '/tracks' },
  { name: t('sidebar.genres'), icon: ListMusic, to: '/genres' },
  { name: t('sidebar.composers'), icon: PenTool, to: '/composers' },
])

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
    <SidebarItem
      v-for="item in navItems"
      :key="item.name"
      :to="item.to"
      :icon="item.icon"
      :label="item.name"
      @dblclick="handleItemDblClick(item)"
    />
  </nav>
</template>
