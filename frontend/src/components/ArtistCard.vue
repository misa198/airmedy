<script setup lang="ts">
import { User } from 'lucide-vue-next'
import type { Artist } from '../../bindings/airmedy/internal/domain/models'

defineProps<{
  artist: Artist
}>()

const emit = defineEmits<{
  'click': [id: string]
}>()
</script>

<template>
  <div 
    class="group cursor-pointer text-center"
    @click="emit('click', artist.id)"
  >
    <div class="aspect-square bg-foreground/5 rounded-full ring-1 ring-foreground/[0.06] overflow-hidden relative mb-3 transition-all flex items-center justify-center">
      <div v-if="false" class="w-full h-full">
        <!-- Artists might have images in the future -->
      </div>
      <div v-else class="w-full h-full flex items-center justify-center text-foreground/20 group-hover:bg-foreground/10 transition-colors">
        <User class="w-1/2 h-1/2" />
      </div>
    </div>

    <div class="space-y-1 px-1">
      <h3 class="font-medium text-sm truncate group-hover:text-foreground transition-colors">{{ artist.name || $t('library.unknown_artist') }}</h3>
      <p class="text-xs text-foreground/40">{{ $t('library.artist') }}</p>
    </div>
  </div>
</template>
