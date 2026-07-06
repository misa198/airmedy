<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import { ChevronRight } from '@lucide/vue'
import type { useHoverSubmenuGroup } from '@/composables/useHoverSubmenuGroup'

const props = withDefaults(defineProps<{
  label: string
  group: ReturnType<typeof useHoverSubmenuGroup>
  disabled?: boolean
  panelWidth?: number
}>(), { disabled: false, panelWidth: 160 })

const key = Symbol()
const open = computed(() => props.group.activeKey.value === key)

const triggerRef = ref<HTMLElement | null>(null)
const panelX = ref(0)
const panelY = ref(0)

function updatePosition() {
  if (!triggerRef.value) return
  const rect = triggerRef.value.getBoundingClientRect()
  panelX.value = rect.right
  panelY.value = rect.top
}

watch(open, (isOpen) => {
  if (isOpen) {
    updatePosition()
    nextTick(updatePosition)
  }
})

function onEnter() {
  if (!props.disabled) props.group.enter(key)
}

function onLeave() {
  props.group.leave(key)
}
</script>

<template>
  <div
    ref="triggerRef"
    class="relative"
    @mouseenter="onEnter"
    @mouseleave="onLeave"
  >
    <div
      class="flex items-center gap-2.5 px-1.5 py-1.5 rounded-lg transition-colors"
      :class="disabled ? 'opacity-40 cursor-not-allowed' : 'hover:bg-foreground/[0.06] cursor-pointer'"
    >
      <span class="text-sm text-foreground opacity-90">{{ label }}</span>
      <ChevronRight class="w-3.5 h-3.5 text-foreground/50 ml-auto" />
    </div>

    <!-- Zero-width bridge: keeps the hover region contiguous across the gap -->
    <div v-if="open" class="absolute left-full top-0 bottom-0 w-2" />

    <Teleport to="body">
      <div
        v-if="open"
        class="fixed z-[999] flex flex-col gap-0.5 max-h-72 overflow-y-auto rounded-2xl bg-glass-elevated backdrop-blur-xl ring-1 ring-border-glass shadow-2xl p-1.5 transform-gpu isolate"
        :style="{ left: panelX + 'px', top: panelY - 1 + 'px', minWidth: panelWidth + 'px' }"
        @mouseenter="onEnter"
        @mouseleave="onLeave"
      >
        <slot />
      </div>
    </Teleport>
  </div>
</template>
