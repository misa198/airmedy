<script setup lang="ts">
import { Bug } from '@lucide/vue'
import { Window } from '@wailsio/runtime'
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const isDevelopment = import.meta.env.DEV
const position = ref({ x: 16, y: 16 })

const BUTTON_SIZE = 40
let pointerStart: { x: number, y: number, buttonX: number, buttonY: number } | null = null
let dragged = false

onMounted(() => {
  position.value = {
    x: Math.max(0, window.innerWidth - BUTTON_SIZE - 16),
    y: Math.max(0, window.innerHeight - BUTTON_SIZE - 16),
  }
})

function onPointerDown(event: PointerEvent) {
  if (event.button !== 0) return
  pointerStart = {
    x: event.clientX,
    y: event.clientY,
    buttonX: position.value.x,
    buttonY: position.value.y,
  }
  dragged = false
  const target = event.currentTarget as HTMLElement
  target.setPointerCapture?.(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (!pointerStart) return
  const x = pointerStart.buttonX + event.clientX - pointerStart.x
  const y = pointerStart.buttonY + event.clientY - pointerStart.y
  position.value = {
    x: Math.min(Math.max(0, x), Math.max(0, window.innerWidth - BUTTON_SIZE)),
    y: Math.min(Math.max(0, y), Math.max(0, window.innerHeight - BUTTON_SIZE)),
  }
  dragged ||= Math.abs(event.clientX - pointerStart.x) > 3 || Math.abs(event.clientY - pointerStart.y) > 3
}

function onPointerUp(event: PointerEvent) {
  pointerStart = null
  const target = event.currentTarget as HTMLElement
  target.releasePointerCapture?.(event.pointerId)
}

async function openDevTools() {
  if (dragged) {
    dragged = false
    return
  }
  try {
    await Window.OpenDevTools()
  } catch (error) {
    console.error('Failed to open developer tools:', error)
  }
}
</script>

<template>
  <button
    v-if="isDevelopment"
    type="button"
    class="fixed z-[200] flex h-10 w-10 touch-none select-none items-center justify-center rounded-xl border border-[var(--border-glass)] bg-red-600 text-white shadow-[0_10px_15px_-3px_rgba(0,0,0,0.4)] backdrop-blur-[30px] transition-[color] duration-300"
    :style="{ left: `${position.x}px`, top: `${position.y}px` }"
    :aria-label="t('common.open_devtools')"
    :title="t('common.open_devtools')"
    @dragstart.prevent
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
    @click="openDevTools"
  >
    <Bug class="h-4 w-4" />
  </button>
</template>
