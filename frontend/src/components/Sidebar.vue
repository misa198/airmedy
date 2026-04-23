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
  Settings
} from 'lucide-vue-next'
import { RouterLink } from 'vue-router'

const navItems = [
  { name: 'Home', icon: Home, to: '/' },
  { name: 'Recently Added', icon: Clock, to: '/recently-added' },
  { name: 'Artists', icon: Users, to: '/artists' },
  { name: 'Albums', icon: Disc, to: '/albums' },
  { name: 'Tracks', icon: Music, to: '/tracks' },
  { name: 'Genres', icon: ListMusic, to: '/genres' },
  { name: 'Composers', icon: PenTool, to: '/composers' },
  { name: 'Search', icon: Search, to: '/search' },
  { name: 'Settings', icon: Settings, to: '/settings' },
]
</script>

<template>
  <div class="flex flex-col h-full bg-background w-full">
    <nav class="flex-1 px-3 py-2 space-y-0.5 overflow-y-auto">
      <RouterLink
        v-for="item in navItems"
        :key="item.name"
        :to="item.to"
        class="flex items-center gap-3 px-3 py-2 rounded-lg transition-colors text-white/50 hover:text-white hover:bg-white/[0.05]"
        active-class="bg-white/[0.08] text-white font-medium"
      >
        <component :is="item.icon" class="w-4 h-4 flex-shrink-0" />
        <span class="text-sm">{{ item.name }}</span>
      </RouterLink>
    </nav>
  </div>
</template>
