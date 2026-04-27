<script setup lang="ts">
import { PenTool } from 'lucide-vue-next'
import type { Composer } from '../../bindings/airmedy/internal/domain/models'

defineProps<{
  composer: Composer
}>()

const emit = defineEmits<{
  'click': [id: string]
}>()
</script>

<template>
  <div 
    class="group cursor-pointer"
    @click="emit('click', composer.id)"
  >
    <div class="aspect-square bg-foreground/5 rounded-lg ring-1 ring-foreground/[0.06] overflow-hidden relative mb-3 transition-all flex items-center justify-center">
      <div v-if="false" class="w-full h-full">
      </div>
      <div v-else class="w-full h-full flex items-center justify-center text-foreground opacity-40 group-hover:bg-foreground/10 transition-colors">
        <PenTool class="w-1/3 h-1/3" />
      </div>
    </div>

    <div class="space-y-1 px-1">
      <h3 class="font-medium text-sm truncate group-hover:text-foreground transition-colors">{{ composer.name || $t('library.unknown_composer') }}</h3>
      <p class="text-xs text-foreground opacity-60">{{ $t('library.composer') }}</p>
    </div>
  </div>
</template>
