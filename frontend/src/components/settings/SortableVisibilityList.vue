<script setup lang="ts">
import { onUnmounted } from 'vue'
import type { Component } from 'vue'
import { GripVertical } from '@lucide/vue'
import { Checkbox } from '@airmedy/ui'
import VirtualList from 'vue-virtual-sortable'

export interface VisibilityItem {
  id: string
  visible: boolean
}

defineProps<{
  items: VisibilityItem[]
  options: Record<string, { label: string; icon: Component }>
  hiddenVisibilityIds?: string[]
  disabled?: boolean
  reorderLabel: (label: string) => string
}>()

const emit = defineEmits<{
  'update:items': [items: VisibilityItem[]]
}>()

function endHold() {
  document.documentElement.classList.remove('sortable-visibility-list-dragging')
  window.removeEventListener('mouseup', endHold, true)
  window.removeEventListener('blur', endHold)
}

function startHold(disabled: boolean | undefined) {
  if (disabled) return
  document.documentElement.classList.add('sortable-visibility-list-dragging')
  window.addEventListener('mouseup', endHold, true)
  window.addEventListener('blur', endHold)
}

function moveItem(items: VisibilityItem[], index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= items.length) return
  const next = [...items]
  const [item] = next.splice(index, 1)
  next.splice(target, 0, item!)
  emit('update:items', next)
}

onUnmounted(endHold)
</script>

<template>
  <VirtualList :model-value="items" data-key="id" :size="56" :keeps="items.length"
    handle=".sortable-visibility-list-handle" chosen-class="sortable-visibility-list-held" :ghost-style="{ display: 'none' }"
    :disabled="disabled" :auto-scroll="false" :animation="150" :class="items.length === 5 ? 'h-[280px]' : 'h-[392px]'"
    @update:model-value="items => emit('update:items', items)">
    <template #item="{ record: item, index }">
      <div class="flex h-14 items-center gap-3 rounded-xl pl-2 pr-3 transition-colors duration-300 ease-[cubic-bezier(0.4,0,0.2,1)] select-none hover:bg-foreground/[0.04]"
        :data-action="item.id">
        <button type="button" class="sortable-visibility-list-handle cursor-grab text-foreground/40 hover:text-foreground/70 focus-visible:outline focus-visible:outline-foreground"
          :disabled="disabled" :aria-label="reorderLabel(options[item.id].label)"
          @mousedown.left="startHold(disabled)"
          @keydown.up.prevent="moveItem(items, index, -1)" @keydown.down.prevent="moveItem(items, index, 1)">
          <GripVertical class="size-4" aria-hidden="true" />
        </button>
        <component :is="options[item.id].icon" class="size-4 text-foreground/70" aria-hidden="true" />
        <span class="flex-1 text-sm text-foreground">{{ options[item.id].label }}</span>
        <Checkbox v-if="!hiddenVisibilityIds?.includes(item.id)" class="opacity-100!" style="cursor: pointer" :checked="item.visible" :disabled="Boolean(disabled)"
          role="checkbox" :tabindex="disabled ? -1 : 0"
          :aria-checked="item.visible" :aria-disabled="Boolean(disabled)"
          :aria-label="options[item.id].label"
          @update:checked="visible => emit('update:items', items.map(current => current.id === item.id ? { ...current, visible } : current))"
          @keydown.space.prevent.stop="!disabled && emit('update:items', items.map(current => current.id === item.id ? { ...current, visible: !current.visible } : current))" />
      </div>
    </template>
  </VirtualList>
</template>

<style scoped>
:global(.sortable-visibility-list-dragging),
:global(.sortable-visibility-list-dragging *) {
  cursor: grabbing !important;
}
:deep(.sortable-visibility-list-held) {
  background-color: color-mix(in srgb, var(--text-main) 8%, transparent);
}
</style>
