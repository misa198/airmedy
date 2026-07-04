<script setup lang="ts">
import { ref, nextTick, onBeforeUnmount } from 'vue'

const props = withDefaults(defineProps<{
  panelWidth?: number
  panelOffsetY?: number
}>(), { panelWidth: 256, panelOffsetY: 0 })

const open = ref(false)
const triggerRef = ref<HTMLElement | null>(null)
const panelX = ref(0)
const panelY = ref(0)

function updatePosition() {
  if (!triggerRef.value) return
  const rect = triggerRef.value.getBoundingClientRect()
  panelX.value = rect.right - props.panelWidth
  panelY.value = rect.bottom + props.panelOffsetY
}

function toggle() {
  open.value = !open.value
  if (open.value) {
    updatePosition()
    nextTick(() => {
      document.addEventListener('click', close, { once: true })
      document.addEventListener('mousedown', onMouseDown, { capture: true })
    })
    window.addEventListener('resize', updatePosition)
  } else {
    window.removeEventListener('resize', updatePosition)
  }
}

function onMouseDown(e: MouseEvent) {
  // right-click: close immediately (contextmenu stopPropagation won't block this)
  if (e.button === 2) {
    close()
    document.removeEventListener('click', close)
  }
}

function close() {
  open.value = false
  document.removeEventListener('mousedown', onMouseDown, { capture: true })
  window.removeEventListener('resize', updatePosition)
}

onBeforeUnmount(() => {
  document.removeEventListener('click', close)
  document.removeEventListener('mousedown', onMouseDown, { capture: true })
  window.removeEventListener('resize', updatePosition)
})

defineExpose({ close })
</script>

<template>
  <div ref="triggerRef" class="inline-flex" @click.stop="toggle">
    <slot name="trigger" :open="open" />
  </div>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed z-[999] bg-glass-elevated backdrop-blur-xl ring-1 ring-border-glass rounded-2xl shadow-2xl p-3 transform-gpu isolate"
      :style="{ left: panelX + 'px', top: panelY + 'px', width: props.panelWidth + 'px' }"
      @click.stop
    >
      <slot />
    </div>
  </Teleport>
</template>
