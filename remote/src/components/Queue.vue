<script setup lang="ts">
import { ref, computed } from 'vue'
import { Music, ListMusic, Goal } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { send } from '../ws'
import { usePlayerStore } from '../stores/player'
import { getTrackDisplayTitle } from '@/lib/utils'
import PlayingBar from './PlayingBar.vue'

const { t } = useI18n()
const store = usePlayerStore()
const scrollerRef = ref<any>(null)
const queue = computed(() => store.queue)
const currentId = computed(() => store.status?.track_id ?? '')
const isPlaying = computed(() => store.status?.playback_state === 'playing')

function playAt(index: number) {
  send({ type: 'play_queue_index', index })
}

function scrollToCurrentTrack() {
  if (!scrollerRef.value || !currentId.value || queue.value.length === 0) return
  const index = queue.value.findIndex((t) => t.id === currentId.value)
  if (index !== -1) {
    if (typeof scrollerRef.value.scrollToItem === 'function') {
      scrollerRef.value.scrollToItem(index)
    } else if (typeof scrollerRef.value.scrollToIndex === 'function') {
      scrollerRef.value.scrollToIndex(index)
    } else if (scrollerRef.value.$el) {
      // Fallback to direct scroll if methods are missing
      scrollerRef.value.$el.scrollTop = index * 68
    }
  }
}

function artistNames(track: (typeof queue.value)[number]) {
  const names = track.artists?.map(a => a.name).join(', ')
  if (names) return names
  if (track.album?.title) return track.album.title
  return t('library.unknown_artist')
}
</script>

<template>
  <div
    class="flex flex-col h-full bg-black/20 backdrop-blur-3xl rounded-3xl border border-white/10 overflow-hidden shadow-2xl">
    <div class="px-6 py-4 flex items-center justify-between border-b border-white/5">
      <div class="flex items-center gap-2 text-white/80">
        <ListMusic class="w-4 h-4" />
        <h3 class="text-sm font-semibold uppercase tracking-wider">{{ t('player.up_next') }}</h3>
      </div>
      <div class="flex items-center gap-3">
        <button v-if="currentId" @click="scrollToCurrentTrack"
          class="text-white/40 hover:text-white transition-colors p-1 hover:bg-white/5 rounded-full"
          :title="t('player.scroll_to_current')">
          <Goal class="w-4 h-4" />
        </button>
        <span class="text-[11px] font-medium text-white/30 uppercase tracking-widest">{{ t('library.tracks_count', { count: queue.length }) }}</span>
      </div>
    </div>

    <div class="flex-1 overflow-hidden relative">
      <RecycleScroller v-show="queue.length > 0" ref="scrollerRef" class="h-full custom-scrollbar" :items="queue"
        :item-size="68" key-field="id" v-slot="{ item: track, index: i }">
        <div
          class="flex items-center gap-4 px-4 py-3.5 cursor-pointer active:bg-white/10 lg:hover:bg-white/5 transition-all group relative"
          :class="track.id === currentId ? 'bg-white/10' : ''" @click="playAt(i)">
          <!-- Index / Playing indicator -->
          <div class="w-5 shrink-0 flex items-center justify-center">
            <PlayingBar v-if="track.id === currentId" :is-playing="isPlaying" />
            <span v-else class="text-xs text-white/30 tabular-nums">{{ i + 1 }}</span>
          </div>

          <!-- Artwork -->
          <div class="w-10 h-10 rounded-lg overflow-hidden bg-white/5 shrink-0 shadow-lg ring-1 ring-white/10">
            <img v-if="track.artwork_key" :src="store.artworkUrl(track.artwork_key, 'sm')" :alt="getTrackDisplayTitle(track)"
              class="w-full h-full object-cover" draggable="false" />
            <div v-else class="w-full h-full flex items-center justify-center">
              <Music class="w-4 h-4 text-white/10" />
            </div>
          </div>

          <!-- Info -->
          <div class="flex-1 min-w-0">
            <p class="text-sm font-semibold truncate"
              :class="track.id === currentId ? 'text-primary' : 'text-white/90'">{{ getTrackDisplayTitle(track) }}</p>
            <p class="text-xs text-white/40 truncate mt-0.5">{{ artistNames(track) }}</p>
          </div>
        </div>
      </RecycleScroller>

      <div v-show="queue.length === 0" class="flex flex-col items-center justify-center h-48 gap-3">
        <Music class="w-8 h-8 text-white/5" />
        <p class="text-sm text-white/20 font-medium">{{ t('player.queue_empty') }}</p>
      </div>
    </div>
  </div>
</template>
