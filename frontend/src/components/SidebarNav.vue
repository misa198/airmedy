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

const { t } = useI18n()

const navItems = computed(() => [
  { name: t('sidebar.home'), icon: Home, to: '/' },
  { name: t('sidebar.recently_added'), icon: Clock, to: '/recently-added' },
  { name: t('sidebar.artists'), icon: Users, to: '/artists' },
  { name: t('sidebar.albums'), icon: Disc, to: '/albums' },
  { name: t('sidebar.tracks'), icon: Music, to: '/tracks' },
  { name: t('sidebar.genres'), icon: ListMusic, to: '/genres' },
  { name: t('sidebar.composers'), icon: PenTool, to: '/composers' },
])
</script>

<template>
  <nav class="px-3 py-2 space-y-0.5">
    <SidebarItem v-for="item in navItems" :key="item.name" :to="item.to" :icon="item.icon" :label="item.name" />
  </nav>
</template>
