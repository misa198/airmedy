<script setup lang="ts">
import LazyImg from '@/components/LazyImg.vue'
import type { TrackContextMenuOptions } from '@/composables/useTrackContextMenu'
import { buildArtworkUrl, formatTime, getTrackDisplayTitle } from '@airmedy/utils'
import { Music } from '@lucide/vue'
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import VirtualList from 'vue-virtual-sortable'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { usePlayerStore } from '../stores/player'
import TrackContextMenu from './TrackContextMenu.vue'

const { t } = useI18n()
const store = usePlayerStore()
const props = defineProps<{
  scrollToCurrentOnMount?: boolean
  contextMenuOptions?: TrackContextMenuOptions
}>()
const scroller = ref<any>(null)
const trackContextMenu = ref<InstanceType<typeof TrackContextMenu> | null>(null)
const isReady = ref(!props.scrollToCurrentOnMount)
let scrollFrame: number | null = null
let scrollObserver: ResizeObserver | null = null
let disposed = false

function scrollToCurrentTrack() {
  if (!scroller.value || !store.currentTrack) return
  const index = store.queue.findIndex(track => track.id === store.currentTrack?.id)
  if (index !== -1) scroller.value.scrollToIndex(index)
}

function onContextMenu(event: MouseEvent, track: TrackDTO) {
  trackContextMenu.value?.open(event, track, props.contextMenuOptions ?? { showRemoveFromQueue: true })
}

onMounted(async () => {
  if (!props.scrollToCurrentOnMount) return
  const revealCurrentTrack = () => {
    scrollToCurrentTrack()
    isReady.value = true
    scrollFrame = null
  }
  await nextTick()
  if (disposed) return
  const list = scroller.value?.$el as Element | undefined
  if (list && typeof ResizeObserver !== 'undefined') {
    scrollObserver = new ResizeObserver(() => {
      scrollObserver?.disconnect()
      scrollObserver = null
      scrollFrame = requestAnimationFrame(revealCurrentTrack)
    })
    scrollObserver.observe(list)
  } else {
    scrollFrame = requestAnimationFrame(revealCurrentTrack)
  }
})

onUnmounted(() => {
  disposed = true
  if (scrollFrame !== null) cancelAnimationFrame(scrollFrame)
  scrollObserver?.disconnect()
})

defineExpose({ scrollToCurrentTrack })
</script>

<template>
  <div class="h-full flex-1 overflow-hidden">
    <div v-if="store.queue.length === 0"
      class="h-full flex flex-col items-center justify-center text-muted-foreground gap-3">
      <Music class="w-10 h-10 opacity-20" />
      <p class="text-sm">{{ t('player.queue_empty') }}</p>
    </div>

    <VirtualList v-show="store.queue.length > 0" ref="scroller" data-test="queue-track-list" :model-value="store.queue"
      @update:model-value="store.reorderQueue" data-key="id" :size="64" :delay="120" :force-fallback="true"
      fallback-class="drag-chosen" chosen-class="drag-chosen" :ghost-style="{ display: 'none' }"
      class="h-full overflow-y-auto select-none" :class="{ 'opacity-0': !isReady }" @scroll="trackContextMenu?.close()">
      <template #item="{ record: item, index }">
        <div
          class="w-full flex items-center gap-3 pl-3 h-16 border-l-2 text-left hover:bg-foreground/[0.04] transition-colors group relative"
          :class="{ 'bg-primary/10': store.currentTrack?.id === item.id }"
          :style="{ borderLeftColor: store.currentTrack?.id === item.id ? 'var(--primary)' : 'transparent' }"
          @click="store.playQueueIndex(index)" @dblclick="store.playQueueIndex(index)"
          @contextmenu.prevent="onContextMenu($event, item)">
          <div class="w-10 h-10 rounded-md bg-foreground/5 flex-shrink-0 overflow-hidden">
            <LazyImg v-if="item.artwork_key" :src="buildArtworkUrl(item.artwork_key, 'sm')" :alt="item.title"
              class="w-full h-full object-cover" />
            <div v-else class="w-full h-full flex items-center justify-center text-muted-foreground/30">
              <Music class="w-4 h-4" />
            </div>
          </div>

          <div class="flex-1 min-w-0">
            <div class="text-sm font-medium truncate" :class="store.currentTrack?.id === item.id ? 'text-primary' : ''">
              {{ getTrackDisplayTitle(item) || t('library.unknown_title') }}
            </div>
            <div class="text-xs text-muted-foreground truncate">
              {{item.artists?.map((artist) => artist?.name).filter(Boolean).join(', ') || item.raw_artist_names ||
                t('library.unknown_artist') }}
            </div>
          </div>

          <div class="flex items-center justify-end w-16 h-full flex-shrink-0 gap-2 pr-4">
            <div class="flex flex-col items-end">
              <div class="text-xs text-muted-foreground/50 mb-1">{{ index + 1 }}</div>
              <div class="text-xs text-muted-foreground mt-0.5">{{ formatTime(item.duration) }}</div>
            </div>
          </div>
        </div>
      </template>
    </VirtualList>
  </div>
  <TrackContextMenu ref="trackContextMenu" />
</template>

<style scoped>
.drag-chosen {
  background: var(--bg-main) !important;
  opacity: 0.9;
  z-index: 50;
  -webkit-box-shadow: 0px 0px 11px -2px rgba(0, 0, 0, 0.3);
  box-shadow: 0px 0px 11px -2px rgba(0, 0, 0, 0.3);
  cursor: grabbing !important;
}
</style>
