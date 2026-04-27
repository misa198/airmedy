<script setup lang="ts">
import { ref, nextTick, onBeforeUnmount } from 'vue'
import { SlidersHorizontal } from 'lucide-vue-next'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import { type ColumnDef, useTrackTableSettings } from '@/composables/useTrackTableSettings'

const props = defineProps<{
  simpleMode?: boolean
  optionalColumns: ColumnDef[]
}>()

const settings = useTrackTableSettings()
const filterOpen = ref(false)
const filterBtnRef = ref<HTMLElement | null>(null)

function toggleFilter(e: MouseEvent) {
  e.stopPropagation()
  filterOpen.value = !filterOpen.value
  if (filterOpen.value) {
    nextTick(() => document.addEventListener('click', closeFilter, { once: true }))
  }
}

function closeFilter() {
  filterOpen.value = false
}

onBeforeUnmount(() => {
  document.removeEventListener('click', closeFilter)
})
</script>

<template>
  <template v-if="!simpleMode">
    <button
      ref="filterBtnRef"
      class="absolute right-0 top-0 z-30 h-[40px] w-[48px] flex items-center justify-center text-foreground opacity-60 hover:text-foreground opacity-80 transition-colors"
      :class="{ 'text-primary! hover:text-primary!': filterOpen }"
      @click="toggleFilter"
    >
      <SlidersHorizontal class="w-3.5 h-3.5" />
    </button>

    <!-- Filter panel -->
    <div
      v-show="filterOpen"
      class="absolute right-0 top-[40px] z-30 w-64 bg-background/80 backdrop-blur-xl ring-1 ring-foreground/10 rounded-2xl shadow-2xl p-3"
      @click.stop
    >
      <p class="text-[10px] font-semibold text-foreground opacity-60 uppercase tracking-widest px-1 mb-2">
        {{ $t('library.columns') }}
      </p>
      <div class="flex flex-col">
        <div
          v-for="col in optionalColumns"
          :key="col.key"
          class="flex items-center gap-2.5 px-1.5 py-1.5 rounded-lg hover:bg-foreground/[0.06] cursor-pointer transition-colors"
          @click="settings.toggleColumn(col.key)"
        >
          <Checkbox
            :checked="settings.visibleColumns.value.includes(col.key)"
            variant="contained"
            @update:checked="settings.toggleColumn(col.key)"
          />
          <span class="text-sm text-foreground opacity-90">{{ $t(col.labelKey) }}</span>
        </div>
      </div>
    </div>
  </template>
</template>
