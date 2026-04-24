<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Slider } from '@/components/ui/slider'
import * as EQService from '../../bindings/airmedy/internal/infra/wails/eqservice'
import type { EQProfile } from '../../bindings/airmedy/internal/domain/models'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const profiles = ref<EQProfile[]>([])
const activeProfile = ref<EQProfile | null>(null)
const enabled = ref(true)

const FREQ_LABELS = ['32', '64', '125', '250', '500', '1k', '2k', '4k', '8k', '16k']

onMounted(async () => {
  try {
    const [all, active] = await Promise.all([
      EQService.GetAllProfiles(),
      EQService.GetActiveProfile(),
    ])
    profiles.value = all.filter(Boolean) as EQProfile[]
    activeProfile.value = active
  } catch (e) {
    console.error('Failed to load EQ profiles', e)
  }
})

const bands = computed(() => {
  return activeProfile.value?.bands?.slice().sort((a, b) => (a?.index ?? 0) - (b?.index ?? 0)) ?? []
})

async function selectProfile(id: string) {
  await EQService.ApplyProfile(id)
  const p = profiles.value.find((x) => x.id === id)
  if (p) {
    activeProfile.value = { ...p }
    profiles.value = profiles.value.map((x) => ({ ...x, is_active: x.id === id }))
  }
}

async function updateBand(bandIndex: number, gain: number) {
  if (!activeProfile.value) return
  await EQService.UpdateBand(activeProfile.value.id, bandIndex, gain)
  if (activeProfile.value.bands) {
    activeProfile.value.bands = activeProfile.value.bands.map((b) =>
      b && b.index === bandIndex ? { ...b, gain } : b
    )
  }
}

async function toggleEnabled() {
  enabled.value = !enabled.value
  await EQService.SetEnabled(enabled.value)
}

function getBandGain(index: number): number {
  return bands.value.find((b) => b?.index === index)?.gain ?? 0
}
</script>

<template>
  <div class="space-y-4">
    <!-- Header row: profile selector + enable toggle -->
    <div class="flex items-center justify-between gap-3">
      <Select
        v-if="profiles.length > 0"
        :model-value="activeProfile?.id"
        @update:model-value="selectProfile"
      >
        <SelectTrigger class="flex-1 bg-foreground/[0.05] border border-foreground/[0.08] text-sm text-foreground rounded-lg px-3 py-1.5 focus:outline-none focus:ring-1 focus:ring-foreground/20">
          <SelectValue placeholder="Select Profile" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem
            v-for="p in profiles"
            :key="p.id"
            :value="p.id"
          >
            {{ p.name }}
          </SelectItem>
        </SelectContent>
      </Select>

      <!-- Enable/Disable toggle -->
      <button
        class="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm transition-colors"
        :class="enabled
          ? 'bg-foreground/[0.1] text-foreground hover:bg-foreground/[0.14]'
          : 'bg-foreground/[0.03] text-foreground/40 hover:bg-foreground/[0.06]'"
        @click="toggleEnabled"
      >
        <span class="w-1.5 h-1.5 rounded-full" :class="enabled ? 'bg-green-400' : 'bg-foreground/20'" />
        {{ enabled ? 'On' : 'Off' }}
      </button>
    </div>

    <!-- 10-band vertical sliders -->
    <div class="flex items-end justify-between gap-1 h-40 px-1">
      <div
        v-for="(label, i) in FREQ_LABELS"
        :key="i"
        class="flex flex-col items-center flex-1 min-w-0 h-full"
      >
        <!-- Gain value -->
        <p class="text-[10px] text-foreground/30 mb-1 tabular-nums w-full text-center">
          {{ getBandGain(i) >= 0 ? '+' : '' }}{{ getBandGain(i).toFixed(1) }}
        </p>
        <!-- Vertical slider via CSS rotation wrapper -->
        <div class="flex-1 flex items-center justify-center w-full">
          <div class="relative" style="width: 24px; height: 80px;">
            <div
              class="absolute inset-0 flex items-center justify-center"
              style="transform: rotate(-90deg); transform-origin: center; width: 80px; height: 24px; top: 50%; left: 50%; margin-top: -12px; margin-left: -40px;"
            >
              <Slider
                :model-value="getBandGain(i)"
                :min="-12"
                :max="12"
                :step="0.5"
                class="w-full"
                @update:model-value="(val: number) => updateBand(i, val)"
              />
            </div>
          </div>
        </div>
        <!-- Freq label -->
        <p class="text-[10px] text-foreground/30 mt-1">{{ label }}</p>
      </div>
    </div>
  </div>
</template>
