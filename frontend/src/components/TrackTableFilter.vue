<script setup lang="ts">
import { ListFilter, ArrowUp, ArrowDown } from '@lucide/vue'
import { Checkbox, IconButton } from '@airmedy/ui'
import { COLUMNS, type ColumnDef, type ColumnKey, useTrackTableSettings } from '@/composables/useTrackTableSettings'
import FilterDropdown from './FilterDropdown.vue'
import FilterSubmenu from './FilterSubmenu.vue'
import { useHoverSubmenuGroup } from '@/composables/useHoverSubmenuGroup'

const props = defineProps<{
  simpleMode?: boolean
  optionalColumns: ColumnDef[]
  sortColumn?: ColumnKey | null
  sortDir?: 'asc' | 'desc' | null
}>()

const emit = defineEmits<{
  'select-sort': [ColumnKey]
}>()

const settings = useTrackTableSettings()
const sortableColumns = COLUMNS.filter((c) => c.sortable)
const submenuGroup = useHoverSubmenuGroup()
</script>

<template>
  <template v-if="!simpleMode">
    <FilterDropdown>
      <template #trigger="{ open }">
        <IconButton variant="outlined" :active="open">
          <ListFilter class="w-3.5 h-3.5" />
        </IconButton>
      </template>

      <FilterSubmenu :label="$t('library.sort_by')" :group="submenuGroup">
        <div
          v-for="col in sortableColumns"
          :key="col.key"
          class="flex items-center gap-2.5 px-1.5 py-1.5 rounded-lg hover:bg-foreground/[0.06] cursor-pointer transition-colors"
          :class="{ 'bg-foreground/[0.08]': sortColumn === col.key }"
          @click="emit('select-sort', col.key)"
        >
          <ArrowUp v-if="sortColumn === col.key && sortDir === 'asc'" class="w-4 h-4 text-foreground/70" />
          <ArrowDown v-else-if="sortColumn === col.key && sortDir === 'desc'" class="w-4 h-4 text-foreground/70" />
          <span v-else class="w-4 h-4 inline-block" />
          <span class="text-sm text-foreground opacity-90">{{ $t(col.labelKey) }}</span>
          <div v-if="sortColumn === col.key" class="ml-auto w-1.5 h-1.5 rounded-full bg-primary" />
        </div>
      </FilterSubmenu>

      <div class="mt-1">
        <FilterSubmenu :label="$t('library.columns')" :group="submenuGroup" :panel-width="180">
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
        </FilterSubmenu>
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
    </FilterDropdown>
  </template>
</template>
