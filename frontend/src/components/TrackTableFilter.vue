<script setup lang="ts">
import { ListFilter } from '@lucide/vue'
import { Checkbox, IconButton } from '@airmedy/ui'
import { type ColumnDef, useTrackTableSettings } from '@/composables/useTrackTableSettings'
import FilterDropdown from './FilterDropdown.vue'

const props = defineProps<{
  simpleMode?: boolean
  optionalColumns: ColumnDef[]
}>()

const settings = useTrackTableSettings()
</script>

<template>
  <template v-if="!simpleMode">
    <FilterDropdown>
      <template #trigger="{ open }">
        <IconButton variant="outlined" :active="open">
          <ListFilter class="w-3.5 h-3.5" />
        </IconButton>
      </template>

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
    </FilterDropdown>
  </template>
</template>
