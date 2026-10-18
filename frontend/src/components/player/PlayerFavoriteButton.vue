<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { Star } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '@/stores/player'
import { useFavoritesStore } from '@/stores/favorites'

defineProps<{ mini?: boolean; large?: boolean }>()

const { t } = useI18n()
const store = usePlayerStore()
const favoritesStore = useFavoritesStore()
const isTogglingFavorite = ref(false)
const animateFavorite = ref(false)
let favoriteTransitionTimer: ReturnType<typeof setTimeout> | undefined

function stopFavoriteTransition() {
  clearTimeout(favoriteTransitionTimer)
  animateFavorite.value = false
}

watch(() => store.currentTrack?.id, stopFavoriteTransition)
onUnmounted(stopFavoriteTransition)

const isFavorite = computed(() => store.currentTrack ? favoritesStore.isFavorite(store.currentTrack) : false)
const favoriteLabel = computed(() => t(isFavorite.value ? 'context_menu.remove_from_favorites' : 'context_menu.add_to_favorites'))

async function toggleFavorite() {
  const trackId = store.currentTrack?.id
  if (!trackId || isTogglingFavorite.value) return
  isTogglingFavorite.value = true
  stopFavoriteTransition()
  animateFavorite.value = true
  try {
    await favoritesStore.toggle(trackId)
    if (animateFavorite.value) {
      favoriteTransitionTimer = setTimeout(stopFavoriteTransition, 300)
    }
  } catch (error) {
    stopFavoriteTransition()
    console.error('Failed to toggle favorite:', error)
  } finally {
    isTogglingFavorite.value = false
  }
}

</script>

<template>
  <button
    type="button"
    class="flex shrink-0 items-center justify-center rounded-full disabled:pointer-events-none focus-visible:outline-2 duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]"
    :class="[
      { 'opacity-30': !store.currentTrack },
      large ? 'h-9 w-9' : 'h-7 w-7',
      mini
        ? 'focus-visible:outline-mini-player-pill-foreground/70'
        : 'hover:bg-foreground/[0.04] focus-visible:outline-foreground/70',
      isFavorite
        ? (mini ? 'text-mini-player-pill-foreground/90' : 'text-foreground/70')
        : (mini ? 'text-mini-player-pill-foreground/60' : 'text-foreground/40 hover:text-foreground/70'),
      animateFavorite ? 'transition-colors' : 'transition-none',
    ]"
    :aria-label="favoriteLabel"
    :aria-pressed="isFavorite"
    :disabled="!store.currentTrack || isTogglingFavorite"
    @click="toggleFavorite"
  >
    <Star :class="[large ? 'h-5 w-5' : 'h-4 w-4', { 'fill-current': isFavorite }]" />
  </button>
</template>
