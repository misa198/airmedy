<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronLeft, ChevronRight, Users } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { buildArtworkUrl, formatTotalDuration } from '@airmedy/utils'
import LazyImg from '@/components/LazyImg.vue'

const props = defineProps<{
  artists: { id: string; name: string; artwork_key: string; listened_seconds: number }[]
}>()

const { t } = useI18n()
const router = useRouter()
const carouselRef = ref<HTMLElement | null>(null)
const columnsPerPage = ref(4)
const currentPage = ref(0)
const transitionName = ref('slide-next')
let resizeObserver: ResizeObserver | null = null

const itemsPerPage = computed(() => columnsPerPage.value)
const totalPages = computed(() => Math.ceil(props.artists.length / itemsPerPage.value))
const paginatedArtists = computed(() => {
  const maxStart = Math.max(0, props.artists.length - itemsPerPage.value)
  const start = Math.min(currentPage.value * itemsPerPage.value, maxStart)
  return props.artists.slice(start, start + itemsPerPage.value)
})

const formatTime = (seconds: number) => formatTotalDuration(seconds, t)

function updateColumns() {
  if (!carouselRef.value) return
  const itemWidth = 128
  const gap = 16
  columnsPerPage.value = Math.max(1, Math.floor((carouselRef.value.offsetWidth + gap) / (itemWidth + gap)))
}

function next() {
  if (currentPage.value >= totalPages.value - 1) return
  transitionName.value = 'slide-next'
  currentPage.value++
}

function previous() {
  if (currentPage.value <= 0) return
  transitionName.value = 'slide-prev'
  currentPage.value--
}

watch(() => props.artists.length, () => { currentPage.value = 0 })
watch(totalPages, (pages) => {
  if (currentPage.value >= pages && pages > 0) currentPage.value = pages - 1
})

onMounted(() => {
  updateColumns()
  if (carouselRef.value) {
    resizeObserver = new ResizeObserver(updateColumns)
    resizeObserver.observe(carouselRef.value)
  }
})
onUnmounted(() => resizeObserver?.disconnect())
</script>

<template>
  <article ref="carouselRef" class="rounded-xl border border-[var(--border-glass)] bg-[var(--bg-glass)] p-5 backdrop-blur-[30px]">
    <div class="mb-4 flex items-center justify-between gap-4">
      <div class="flex items-center gap-2 text-xs font-medium text-[color:var(--text-muted)]"><Users class="h-4 w-4" /><h2>{{ t('analytics.top_artists') }}</h2></div>
      <div v-if="totalPages > 1" class="flex gap-1">
        <button type="button" :disabled="currentPage === 0" class="flex h-7 w-7 items-center justify-center rounded-full text-[color:var(--text-muted)] transition-all hover:bg-white/[0.04] hover:text-[color:var(--text-main)] disabled:opacity-30" @click="previous"><ChevronLeft class="h-4 w-4" /></button>
        <button type="button" :disabled="currentPage === totalPages - 1" class="flex h-7 w-7 items-center justify-center rounded-full text-[color:var(--text-muted)] transition-all hover:bg-white/[0.04] hover:text-[color:var(--text-main)] disabled:opacity-30" @click="next"><ChevronRight class="h-4 w-4" /></button>
      </div>
    </div>
    <div v-if="artists.length" class="relative overflow-hidden">
      <Transition :name="transitionName">
        <div :key="currentPage" class="grid gap-4" :style="{ gridTemplateColumns: `repeat(${columnsPerPage}, minmax(0, 1fr))` }">
          <button v-for="artist in paginatedArtists" :key="artist.id" type="button" class="group min-w-0 text-center" @click="router.push(`/artists/${artist.id}`)">
            <div class="relative mx-auto aspect-square w-full max-w-32 overflow-hidden rounded-full border border-[var(--border-glass)] bg-white/[0.04] transition-all duration-300 group-hover:brightness-110">
              <LazyImg v-if="artist.artwork_key" :src="buildArtworkUrl(artist.artwork_key, 'md')" :alt="artist.name" class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105" />
              <Users v-else class="absolute inset-0 m-auto h-9 w-9 text-[color:var(--text-muted)]" />
            </div>
            <p class="mt-3 truncate text-sm font-medium">{{ artist.name }}</p>
            <p class="mt-1 text-xs text-[color:var(--text-muted)]">{{ formatTime(artist.listened_seconds) }}</p>
          </button>
        </div>
      </Transition>
    </div>
    <div v-else class="flex min-h-32 flex-col items-center justify-center text-center text-[color:var(--text-muted)]"><Users class="mb-2 h-6 w-6 opacity-60" /><p class="text-sm">{{ t('analytics.no_top_artists') }}</p></div>
  </article>
</template>

<style scoped>
.slide-next-enter-active,
.slide-next-leave-active,
.slide-prev-enter-active,
.slide-prev-leave-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-next-enter-from { transform: translateX(32px); opacity: 0; }
.slide-next-leave-to { transform: translateX(-32px); opacity: 0; }
.slide-prev-enter-from { transform: translateX(-32px); opacity: 0; }
.slide-prev-leave-to { transform: translateX(32px); opacity: 0; }

.slide-next-leave-active,
.slide-prev-leave-active { position: absolute; inset: 0; width: 100%; pointer-events: none; }
</style>
