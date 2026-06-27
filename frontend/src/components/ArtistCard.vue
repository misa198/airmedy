<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { User } from 'lucide-vue-next'
import type { Artist } from '../../bindings/airmedy/internal/domain/models'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import { Events } from '@wailsio/runtime'
import { useAppStore } from '@/stores/app'

const props = defineProps<{
  artist: Artist
  variant?: 'card' | 'avatar'
}>()

const emit = defineEmits<{
  'click': [id: string]
}>()

const appStore = useAppStore()

// All stored artwork keys are kept; which one shows is resolved client-side so
// flipping the "prefer local" setting swaps instantly without any round-trip.
const keys = reactive({
  manual: props.artist.artwork_key_manual as string | null,
  local: props.artist.artwork_key_local as string | null,
  online: props.artist.artwork_key_online as string | null,
})

// Online may be shown only when enabled and not suppressed by prefer-local (a
// local image then wins). Online off → never fall back to it, even if cached.
const canShowOnline = computed(() =>
  appStore.useOnlineArtistArtwork && !(appStore.preferLocalArtistArtwork && keys.local)
)

const resolvedKey = computed(() => {
  if (keys.manual) return keys.manual
  return canShowOnline.value ? (keys.online || keys.local) : keys.local
})

const imageUrl = ref<string | null>(null)
let offGlobal: (() => void) | null = null

// Keep imageUrl in sync with the resolved key (instant on preference/key change).
watch(resolvedKey, (key) => {
  imageUrl.value = key ? `/artwork/${key}` : null
}, { immediate: true })

const seedKeys = () => {
  keys.manual = props.artist.artwork_key_manual as string | null
  keys.local = props.artist.artwork_key_local as string | null
  keys.online = props.artist.artwork_key_online as string | null
}

// Fetch the Deezer image only when it would actually be shown (online enabled, no
// manual, and not suppressed by a local image under prefer-local) and none cached.
const maybeFetchOnline = () => {
  if (!canShowOnline.value || keys.manual || keys.online) return
  LibraryService.GetArtistArtwork(props.artist.id, `artist-artwork:${props.artist.id}`)
    .catch((err) => console.error(`[ArtistCard] artwork fetch failed for ${props.artist.name}:`, err))
}

// Backend emits this whenever a single source's key changes (empty = cleared).
const subscribeGlobal = () => {
  if (offGlobal) return
  offGlobal = Events.On('artist-artwork-updated', (ev) => {
    const data = ev.data as { artist_id?: string; source?: string; key?: string } | undefined
    if (!data || data.artist_id !== props.artist.id) return
    const value = data.key ? data.key : null
    if (data.source === 'manual') keys.manual = value
    else if (data.source === 'local_file') keys.local = value
    else if (data.source === 'online') keys.online = value
  })
}

watch(() => props.artist.id, () => {
  seedKeys()
  maybeFetchOnline()
})

watch(() => [appStore.useOnlineArtistArtwork, appStore.preferLocalArtistArtwork], () => {
  // Re-attempt a fetch when the toggles change such that online is now wanted.
  maybeFetchOnline()
})

onMounted(() => {
  subscribeGlobal()
  maybeFetchOnline()
})

onUnmounted(() => {
  if (offGlobal) offGlobal()
})
</script>

<template>
  <!-- Avatar only variant -->
  <div v-if="variant === 'avatar'" class="w-full h-full flex items-center justify-center">
    <div v-if="imageUrl" class="w-full h-full">
      <img 
        :src="imageUrl" 
        class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110"
        @error="imageUrl = null"
      />
    </div>
    <div v-else class="w-full h-full flex items-center justify-center text-foreground opacity-40 group-hover:bg-foreground/10 transition-colors">
      <User class="w-1/2 h-1/2" />
    </div>
  </div>

  <!-- Full card variant (default) -->
  <div 
    v-else
    class="group cursor-pointer text-center"
    @click="emit('click', artist.id)"
  >
    <div class="aspect-square bg-foreground/5 rounded-full ring-1 ring-foreground/[0.06] overflow-hidden relative mb-3 transition-all flex items-center justify-center">
      <div v-if="imageUrl" class="w-full h-full">
        <img
          :src="imageUrl"
          class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110"
          @error="imageUrl = null"
        />
      </div>
      <div v-else class="w-full h-full flex items-center justify-center text-foreground opacity-40 group-hover:bg-foreground/10 transition-colors">
        <User class="w-1/2 h-1/2" />
      </div>
    </div>

    <div class="space-y-1 px-1">
      <h3 class="font-medium text-sm truncate group-hover:text-foreground transition-colors">{{ artist.name || $t('library.unknown_artist') }}</h3>
      <p class="text-xs text-foreground opacity-60">{{ $t('library.artist') }}</p>
    </div>
  </div>
</template>
