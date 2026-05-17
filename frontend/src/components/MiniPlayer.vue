<script setup lang="ts">
import { Play, Pause, SkipForward, Maximize, Music } from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { formatTime, getTrackDisplayTitle } from '../lib/utils'
import MarqueeText from './MarqueeText.vue'
import LazyImg from '@/components/LazyImg.vue'

const store = usePlayerStore()
</script>

<template>
  <div class="h-14 bg-background/80 backdrop-blur-2xl border-t border-foreground/[0.06] flex items-center px-4 gap-3">
    <!-- Artwork + track info -->
    <div class="flex items-center gap-3 flex-1 min-w-0">
      <div class="w-9 h-9 rounded-md flex-shrink-0 overflow-hidden ring-1 ring-foreground/10 cursor-pointer transition-transform hover:scale-105 active:scale-95"
        @click="store.openTrackInfo(store.currentTrack)">
        <LazyImg
          v-if="store.artworkUrlSm"
          :src="store.artworkUrlSm"
          class="w-full h-full object-cover"
        />
        <div v-else class="w-full h-full bg-foreground/5 flex items-center justify-center">
          <Music class="w-4 h-4 text-foreground opacity-40" />
        </div>
      </div>
      <div class="min-w-0 flex-1">
        <MarqueeText
          :text="store.currentTrack ? (getTrackDisplayTitle(store.currentTrack) || 'Not Playing') : 'Not Playing'"
          content-class="text-sm font-medium leading-tight"
        />
        <MarqueeText
          :text="store.currentTrack?.artists?.map((a) => a?.name).filter(Boolean).join(', ') ?? ''"
          content-class="text-xs text-foreground opacity-60 leading-tight mt-0.5"
        />
      </div>
    </div>

    <!-- Controls -->
    <div class="flex items-center gap-3">
      <button
        class="w-7 h-7 bg-foreground rounded-full flex items-center justify-center hover:scale-105 transition-transform"
        @click="store.togglePlayPause()"
      >
        <Pause v-if="store.isPlaying" class="w-3.5 h-3.5 fill-current text-background" />
        <Play v-else class="w-3.5 h-3.5 fill-current text-background ml-0.5" />
      </button>
      <button class="text-foreground opacity-60 hover:text-foreground opacity-90 transition-colors" @click="store.next()">
        <SkipForward class="w-4 h-4 fill-current" />
      </button>
    </div>

    <!-- Time + expand -->
    <div class="flex items-center gap-2">
      <span class="text-[10px] text-foreground opacity-50 tabular-nums">
        {{ formatTime(store.position) }} / {{ formatTime(store.duration) }}
      </span>
      <button
        class="text-foreground opacity-60 hover:text-foreground opacity-90 transition-colors"
        @click="store.playerMode = 'sticky'"
      >
        <Maximize class="w-4 h-4" />
      </button>
    </div>
  </div>
</template>
