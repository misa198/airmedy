<script setup lang="ts">
import { ArrowUp, ArrowDown, ArrowUpDown } from 'lucide-vue-next'

defineProps<{
  active: boolean
  dir: 'asc' | 'desc' | null
}>()
</script>

<template>
  <ArrowUp v-if="active && dir === 'asc'" class="w-3 h-3 flex-shrink-0 pointer-events-none" />
  <ArrowDown v-else-if="active && dir === 'desc'" class="w-3 h-3 flex-shrink-0 pointer-events-none" />
  <ArrowUpDown v-else class="w-3 h-3 flex-shrink-0 opacity-40 pointer-events-none" />
</template>
