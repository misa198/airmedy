<script setup lang="ts">
import { Play, Pause, SkipForward, Maximize2, Music } from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { formatTime } from '../lib/utils'

const store = usePlayerStore()
</script>

<template>
  <div
    class="h-16 bg-card/90 backdrop-blur border-t flex items-center px-4 gap-3 shadow-lg"
  >
    <!-- Artwork + track info -->
    <div class="flex items-center gap-3 flex-1 min-w-0">
      <div
        class="w-10 h-10 bg-accent rounded flex-shrink-0 overflow-hidden border"
      >
        <img
          v-if="store.artworkUrl"
          :src="store.artworkUrl"
          class="w-full h-full object-cover"
        />
        <div v-else class="w-full h-full flex items-center justify-center text-muted-foreground/30">
          <Music class="w-4 h-4" />
        </div>
      </div>
      <div class="min-w-0">
        <div class="text-sm font-medium truncate">
          {{ store.currentTrack?.title ?? 'Not Playing' }}
        </div>
        <div class="text-xs text-muted-foreground truncate">
          {{ store.currentTrack?.artists?.map((a) => a?.name).filter(Boolean).join(', ') ?? '' }}
        </div>
      </div>
    </div>

    <!-- Controls -->
    <div class="flex items-center gap-3">
      <button
        class="w-8 h-8 bg-primary rounded-full flex items-center justify-center hover:scale-105 transition-transform"
        @click="store.togglePlayPause()"
      >
        <Pause v-if="store.isPlaying" class="w-4 h-4 fill-current" />
        <Play v-else class="w-4 h-4 fill-current ml-0.5" />
      </button>
      <button class="text-muted-foreground hover:text-foreground transition-colors" @click="store.next()">
        <SkipForward class="w-4 h-4 fill-current" />
      </button>
    </div>

    <!-- Progress + expand -->
    <div class="flex items-center gap-2">
      <span class="text-[10px] text-muted-foreground font-mono">
        {{ formatTime(store.position) }} / {{ formatTime(store.duration) }}
      </span>
      <button
        class="text-muted-foreground hover:text-foreground transition-colors"
        @click="store.playerMode = 'sticky'"
      >
        <Maximize2 class="w-4 h-4" />
      </button>
    </div>
  </div>
</template>
