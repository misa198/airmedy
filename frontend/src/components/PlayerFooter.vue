<script setup lang="ts">
import { 
  Play, 
  SkipBack, 
  SkipForward, 
  Repeat, 
  Shuffle, 
  Volume2, 
  Maximize2,
  Music
} from 'lucide-vue-next'
</script>

<template>
  <div class="h-24 bg-card border-t flex items-center px-6 gap-6 shadow-2xl">
    <!-- Track Info -->
    <div class="flex items-center gap-4 w-1/4 min-w-[200px]">
      <div class="w-14 h-14 bg-accent rounded-md flex items-center justify-center shadow-inner overflow-hidden border">
        <Music class="w-6 h-6 text-muted-foreground" />
      </div>
      <div class="flex flex-col truncate">
        <span class="font-medium text-sm truncate">Not Playing</span>
        <span class="text-xs text-muted-foreground truncate">Select a track to start listening</span>
      </div>
    </div>

    <!-- Playback Controls -->
    <div class="flex-1 flex flex-col items-center max-w-2xl">
      <div class="flex items-center gap-6 mb-2">
        <button class="text-muted-foreground hover:text-foreground transition-colors">
          <Shuffle class="w-4 h-4" />
        </button>
        <button class="text-foreground hover:text-primary transition-colors">
          <SkipBack class="w-5 h-5 fill-current" />
        </button>
        <button class="w-10 h-10 bg-primary rounded-full flex items-center justify-center hover:scale-105 transition-transform shadow-lg">
          <Play class="w-5 h-5 fill-current ml-0.5" />
        </button>
        <button class="text-foreground hover:text-primary transition-colors">
          <SkipForward class="w-5 h-5 fill-current" />
        </button>
        <button class="text-muted-foreground hover:text-foreground transition-colors">
          <Repeat class="w-4 h-4" />
        </button>
      </div>
      <div class="w-full flex items-center gap-3">
        <span class="text-[10px] text-muted-foreground w-8 text-right">0:00</span>
        <div class="flex-1 h-1 bg-accent rounded-full relative overflow-hidden group">
          <div class="absolute inset-y-0 left-0 bg-primary w-0 group-hover:bg-primary/80 transition-all"></div>
        </div>
        <span class="text-[10px] text-muted-foreground w-8">0:00</span>
      </div>
    </div>

    <!-- Volume & Options -->
    <div class="flex items-center justify-end gap-4 w-1/4 min-w-[150px]">
      <div class="flex items-center gap-2 w-32">
        <Volume2 class="w-4 h-4 text-muted-foreground" />
        <div class="flex-1 h-1 bg-accent rounded-full relative overflow-hidden group">
          <div class="absolute inset-y-0 left-0 bg-primary w-2/3 group-hover:bg-primary/80 transition-all"></div>
        </div>
      </div>
      <button class="text-muted-foreground hover:text-foreground transition-colors">
        <Maximize2 class="w-4 h-4" />
      </button>
    </div>
  </div>
</template>
