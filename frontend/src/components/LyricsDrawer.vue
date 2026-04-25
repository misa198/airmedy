<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Mic2, X } from 'lucide-vue-next'
import { usePlayerStore } from '../stores/player'
import { useI18n } from 'vue-i18n'
import { useLyrics } from '../composables/useLyrics'

const { t } = useI18n()
const store = usePlayerStore()

const lyricsContent = computed(() => store.lyrics?.content)
const { isSynced, syncedLines, plainLines } = useLyrics(lyricsContent)

const viewType = ref<'synced' | 'plain'>('synced')

watch(isSynced, (synced) => {
  viewType.value = synced ? 'synced' : 'plain'
}, { immediate: true })

const effectiveViewType = computed(() =>
  viewType.value === 'synced' && !isSynced.value ? 'plain' : viewType.value
)

const activeIndex = computed(() => {
  const pos = store.position
  const idx = [...syncedLines.value].reverse().findIndex(line => line.time <= pos)
  return idx !== -1 ? syncedLines.value.length - 1 - idx : -1
})

const scrollContainer = ref<HTMLElement | null>(null)
const lineRefs = ref<HTMLElement[]>([])

watch(activeIndex, (newIndex) => {
  if (newIndex === -1 || !lineRefs.value[newIndex] || !scrollContainer.value) return
  const container = scrollContainer.value
  const el = lineRefs.value[newIndex]
  container.scrollTo({
    top: el.offsetTop - container.clientHeight / 2 + el.clientHeight / 2,
    behavior: 'smooth',
  })
})
</script>

<template>
  <div class="h-full w-full bg-background flex flex-col">
    <!-- Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-foreground/[0.06] gap-2">
      <div class="flex items-center gap-2 font-semibold flex-shrink-0">
        <Mic2 class="w-4 h-4 text-primary" />
        <span>{{ t('player.lyrics') }}</span>
      </div>

      <!-- View type toggle (only when synced lyrics are available) -->
      <div v-if="isSynced" class="relative grid grid-cols-2 bg-foreground/[0.06] rounded-full p-0.5 text-xs">
        <!-- Sliding pill -->
        <div
          class="absolute inset-y-0.5 w-1/2 rounded-full bg-foreground/10 transition-transform duration-200 ease-in-out"
          :class="viewType === 'plain' ? 'translate-x-full' : 'translate-x-0'" />
        <button class="relative z-10 px-2.5 py-1 rounded-full transition-colors duration-200 text-center"
          :class="viewType === 'synced' ? 'text-foreground' : 'text-foreground/40 hover:text-foreground/70'"
          @click="viewType = 'synced'">
          {{ t('player.synced') }}
        </button>
        <button class="relative z-10 px-2.5 py-1 rounded-full transition-colors duration-200 text-center"
          :class="viewType === 'plain' ? 'text-foreground' : 'text-foreground/40 hover:text-foreground/70'"
          @click="viewType = 'plain'">
          {{ t('player.plain') }}
        </button>
      </div>

      <button
        class="p-1.5 rounded-full hover:bg-foreground/8 transition-colors text-foreground/40 hover:text-foreground flex-shrink-0"
        @click="store.toggleLyrics()">
        <X class="w-4 h-4" />
      </button>
    </div>

    <!-- Body -->
    <div class="flex-1 overflow-hidden">
      <!-- Loading skeleton -->
      <div v-if="store.lyricsLoading" class="px-4 py-8 space-y-4">
        <div v-for="(width, i) in ['w-3/4', 'w-1/2', 'w-5/6', 'w-2/3', 'w-1/3', 'w-4/5', 'w-1/2', 'w-2/3']" :key="i"
          class="h-4 rounded bg-foreground/[0.06] animate-pulse" :class="width" />
      </div>

      <!-- No lyrics -->
      <div v-else-if="!lyricsContent"
        class="h-full flex flex-col items-center justify-center text-muted-foreground gap-3 px-6 text-center">
        <Mic2 class="w-10 h-10 opacity-20" />
        <p class="text-sm">{{ t('player.lyrics_not_available') }}</p>
      </div>

      <!-- Synced view -->
      <div v-else-if="effectiveViewType === 'synced'" ref="scrollContainer"
        class="h-full overflow-y-auto px-4 py-10 scrollbar-hide">
        <div class="space-y-4">
          <div v-for="(line, index) in syncedLines" :key="index" ref="lineRefs"
            class="transition-all duration-150 cursor-pointer select-none leading-snug py-1 origin-left"
            :class="[
              index === activeIndex
                ? 'text-foreground opacity-100'
                : index < activeIndex
                  ? 'text-foreground/40 font-bold opacity-60 hover:text-foreground/50'
                  : 'text-foreground/40 font-bold opacity-40 hover:text-foreground/40',
            ]" @click="store.seek(line.time)">
            <div class="font-bold">{{ line.text }}</div>
            <div v-if="line.secondary" class="text-[0.9em] opacity-50 mt-0.5">{{ line.secondary }}</div>
          </div>
        </div>
      </div>

      <!-- Plain view -->
      <div v-else class="h-full overflow-y-auto px-4 py-6 scrollbar-hide">
        <div class="space-y-3.5">
          <div v-for="(line, index) in plainLines" :key="index" class="leading-relaxed select-text">
            <p class="text-sm text-foreground/70">{{ line.primary }}</p>
            <p v-if="line.secondary" class="text-xs text-foreground/40 mt-0.5">{{ line.secondary }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.scrollbar-hide::-webkit-scrollbar {
  display: none;
}

.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
