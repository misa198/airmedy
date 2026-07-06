<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { MoodDensityGrid } from '../../bindings/airmedy/internal/domain/models'
import { isMoodBoxValid, type MoodBox } from '../lib/moodPlaylistFields'
import { useQuadrantBrush } from '../composables/useQuadrantBrush'

const props = defineProps<{
  grid: MoodDensityGrid | null
  modelValue: MoodBox
}>()

const emit = defineEmits<{ 'update:modelValue': [MoodBox] }>()

const { t } = useI18n()

const SIZE = 440

// Sequential single-hue (blue) ramp, light->dark, for continuous magnitude —
// per the dataviz convention: one hue only, never a rainbow. Zero-count
// cells are handled separately (rendered transparent, not this ramp's
// lightest step) so "no data" never reads as "low value."
const RAMP = ['#cde2fb', '#9ec5f4', '#6da7ec', '#3987e5', '#256abf', '#184f95', '#0d366b']

function hexToRgb(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

function rampColor(t: number): string {
  const clamped = Math.max(0, Math.min(1, t))
  const scaled = clamped * (RAMP.length - 1)
  const i = Math.min(RAMP.length - 2, Math.floor(scaled))
  const frac = scaled - i
  const [r0, g0, b0] = hexToRgb(RAMP[i])
  const [r1, g1, b1] = hexToRgb(RAMP[i + 1])
  const r = Math.round(r0 + (r1 - r0) * frac)
  const g = Math.round(g0 + (g1 - g0) * frac)
  const b = Math.round(b0 + (b1 - b0) * frac)
  return `rgb(${r}, ${g}, ${b})`
}

const gridSize = computed(() => props.grid?.grid_size ?? 0)
const cellSize = computed(() => (gridSize.value > 0 ? SIZE / gridSize.value : 0))
const maxCount = computed(() => {
  if (!props.grid) return 0
  let max = 0
  for (const col of props.grid.counts) {
    for (const n of col) max = Math.max(max, n)
  }
  return max
})

const cells = computed(() => {
  if (!props.grid || gridSize.value === 0) return []
  const result: { x: number; y: number; count: number }[] = []
  for (let x = 0; x < gridSize.value; x++) {
    for (let y = 0; y < gridSize.value; y++) {
      result.push({ x, y, count: props.grid.counts[x]?.[y] ?? 0 })
    }
  }
  return result
})

function cellFill(count: number): string {
  if (count === 0 || maxCount.value === 0) return 'transparent'
  return rampColor(count / maxCount.value)
}

// SVG y grows downward; energy should read bottom-to-top, so row y=0
// (lowest energy) renders at the bottom.
function cellRectY(y: number): number {
  return (gridSize.value - 1 - y) * cellSize.value
}

const brushGroup = ref<SVGGElement | null>(null)
const width = computed(() => SIZE)
const height = computed(() => SIZE)

const { box, setBox } = useQuadrantBrush(brushGroup, width, height, props.modelValue, (next) => {
  emit('update:modelValue', next)
})

const boxIsValid = computed(() => isMoodBoxValid(box.value))

// Dual-thumb range sliders for precision / non-drag access — edits here call
// setBox so the brush's own drag state stays in sync (a subsequent drag
// continues from the slid value, not the stale rendered rect). Clamped so
// the min thumb can't cross past the max thumb and vice versa.
function updateAxis(key: keyof MoodBox, raw: string) {
  const n = Number(raw)
  if (!Number.isFinite(n)) return
  const next = { ...box.value }
  if (key === 'energyMin') next.energyMin = Math.min(n, box.value.energyMax)
  else if (key === 'energyMax') next.energyMax = Math.max(n, box.value.energyMin)
  else if (key === 'danceMin') next.danceMin = Math.min(n, box.value.danceMax)
  else next.danceMax = Math.max(n, box.value.danceMin)
  setBox(next)
  emit('update:modelValue', next)
}
</script>

<template>
  <div class="rounded-2xl bg-glass-elevated backdrop-blur-xl ring-1 ring-border-glass p-4">
    <div class="flex gap-3">
      <!-- Energy axis label -->
      <div class="flex flex-col items-center justify-between text-[11px] text-foreground/40 py-1 select-none">
        <span>{{ t('playlists.smart.mood_energy_axis') }} ↑</span>
      </div>

      <div class="flex flex-col gap-1">
        <div class="relative" :style="{ width: `${SIZE}px`, height: `${SIZE}px` }">
          <svg :width="SIZE" :height="SIZE" class="overflow-visible">
            <rect x="0" y="0" :width="SIZE" :height="SIZE" class="fill-foreground/[0.03]" />
            <rect
              v-for="cell in cells"
              :key="`${cell.x}-${cell.y}`"
              :x="cell.x * cellSize"
              :y="cellRectY(cell.y)"
              :width="cellSize"
              :height="cellSize"
              :fill="cellFill(cell.count)"
            />
            <!-- Center crosshair at the 0.5/0.5 midpoint — both axes run 0..1
                 (no negative half), so without this the square reads as a
                 single positive-only block rather than four mood quadrants
                 (low/high energy x low/high danceability). -->
            <line :x1="SIZE / 2" y1="0" :x2="SIZE / 2" :y2="SIZE" class="stroke-foreground/15" stroke-dasharray="4 4" />
            <line x1="0" :y1="SIZE / 2" :x2="SIZE" :y2="SIZE / 2" class="stroke-foreground/15" stroke-dasharray="4 4" />
            <!-- Quadrant labels: high energy + high dance = Party, high energy +
                 low dance = Intense, low energy + high dance = Groove,
                 low energy + low dance = Chill. Purely a reading aid — plotted
                 over the density cells, not part of the rule/data. -->
            <text :x="SIZE / 2 + 10" y="18" class="fill-foreground/30 text-[11px] select-none">{{ t('playlists.smart.mood_quadrant_party') }}</text>
            <text x="10" y="18" class="fill-foreground/30 text-[11px] select-none">{{ t('playlists.smart.mood_quadrant_intense') }}</text>
            <text :x="SIZE / 2 + 10" :y="SIZE - 8" class="fill-foreground/30 text-[11px] select-none">{{ t('playlists.smart.mood_quadrant_groove') }}</text>
            <text x="10" :y="SIZE - 8" class="fill-foreground/30 text-[11px] select-none">{{ t('playlists.smart.mood_quadrant_chill') }}</text>
            <g ref="brushGroup" class="quadrant-brush" />
          </svg>
        </div>

        <div class="text-center text-[11px] text-foreground/40 select-none">
          {{ t('playlists.smart.mood_danceability_axis') }} →
        </div>
      </div>

      <!-- Selected-range readout, beside the heatmap rather than below it. -->
      <div class="flex flex-col justify-center gap-5 text-xs w-44 shrink-0">
        <div>
          <div class="text-foreground/50 mb-2">
            {{ t('playlists.smart.mood_energy_axis') }}
            <span class="text-foreground/30">{{ box.energyMin.toFixed(2) }}–{{ box.energyMax.toFixed(2) }}</span>
          </div>
          <div class="dual-range">
            <div class="dual-range-track" />
            <div
              class="dual-range-fill"
              :style="{ left: `${box.energyMin * 100}%`, width: `${(box.energyMax - box.energyMin) * 100}%` }"
            />
            <input
              type="range" min="0" max="1" step="0.01"
              :value="box.energyMin"
              @input="e => updateAxis('energyMin', (e.target as HTMLInputElement).value)"
            >
            <input
              type="range" min="0" max="1" step="0.01"
              :value="box.energyMax"
              @input="e => updateAxis('energyMax', (e.target as HTMLInputElement).value)"
            >
          </div>
        </div>
        <div>
          <div class="text-foreground/50 mb-2">
            {{ t('playlists.smart.mood_danceability_axis') }}
            <span class="text-foreground/30">{{ box.danceMin.toFixed(2) }}–{{ box.danceMax.toFixed(2) }}</span>
          </div>
          <div class="dual-range">
            <div class="dual-range-track" />
            <div
              class="dual-range-fill"
              :style="{ left: `${box.danceMin * 100}%`, width: `${(box.danceMax - box.danceMin) * 100}%` }"
            />
            <input
              type="range" min="0" max="1" step="0.01"
              :value="box.danceMin"
              @input="e => updateAxis('danceMin', (e.target as HTMLInputElement).value)"
            >
            <input
              type="range" min="0" max="1" step="0.01"
              :value="box.danceMax"
              @input="e => updateAxis('danceMax', (e.target as HTMLInputElement).value)"
            >
          </div>
        </div>

        <div class="border-t border-border-glass pt-4 flex flex-col gap-2">
          <div class="flex items-center gap-2 text-foreground/50">
            <span
              class="inline-block w-12 h-2 rounded-full shrink-0"
              :style="{ background: `linear-gradient(to right, ${RAMP[0]}, ${RAMP[RAMP.length - 1]})` }"
            />
            <span>{{ t('playlists.smart.mood_legend_few') }} → {{ t('playlists.smart.mood_legend_many') }}</span>
          </div>
          <div class="text-foreground/50 whitespace-nowrap">
            {{ t('playlists.smart.mood_coverage_caption', { analyzed: grid?.analyzed_count ?? 0, total: grid?.total_count ?? 0 }) }}
          </div>
        </div>
      </div>
    </div>

    <p v-if="!boxIsValid" class="text-xs text-red-500 mt-3">
      {{ t('playlists.smart.mood_invalid_box') }}
    </p>
  </div>
</template>

<style scoped>
.quadrant-brush :deep(.selection) {
  fill: var(--primary);
  fill-opacity: 0.15;
  stroke: var(--primary);
  stroke-width: 0.75px;
  stroke-opacity: 1;
}

/* d3-brush's resize handles are ~6px-thick rects along each edge/corner by
   default — that's what reads as a "thick border", not .selection's own
   stroke. Keep them functional (still there for resize hit-testing/cursor)
   but visually invisible, so the only visible border is .selection's thin
   stroke above. */
.quadrant-brush :deep(.handle) {
  fill: transparent;
}

.quadrant-brush :deep(.overlay) {
  cursor: crosshair;
}

/* Two overlaid range inputs sharing one visual track — the classic
   dual-thumb slider trick: each <input> is transparent/full-width, and only
   its thumb captures pointer events so both stay independently draggable.
   Track/fill/thumb visuals mirror @airmedy/ui's Slider (packages/ui/src/
   slider/Slider.vue) exactly, so this reads as the same slider used
   everywhere else (seek bar, volume, EQ) rather than a one-off control. */
.dual-range {
  position: relative;
  height: 16px;
  display: flex;
  align-items: center;
}

.dual-range-track {
  position: absolute;
  left: 0;
  right: 0;
  height: 4px;
  border-radius: 999px;
  background: var(--text-main);
  opacity: 0.15;
}

.dual-range-fill {
  position: absolute;
  height: 4px;
  border-radius: 999px;
  background: var(--text-main);
}

.dual-range input[type='range'] {
  position: absolute;
  inset: 0;
  width: 100%;
  margin: 0;
  background: transparent;
  appearance: none;
  -webkit-appearance: none;
  pointer-events: none;
  z-index: 10;
}

.dual-range:hover input[type='range']::-webkit-slider-thumb {
  opacity: 1;
}
.dual-range:hover input[type='range']::-moz-range-thumb {
  opacity: 1;
}

.dual-range input[type='range']::-webkit-slider-thumb {
  appearance: none;
  -webkit-appearance: none;
  pointer-events: auto;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
  opacity: 0;
  transition: opacity 150ms ease;
  cursor: pointer;
}

.dual-range input[type='range']::-moz-range-thumb {
  pointer-events: auto;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: white;
  border: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
  opacity: 0;
  transition: opacity 150ms ease;
  cursor: pointer;
}

.dual-range input[type='range']::-webkit-slider-runnable-track,
.dual-range input[type='range']::-moz-range-track {
  background: transparent;
}
</style>
