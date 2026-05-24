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
const panelX = ref(0)
const panelY = ref(0)

async function updatePosition() {
  if (!filterBtnRef.value) return
  const rect = filterBtnRef.value.getBoundingClientRect()
  // Align right edge of panel with right edge of button, with a small offset from the scrollbar
  panelX.value = rect.right - 256 // 256 is the w-64 width
  panelY.value = rect.bottom
}

function toggleFilter(e: MouseEvent) {
  e.stopPropagation()
  filterOpen.value = !filterOpen.value
  if (filterOpen.value) {
    updatePosition()
    nextTick(() => document.addEventListener('click', closeFilter, { once: true }))
    window.addEventListener('resize', updatePosition)
  } else {
    window.removeEventListener('resize', updatePosition)
  }
}

function closeFilter() {
  filterOpen.value = false
  window.removeEventListener('resize', updatePosition)
}

onBeforeUnmount(() => {
  document.removeEventListener('click', closeFilter)
  window.removeEventListener('resize', updatePosition)
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
    <Teleport to="body">
      <div
        v-if="filterOpen"
        class="fixed z-[999] w-64 bg-background/95 backdrop-blur-xl ring-1 ring-foreground/10 rounded-2xl shadow-2xl p-3 transform-gpu isolate"
        :style="{ left: panelX + 'px', top: panelY + 'px' }"
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

        <div class="mt-2 pt-2 border-t border-foreground/10">
          <p class="text-[10px] font-semibold text-foreground opacity-60 uppercase tracking-widest px-1 mb-2">
            {{ $t('settings.appearance.title') }}
          </p>
          <div
            class="flex items-center gap-2.5 px-1.5 py-1.5 rounded-lg hover:bg-foreground/[0.06] cursor-pointer transition-colors"
            @click="settings.toggleCollapsedMode()"
          >
            <Checkbox
              :checked="settings.collapsedMode.value"
              variant="contained"
              @update:checked="settings.toggleCollapsedMode()"
            />
            <span class="text-sm text-foreground opacity-90">{{ $t('library.compact_mode') }}</span>
          </div>
        </div>
      </div>
    </Teleport>
  </template>
</template>
