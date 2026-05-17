<script setup lang="ts">
import { ListMusic, X, Goal } from 'lucide-vue-next'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '../../stores/player'
import TrackTable from '../TrackTable.vue'
import type { TrackDTO } from '../../../bindings/airmedy/internal/domain/models'

defineProps<{
  queue: TrackDTO[]
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'play-track', index: number): void
}>()

const { t } = useI18n()
const router = useRouter()
const playerStore = usePlayerStore()
const trackTable = ref<InstanceType<typeof TrackTable> | null>(null)

function navigate(path: string) {
  if (playerStore.playerMode === 'fullscreen') {
    playerStore.playerMode = 'sticky'
  }
  router.push(path)
  emit('close')
}
</script>

<template>
  <div
    class="absolute left-0 h-[85%] my-auto bg-black/30 backdrop-blur-3xl rounded-3xl border border-white/10 flex flex-col overflow-hidden shadow-2xl w-[50cqw] max-w-xl">
    <div class="flex-1 flex flex-col h-full">
      <!-- Queue Header -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-white/5">
        <div class="flex items-center gap-2 text-white/80">
          <ListMusic class="w-4 h-4" />
          <span class="text-sm font-semibold uppercase tracking-wider">{{ t('player.up_next') }}</span>
        </div>
        <div class="flex items-center gap-1">
          <button @click="trackTable?.scrollToCurrentTrack()"
            class="text-white/40 hover:text-white transition-colors p-1 hover:bg-white/5 rounded-full">
            <Goal class="w-4 h-4" />
          </button>
          <button @click="emit('close')"
            class="text-white/40 hover:text-white transition-colors p-1 hover:bg-white/5 rounded-full">
            <X class="w-4 h-4" />
          </button>
        </div>
      </div>

      <!-- Content Area -->
      <div class="flex-1 overflow-hidden">
        <TrackTable ref="trackTable" :tracks="queue" :show-artwork="false" :scroll-to-current="true" :simple-mode="true"
          :hide-header="true" variant="glass" :allow-dnd="true"
          :context-menu-options="{ showRemoveFromQueue: true }"
          class="dark"
          @play-track="(_, index) => emit('play-track', index)"
          @reorder="tracks => playerStore.reorderQueue(tracks)"
          @navigate-album="id => navigate(`/albums/${id}`)"
          @navigate-artist="id => navigate(`/artists/${id}`)" />
      </div>
    </div>
  </div>
</template>
