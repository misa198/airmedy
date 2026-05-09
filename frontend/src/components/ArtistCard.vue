<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
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
const imageUrl = ref<string | null>(null)
let offArtwork: (() => void) | null = null

const loadArtwork = async () => {
  if (!appStore.useOnlineArtistArtwork || imageUrl.value) return

  const eventId = `artist-artwork:${props.artist.id}`
  try {
    const url = await LibraryService.GetArtistArtwork(props.artist.id, eventId)
    if (url) {
      imageUrl.value = url
    } else {
      // Wait for event
      if (!offArtwork) {
        offArtwork = Events.On(eventId, (ev) => {
          imageUrl.value = ev.data as string
          if (offArtwork) {
            offArtwork()
            offArtwork = null
          }
        })
      }
    }
  } catch (err) {
    console.error(`[ArtistCard] Failed to get artist artwork for ${props.artist.name}:`, err)
  }
}

watch(() => props.artist.id, () => {
  if (offArtwork) {
    offArtwork()
    offArtwork = null
  }
  imageUrl.value = null
  loadArtwork()
})

watch(() => appStore.useOnlineArtistArtwork, (enabled) => {
  if (enabled) {
    loadArtwork()
  } else {
    imageUrl.value = null
  }
})

onMounted(() => {
  loadArtwork()
})

onUnmounted(() => {
  if (offArtwork) {
    offArtwork()
  }
})
</script>

<template>
  <!-- Avatar only variant -->
  <div v-if="variant === 'avatar'" class="w-full h-full flex items-center justify-center">
    <div v-if="imageUrl && appStore.useOnlineArtistArtwork" class="w-full h-full">
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
      <div v-if="imageUrl && appStore.useOnlineArtistArtwork" class="w-full h-full">
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
