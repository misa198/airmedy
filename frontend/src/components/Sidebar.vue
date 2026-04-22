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
  <div class="flex flex-col h-full bg-sidebar w-full">
    <nav class="flex-1 px-4 py-2 space-y-1 overflow-y-auto">
      <RouterLink
        v-for="item in navItems"
        :key="item.name"
        :to="item.to"
        class="flex items-center gap-3 px-3 py-2 rounded-md transition-colors text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
        active-class="bg-sidebar-accent text-sidebar-accent-foreground font-medium"
      >
        <component :is="item.icon" class="w-5 h-5" />
        <span>{{ item.name }}</span>
      </RouterLink>
    </nav>
    
    <div class="p-4 border-t">
      <div class="p-4 bg-card rounded-lg border border-dashed text-center">
        <p class="text-xs text-muted-foreground italic">Playlists coming soon...</p>
      </div>
    </div>
  </div>
</template>
