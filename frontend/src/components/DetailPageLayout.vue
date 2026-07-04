<script setup lang="ts">
import { useRestoreScroll } from '@/composables/useRestoreScroll'
import DetailHero from '@/components/DetailHero.vue'
import type { ThemeColors } from '../../bindings/airmedy/internal/domain/models'

withDefaults(defineProps<{
  loading?: boolean
  theme?: ThemeColors | null
  title?: string
  heroOffset?: number
  bodyClass?: string
}>(), {
  loading: false,
  heroOffset: 390,
  bodyClass: '',
})

defineEmits<{
  'hero-contextmenu': [e: MouseEvent]
}>()

const { scrollContainerRef, handleScroll } = useRestoreScroll()

defineExpose({ scrollContainerRef })
</script>

<template>
  <div class="h-full flex flex-col bg-background overflow-hidden">
    <div v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
    </div>

    <div v-else ref="scrollContainerRef" class="flex-1 overflow-y-auto" @scroll.passive="handleScroll">
      <DetailHero :theme="theme" :title="title" @contextmenu.prevent="$emit('hero-contextmenu', $event)">
        <template v-if="$slots['top-right']" #top-right>
          <slot name="top-right" />
        </template>
        <template v-if="$slots.title" #title>
          <slot name="title" />
        </template>
        <template v-if="$slots.artwork" #artwork>
          <slot name="artwork" />
        </template>
        <template v-if="$slots.metadata" #metadata>
          <slot name="metadata" />
        </template>
        <template v-if="$slots.actions" #actions>
          <slot name="actions" />
        </template>
      </DetailHero>

      <div class="top-0" :class="bodyClass" :style="{ height: `calc(100vh - ${heroOffset}px)` }">
        <slot name="body" />
      </div>

      <slot name="footer" />
    </div>
  </div>
</template>
