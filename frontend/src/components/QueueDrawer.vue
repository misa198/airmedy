<script setup lang="ts">
import { Music, X, ListMusic } from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { formatTime } from '../lib/utils'

const store = usePlayerStore()
</script>

<template>
  <Transition
    enter-active-class="transition-transform duration-300 ease-out"
    enter-from-class="translate-x-full"
    enter-to-class="translate-x-0"
    leave-active-class="transition-transform duration-300 ease-in"
    leave-from-class="translate-x-0"
    leave-to-class="translate-x-full"
  >
    <div
      v-if="store.isQueueOpen"
      class="fixed right-0 top-0 bottom-0 w-80 bg-card/95 backdrop-blur-xl border-l border-border z-50 flex flex-col shadow-2xl"
    >
      <!-- Header -->
      <div class="flex items-center justify-between p-4 border-b">
        <div class="flex items-center gap-2 font-semibold">
          <ListMusic class="w-4 h-4 text-primary" />
          <span>Queue</span>
          <span class="text-xs text-muted-foreground font-normal ml-1">({{ store.queue.length }})</span>
        </div>
        <button
          class="p-2 rounded-full hover:bg-accent transition-colors text-muted-foreground hover:text-foreground"
          @click="store.toggleQueue()"
        >
          <X class="w-4 h-4" />
        </button>
      </div>

      <!-- Queue list -->
      <div class="flex-1 overflow-y-auto">
        <div v-if="store.queue.length === 0" class="h-full flex flex-col items-center justify-center text-muted-foreground gap-3">
          <Music class="w-10 h-10 opacity-20" />
          <p class="text-sm">Queue is empty</p>
        </div>

        <RecycleScroller
          v-else
          class="h-full"
          :items="store.queue"
          :item-size="64"
          key-field="id"
          v-slot="{ item, index }"
        >
          <button
            class="w-full flex items-center gap-3 px-4 h-16 text-left hover:bg-accent/50 transition-colors border-b border-transparent hover:border-accent group"
            :class="{ 'bg-primary/10 border-l-2 border-l-primary': store.currentTrack?.id === item.id }"
            @click="store.playTracks(store.queue, index)"
          >
            <!-- Artwork -->
            <div class="w-10 h-10 rounded bg-muted flex-shrink-0 overflow-hidden border">
              <img
                v-if="item.artwork_key"
                :src="`/artwork/${item.artwork_key}`"
                :alt="item.title"
                class="w-full h-full object-cover"
              />
              <div v-else class="w-full h-full flex items-center justify-center text-muted-foreground/30">
                <Music class="w-4 h-4" />
              </div>
            </div>

            <!-- Track info -->
            <div class="flex-1 min-w-0">
              <div
                class="text-sm font-medium truncate"
                :class="store.currentTrack?.id === item.id ? 'text-primary' : ''"
              >
                {{ item.title || 'Unknown Title' }}
              </div>
              <div class="text-xs text-muted-foreground truncate">
                {{ item.artists?.map((a) => a?.name).filter(Boolean).join(', ') || item.raw_artist_names || 'Unknown Artist' }}
              </div>
            </div>

            <!-- Duration + index -->
            <div class="text-right flex-shrink-0">
              <div class="text-xs font-mono text-muted-foreground">{{ formatTime(item.duration) }}</div>
              <div class="text-xs text-muted-foreground/50 mt-0.5">{{ index + 1 }}</div>
            </div>
          </button>
        </RecycleScroller>
      </div>
    </div>
  </Transition>
</template>
