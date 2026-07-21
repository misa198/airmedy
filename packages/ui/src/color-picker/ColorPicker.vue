<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { Check } from '@lucide/vue'
import { Input } from '../input'
import { Slider } from '../slider'

const props = withDefaults(defineProps<{
  modelValue: string
  presets?: string[]
  ariaLabel: string
  hexLabel: string
}>(), {
  presets: () => [],
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const root = ref<HTMLElement>()
const popover = ref<HTMLElement>()
const isOpen = ref(false)
const popoverStyle = ref<Record<string, string>>({ visibility: 'hidden' })
const hexValue = ref(props.modelValue.toUpperCase())
const hue = ref(348)
const saturation = ref(87)
const brightness = ref(50)
let dragCleanup: (() => void) | undefined

function rgbToHsv(color: string) {
  const value = /^#[0-9a-fA-F]{6}$/.test(color) ? color : '#E11D48'
  const red = parseInt(value.slice(1, 3), 16) / 255
  const green = parseInt(value.slice(3, 5), 16) / 255
  const blue = parseInt(value.slice(5, 7), 16) / 255
  const max = Math.max(red, green, blue)
  const min = Math.min(red, green, blue)
  const delta = max - min
  let nextHue = 0
  if (delta) {
    if (max === red) nextHue = 60 * (((green - blue) / delta) % 6)
    else if (max === green) nextHue = 60 * ((blue - red) / delta + 2)
    else nextHue = 60 * ((red - green) / delta + 4)
  }
  return { hue: Math.round((nextHue + 360) % 360), saturation: max ? Math.round(delta / max * 100) : 0, brightness: Math.round(max * 100) }
}

function hsvToHex(nextHue: number, nextSaturation: number, nextBrightness: number) {
  const chroma = nextBrightness / 100 * nextSaturation / 100
  const segment = nextHue / 60
  const x = chroma * (1 - Math.abs(segment % 2 - 1))
  const [r, g, b] = segment < 1 ? [chroma, x, 0] : segment < 2 ? [x, chroma, 0] : segment < 3 ? [0, chroma, x] : segment < 4 ? [0, x, chroma] : segment < 5 ? [x, 0, chroma] : [chroma, 0, x]
  const match = nextBrightness / 100 - chroma
  return `#${[r, g, b].map(channel => Math.round((channel + match) * 255).toString(16).padStart(2, '0')).join('').toUpperCase()}`
}

function syncFromColor(color: string) {
  const next = rgbToHsv(color)
  hue.value = next.hue
  saturation.value = next.saturation
  brightness.value = next.brightness
  hexValue.value = color.toUpperCase()
}

watch(() => props.modelValue, syncFromColor, { immediate: true })

const hueColor = computed(() => `hsl(${hue.value} 100% 50%)`)
const color = computed(() => hsvToHex(hue.value, saturation.value, brightness.value))
const saturationStyle = computed(() => ({ background: `linear-gradient(to top, #000, transparent), linear-gradient(to right, #fff, ${hueColor.value})` }))
const isCustomColor = computed(() => !props.presets.includes(props.modelValue.toUpperCase()))

function emitColor() {
  hexValue.value = color.value
  emit('update:modelValue', color.value)
}

function updateHex(value: string | number) {
  const next = String(value).toUpperCase()
  hexValue.value = next
  if (/^#[0-9A-F]{6}$/.test(next)) {
    syncFromColor(next)
    emit('update:modelValue', next)
  }
}

function selectPreset(value: string) {
  syncFromColor(value)
  emit('update:modelValue', value)
}

function updatePosition(event: PointerEvent, element: HTMLElement) {
  const rect = element.getBoundingClientRect()
  saturation.value = Math.round(Math.min(100, Math.max(0, (event.clientX - rect.left) / rect.width * 100)))
  brightness.value = Math.round(Math.min(100, Math.max(0, (1 - (event.clientY - rect.top) / rect.height) * 100)))
  emitColor()
}

function startDrag(event: PointerEvent) {
  const target = event.currentTarget as HTMLElement
  target.setPointerCapture(event.pointerId)
  updatePosition(event, target)
  const onMove = (move: PointerEvent) => updatePosition(move, target)
  const stop = () => {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', stop)
    dragCleanup = undefined
  }
  dragCleanup?.()
  dragCleanup = stop
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', stop, { once: true })
}

function closeOnOutside(event: MouseEvent) {
  const target = event.target as Node
  if (!root.value?.contains(target) && !popover.value?.contains(target)) isOpen.value = false
}

function positionPopover() {
  if (!root.value || !popover.value) return

  const trigger = root.value.getBoundingClientRect()
  const menu = popover.value.getBoundingClientRect()
  const margin = 12
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const spaceAbove = trigger.top
  const spaceBelow = viewportHeight - trigger.bottom
  const maxTop = Math.max(margin, viewportHeight - menu.height - margin)
  const maxLeft = Math.max(margin, viewportWidth - menu.width - margin)

  // Prefer opening below/right, but use the side with more room when either
  // edge would otherwise clip the menu. Clamp the result against all edges.
  const top = spaceBelow >= menu.height + margin || spaceBelow >= spaceAbove
    ? trigger.bottom + margin
    : trigger.top - menu.height - margin
  const left = trigger.right - menu.width >= margin
    ? trigger.right - menu.width
    : trigger.left

  popoverStyle.value = {
    top: `${Math.min(Math.max(margin, top), maxTop)}px`,
    left: `${Math.min(Math.max(margin, left), maxLeft)}px`,
    visibility: 'visible',
  }
}

watch(isOpen, async open => {
  if (open) {
    await nextTick()
    document.addEventListener('mousedown', closeOnOutside)
    positionPopover()
    window.addEventListener('resize', positionPopover)
    window.addEventListener('scroll', positionPopover, true)
  } else {
    document.removeEventListener('mousedown', closeOnOutside)
    window.removeEventListener('resize', positionPopover)
    window.removeEventListener('scroll', positionPopover, true)
  }
})

onUnmounted(() => {
  document.removeEventListener('mousedown', closeOnOutside)
  window.removeEventListener('resize', positionPopover)
  window.removeEventListener('scroll', positionPopover, true)
  dragCleanup?.()
})
</script>

<template>
  <div ref="root" class="relative inline-flex">
    <button
      type="button"
      class="flex h-7 w-7 items-center justify-center rounded-full border-2 p-0.5 transition-all duration-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 focus-visible:ring-offset-2 focus-visible:ring-offset-transparent"
      :class="isCustomColor ? '' : 'border-transparent'"
      :style="isCustomColor ? { borderColor: modelValue } : undefined"
      :aria-label="ariaLabel"
      :aria-expanded="isOpen"
      @click="isOpen = !isOpen"
    >
      <span class="h-5 w-5 rounded-full" :style="{ background: 'conic-gradient(#f43f5e, #f59e0b, #eab308, #22c55e, #06b6d4, #3b82f6, #8b5cf6, #ec4899, #f43f5e)' }" />
    </button>

    <Teleport to="body">
      <div ref="popover" v-if="isOpen" class="fixed z-[1000] w-72 rounded-xl border border-glass bg-glass-modal p-4 shadow-[0_10px_15px_-3px_rgba(0,0,0,0.4)] backdrop-blur-[30px]" :style="popoverStyle">
        <div
          class="relative aspect-square cursor-crosshair"
          :style="saturationStyle"
          @pointerdown.prevent="startDrag"
        >
          <span class="pointer-events-none absolute h-3 w-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow" :style="{ left: `${saturation}%`, top: `${100 - brightness}%` }" />
        </div>
        <Slider
          :model-value="hue"
          :min="0"
          :max="360"
          :step="1"
          class="mt-4"
          always-show-thumb
          track-background="linear-gradient(to right, #ef4444, #facc15, #22c55e, #06b6d4, #3b82f6, #a855f7, #ec4899, #ef4444)"
          track-color-class="bg-transparent"
          @update:model-value="value => { hue = value; emitColor() }"
        />
        <div class="mt-4 flex items-center gap-3">
          <span class="h-9 w-9 shrink-0 rounded-lg border border-white/20" :style="{ backgroundColor: color }" />
          <Input :model-value="hexValue" :aria-label="hexLabel" maxlength="7" class="h-9 rounded-lg px-2 font-mono uppercase" @update:model-value="updateHex" />
        </div>
        <div v-if="presets.length" class="mt-4 flex items-center gap-2">
          <button v-for="preset in presets" :key="preset" type="button" class="flex h-6 w-6 items-center justify-center rounded-full p-0.5 transition-all duration-300 hover:scale-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white" :style="{ backgroundColor: preset }" @click="selectPreset(preset)">
            <Check v-if="preset === modelValue" class="h-3.5 w-3.5" :class="preset === '#FBBF24' ? 'text-black' : 'text-white'" />
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>
