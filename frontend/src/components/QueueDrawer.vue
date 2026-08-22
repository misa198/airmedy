<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import { X, ListMusic, Goal, Radio } from '@lucide/vue'
import { usePlayerStore } from '../stores/player'
import { useMoodRadioStore } from '../stores/moodRadio'
import { useI18n } from 'vue-i18n'
import QueueTrackList from './QueueTrackList.vue'

const { t } = useI18n()
const store = usePlayerStore()
const moodRadioStore = useMoodRadioStore()
const queueTrackList = ref<InstanceType<typeof QueueTrackList> | null>(null)

const scrollToCurrentTrack = () => queueTrackList.value?.scrollToCurrentTrack()

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
    <div class="flex items-center justify-between px-4 py-3 border-b border-foreground/[0.06] select-none">
      <div class="flex items-center gap-2 font-semibold">
        <ListMusic class="w-4 h-4 text-primary" />
        <span>{{ t('player.queue') }}</span>
        <Radio v-if="moodRadioStore.active" class="w-3.5 h-3.5 text-primary ml-1" :title="t('player.mood_radio_active')" />
        <span v-else class="text-xs text-muted-foreground font-normal ml-1">({{ store.queue.length }})</span>
      </div>
      <div class="flex items-center gap-1">
        <button
          class="p-1.5 rounded-full hover:bg-foreground/8 transition-colors text-dim hover:text-foreground"
          @click="scrollToCurrentTrack()"
          :title="t('player.scroll_to_current')"
        >
          <Goal class="w-4 h-4" />
        </button>
        <button
          class="p-1.5 rounded-full hover:bg-foreground/8 transition-colors text-dim hover:text-foreground"
          @click="store.toggleQueue()"
          :title="t('common.close')"
        >
          <X class="w-4 h-4" />
        </button>
      </div>
    </div>

    <QueueTrackList ref="queueTrackList" />
  </div>
</template>
