<script setup lang="ts">
import { LayoutGrid, List, ArrowUp, ArrowDown, ListFilter } from '@lucide/vue'
import { IconButton } from '@airmedy/ui'
import FilterDropdown from './FilterDropdown.vue'

type SortCol = 'title' | 'artist' | 'year' | null
type SortDir = 'asc' | 'desc'

const props = defineProps<{
  viewMode: 'grid' | 'list'
  sortColumn: SortCol
  sortDir: SortDir
}>()

const emit = defineEmits<{
  'update:viewMode': ['grid' | 'list']
  'update:sortColumn': [SortCol]
  'update:sortDir': [SortDir]
}>()

function selectSortCol(col: NonNullable<SortCol>) {
  if (props.sortColumn === col) {
    emit('update:sortDir', props.sortDir === 'asc' ? 'desc' : 'asc')
  } else {
    emit('update:sortColumn', col)
    emit('update:sortDir', 'asc')
  }
}
</script>

<template>
  <FilterDropdown :panel-offset-y="4">
    <template #trigger="{ open }">
      <IconButton variant="outlined" :active="open">
        <ListFilter class="w-3.5 h-3.5" />
      </IconButton>
    </template>

    <!-- Layout -->
    <p class="text-[10px] font-semibold text-foreground opacity-60 uppercase tracking-widest px-1 mb-2">
      {{ $t('library.layout') }}
    </p>
    <div class="flex flex-col gap-0.5">
      <div
        class="flex items-center gap-2.5 px-1.5 py-1.5 rounded-lg hover:bg-foreground/[0.06] cursor-pointer transition-colors"
        :class="{ 'bg-foreground/[0.08]': viewMode === 'grid' }"
        @click="emit('update:viewMode', 'grid')"
      >
        <LayoutGrid class="w-4 h-4 text-foreground/70" />
        <span class="text-sm text-foreground opacity-90">{{ $t('library.grid_view') }}</span>
        <div v-if="viewMode === 'grid'" class="ml-auto w-1.5 h-1.5 rounded-full bg-primary" />
      </div>
      <div
        class="flex items-center gap-2.5 px-1.5 py-1.5 rounded-lg hover:bg-foreground/[0.06] cursor-pointer transition-colors"
        :class="{ 'bg-foreground/[0.08]': viewMode === 'list' }"
        @click="emit('update:viewMode', 'list')"
      >
        <List class="w-4 h-4 text-foreground/70" />
        <span class="text-sm text-foreground opacity-90">{{ $t('library.list_view') }}</span>
        <div v-if="viewMode === 'list'" class="ml-auto w-1.5 h-1.5 rounded-full bg-primary" />
      </div>
    </div>

    <!-- Sort -->
    <div class="mt-2 pt-2 border-t border-foreground/10">
      <p class="text-[10px] font-semibold text-foreground opacity-60 uppercase tracking-widest px-1 mb-2">
        {{ $t('library.sort_by') }}
      </p>
      <div class="flex flex-col gap-0.5">
        <div
          v-for="col in (['title', 'artist', 'year'] as const)"
          :key="col"
          class="flex items-center gap-2.5 px-1.5 py-1.5 rounded-lg hover:bg-foreground/[0.06] cursor-pointer transition-colors"
          :class="{ 'bg-foreground/[0.08]': sortColumn === col }"
          @click="selectSortCol(col)"
        >
          <ArrowUp v-if="sortColumn === col && sortDir === 'asc'" class="w-4 h-4 text-foreground/70" />
          <ArrowDown v-else-if="sortColumn === col && sortDir === 'desc'" class="w-4 h-4 text-foreground/70" />
          <span v-else class="w-4 h-4 inline-block" />
          <span class="text-sm text-foreground opacity-90">
            {{ $t(`library.${col}`) }}
          </span>
          <div v-if="sortColumn === col" class="ml-auto w-1.5 h-1.5 rounded-full bg-primary" />
        </div>
      </div>
    </div>
  </FilterDropdown>
</template>
