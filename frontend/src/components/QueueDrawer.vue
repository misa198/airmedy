<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import { Music, X, ListMusic, MoreVertical, GripVertical, Goal } from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { formatTime, buildArtworkUrl, getTrackDisplayTitle } from '../lib/utils'
import LazyImg from '@/components/LazyImg.vue'
import { useI18n } from 'vue-i18n'
import TrackContextMenu from './TrackContextMenu.vue'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import VirtualList from 'vue-virtual-sortable'

const { t } = useI18n()
const store = usePlayerStore()
const scroller = ref<any>(null)
const trackContextMenu = ref<InstanceType<typeof TrackContextMenu> | null>(null)

const scrollToCurrentTrack = () => {
  if (!scroller.value || !store.currentTrack) return
  const index = store.queue.findIndex(t => t.id === store.currentTrack?.id)
  if (index !== -1) {
    scroller.value.scrollToIndex(index)
  }
}

const onContextMenu = (e: MouseEvent, track: TrackDTO) => {
  trackContextMenu.value?.open(e, track, { showRemoveFromQueue: true })
}

let _scrollTimer: ReturnType<typeof setTimeout> | null = null

watch(() => store.isQueueOpen, (open) => {
  if (open) {
    _scrollTimer = setTimeout(() => {
      scrollToCurrentTrack()
      _scrollTimer = null
    }, 100)
  }
}, { immediate: true })

onUnmounted(() => {
  if (_scrollTimer) clearTimeout(_scrollTimer)
})
</script>

<template>
  <div class="h-full w-full bg-background flex flex-col">
    <!-- Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-foreground/[0.06]">
      <div class="flex items-center gap-2 font-semibold">
        <ListMusic class="w-4 h-4 text-primary" />
        <span>{{ t('player.queue') }}</span>
        <span class="text-xs text-muted-foreground font-normal ml-1">({{ store.queue.length }})</span>
      </div>
      <div class="flex items-center gap-1">
        <button
          class="p-1.5 rounded-full hover:bg-foreground/8 transition-colors text-foreground opacity-60 hover:text-foreground"
          @click="scrollToCurrentTrack()"
          :title="t('player.scroll_to_current')"
        >
          <Goal class="w-4 h-4" />
        </button>
        <button
          class="p-1.5 rounded-full hover:bg-foreground/8 transition-colors text-foreground opacity-60 hover:text-foreground"
          @click="store.toggleQueue()"
          :title="t('common.close')"
        >
          <X class="w-4 h-4" />
        </button>
      </div>
    </div>

    <!-- Queue list -->
    <div class="flex-1 overflow-hidden">
      <div v-if="store.queue.length === 0" class="h-full flex flex-col items-center justify-center text-muted-foreground gap-3">
        <Music class="w-10 h-10 opacity-20" />
        <p class="text-sm">{{ t('player.queue_empty') }}</p>
      </div>

      <VirtualList
        v-show="store.queue.length > 0"
        ref="scroller"
        :model-value="store.queue"
        @update:model-value="store.reorderQueue"
        data-key="id"
        :size="64"
        handle=".dnd-handle"
        :force-fallback="true"
        fallback-class="drag-chosen"
        chosen-class="drag-chosen"
        :ghost-style="{ display: 'none' }"
        class="h-full overflow-y-auto select-none"
        @scroll="trackContextMenu?.close()"
      >
        <template v-slot:item="{ record: item, index }">
          <div
            class="w-full flex items-center gap-3 px-0 h-16 text-left hover:bg-foreground/[0.04] transition-colors group relative"
            :class="{ 'bg-primary/10 border-l-2 border-l-primary': store.currentTrack?.id === item.id }"
            @dblclick="store.playQueueIndex(index)"
            @contextmenu.prevent="onContextMenu($event, item)"
          >
            <!-- Drag Handle -->
            <div class="dnd-handle cursor-grab active:cursor-grabbing text-foreground opacity-20 group-hover:opacity-60 transition-opacity px-2">
              <GripVertical class="w-4 h-4 pointer-events-none" />
            </div>

            <!-- Artwork -->
            <div class="w-10 h-10 rounded-md bg-foreground/5 flex-shrink-0 overflow-hidden" @click="store.playQueueIndex(index)">
              <LazyImg
                v-if="item.artwork_key"
                :src="buildArtworkUrl(item.artwork_key, 'sm')"
                :alt="item.title"
                class="w-full h-full object-cover"
              />
              <div v-else class="w-full h-full flex items-center justify-center text-muted-foreground/30">
                <Music class="w-4 h-4" />
              </div>
            </div>

            <!-- Track info -->
            <div class="flex-1 min-w-0" @click="store.playQueueIndex(index)">
              <div
                class="text-sm font-medium truncate"
                :class="store.currentTrack?.id === item.id ? 'text-primary' : ''"
              >
                {{ getTrackDisplayTitle(item) || t('library.unknown_title') }}
              </div>
              <div class="text-xs text-muted-foreground truncate">
                {{ item.artists?.map((a) => a?.name).filter(Boolean).join(', ') || item.raw_artist_names || t('library.unknown_artist') }}
              </div>
            </div>

            <!-- Duration + index + Context Menu -->
            <div class="flex items-center justify-end w-20 h-full flex-shrink-0 gap-2">
              <div class="flex flex-col items-end">
                <div class="text-xs text-muted-foreground/50 mb-1">{{ index + 1 }}</div>
                <div class="text-xs text-muted-foreground mt-0.5">{{ formatTime(item.duration) }}</div>
              </div>
              <button
                class="p-2 hover:bg-foreground/8 rounded-full text-foreground opacity-50 hover:text-foreground opacity-90 transition-colors"
                @click.stop="onContextMenu($event, item)"
              >
                <MoreVertical class="w-4 h-4" />
              </button>
            </div>
          </div>
        </template>
      </VirtualList>
    </div>
  </div>
  <TrackContextMenu ref="trackContextMenu" />
</template>

<style scoped>
.drag-chosen {
  background: var(--bg-main) !important;
  opacity: 0.9;
  z-index: 50;
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.2);
  border-radius: 4px;
}
</style>