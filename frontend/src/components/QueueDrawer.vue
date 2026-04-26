<script setup lang="ts">
import { ref, watch } from 'vue'
import { Music, X, ListMusic, MoreVertical } from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { formatTime } from '../lib/utils'
import { useI18n } from 'vue-i18n'
import TrackContextMenu from './TrackContextMenu.vue'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

const { t } = useI18n()
const store = usePlayerStore()
const scroller = ref<any>(null)
const trackContextMenu = ref<InstanceType<typeof TrackContextMenu> | null>(null)

const scrollToCurrentTrack = () => {
  if (!scroller.value || !store.currentTrack) return
  const index = store.queue.findIndex(t => t.id === store.currentTrack?.id)
  if (index !== -1) {
    scroller.value.scrollToItem(index)
  }
}

const onContextMenu = (e: MouseEvent, track: TrackDTO) => {
  trackContextMenu.value?.open(e, track, { showRemoveFromQueue: true })
}

watch(() => store.isQueueOpen, (open) => {
  if (open) {
    setTimeout(() => {
      scrollToCurrentTrack()
    }, 100)
  }
}, { immediate: true })
</script>

<template>
  <div
    class="h-full w-full bg-background flex flex-col"
  >
    <!-- Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-foreground/[0.06]">
      <div class="flex items-center gap-2 font-semibold">
        <ListMusic class="w-4 h-4 text-primary" />
        <span>{{ t('player.queue') }}</span>
        <span class="text-xs text-muted-foreground font-normal ml-1">({{ store.queue.length }})</span>
      </div>
      <button
        class="p-1.5 rounded-full hover:bg-foreground/8 transition-colors text-foreground/40 hover:text-foreground"
        @click="store.toggleQueue()"
      >
        <X class="w-4 h-4" />
      </button>
    </div>

    <!-- Queue list -->
    <div class="flex-1 overflow-y-auto">
      <div v-if="store.queue.length === 0" class="h-full flex flex-col items-center justify-center text-muted-foreground gap-3">
        <Music class="w-10 h-10 opacity-20" />
        <p class="text-sm">{{ t('player.queue_empty') }}</p>
      </div>

      <RecycleScroller
        v-else
        ref="scroller"
        class="h-full"
        :items="store.queue"
        :item-size="64"
        key-field="id"
        v-slot="{ item, index }"
      >
        <button
          class="w-full flex items-center gap-3 px-4 h-16 text-left hover:bg-foreground/[0.04] transition-colors group relative select-none"
          :class="{ 'bg-primary/10 border-l-2 border-l-primary': store.currentTrack?.id === item.id }"
          @click="store.playTracks(store.queue, index)"
          @contextmenu.prevent="onContextMenu($event, item)"
        >
          <!-- Artwork -->
          <div class="w-10 h-10 rounded-md bg-foreground/5 flex-shrink-0 overflow-hidden">
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
              {{ item.title || t('library.unknown_title') }}
            </div>
            <div class="text-xs text-muted-foreground truncate">
              {{ item.artists?.map((a) => a?.name).filter(Boolean).join(', ') || item.raw_artist_names || t('library.unknown_artist') }}
            </div>
          </div>

          <!-- Duration + index + Context Menu Overlay -->
          <div class="relative flex items-center justify-end w-20 h-full flex-shrink-0">
            <div class="flex flex-col items-end group-hover:opacity-0 transition-opacity">
              <div class="text-xs text-muted-foreground mb-1">{{ formatTime(item.duration) }}</div>
              <div class="text-xs text-muted-foreground/50 mt-0.5">{{ index + 1 }}</div>
            </div>
            <div
              class="absolute inset-0 flex items-center justify-end opacity-0 group-hover:opacity-100 transition-opacity"
            >
              <button
                class="p-2 hover:bg-foreground/8 rounded-full text-foreground/30 hover:text-foreground/70 transition-colors"
                @click.stop="onContextMenu($event, item)"
              >
                <MoreVertical class="w-4 h-4" />
              </button>
            </div>
          </div>
        </button>
      </RecycleScroller>
    </div>
  </div>
  <TrackContextMenu ref="trackContextMenu" />
</template>
