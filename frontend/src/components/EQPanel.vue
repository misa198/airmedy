<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
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
import { MoreHorizontal, Plus, Pencil, Trash2 } from 'lucide-vue-next'
import { useContextMenu } from '@/composables/useContextMenu'
import ContextMenu from './ContextMenu.vue'
import EQProfileDialog from './EQProfileDialog.vue'

const { t } = useI18n()
const appStore = useAppStore()
const profiles = ref<EQProfile[]>([])
const activeProfile = ref<EQProfile | null>(null)

const showAddModal = ref(false)
const showRenameModal = ref(false)

const deleting = ref(false)

const contextMenu = useContextMenu()

const FREQ_LABELS = ['32', '64', '125', '250', '500', '1k', '2k', '4k', '8k', '16k']

onMounted(async () => {
  try {
    const [all, active] = await Promise.all([
      EQService.GetAllProfiles(),
      EQService.GetActiveProfile(),
    ])
    const filtered = all.filter(Boolean) as EQProfile[]
    profiles.value = filtered
    if (active) {
      activeProfile.value = filtered.find((p) => p.id === active.id) || active
    }
  } catch (e) {
    console.error('Failed to load EQ profiles', e)
  }
})

const bands = computed(() => {
  return activeProfile.value?.bands?.slice().sort((a, b) => (a?.index ?? 0) - (b?.index ?? 0)) ?? []
})

async function selectProfile(id: string) {
  await EQService.ApplyProfile(id)
  await appStore.updateEQEnabled(true)
  profiles.value = profiles.value.map((x) => ({ ...x, is_active: x.id === id }))
  const p = profiles.value.find((x) => x.id === id)
  if (p) {
    activeProfile.value = p
  }
}

function onBandInput(bandIndex: number, gain: number) {
  if (!activeProfile.value?.bands) return
  activeProfile.value.bands = activeProfile.value.bands.map((b) =>
    b && b.index === bandIndex ? { ...b, gain } : b
  )
}

async function onBandRelease(bandIndex: number) {
  if (!activeProfile.value) return
  const gain = getBandGain(bandIndex)
  await EQService.UpdateBand(activeProfile.value.id, bandIndex, gain)
}

async function toggleEnabled() {
  await appStore.updateEQEnabled(!appStore.eqEnabled)
}

function getBandGain(index: number): number {
  return bands.value.find((b) => b?.index === index)?.gain ?? 0
}

function openAddModal() {
  showAddModal.value = true
}

async function createProfile(name: string) {
  const p = await EQService.CreateProfile(name)
  if (p) {
    profiles.value.push(p)
    await selectProfile(p.id)
  }
}

function openRenameModal() {
  if (!activeProfile.value) return
  showRenameModal.value = true
}

async function renameProfile(name: string) {
  if (!activeProfile.value) return
  await EQService.RenameProfile(activeProfile.value.id, name)
  profiles.value = profiles.value.map((p) =>
    p.id === activeProfile.value!.id ? { ...p, name } : p
  )
  const updated = profiles.value.find(p => p.id === activeProfile.value!.id)
  if (updated) {
    activeProfile.value = updated
  }
}

async function deleteProfile() {
  if (!activeProfile.value || activeProfile.value.is_default) return
  deleting.value = true
  try {
    const deletedId = activeProfile.value.id
    await EQService.DeleteProfile(deletedId)
    profiles.value = profiles.value.filter((p) => p.id !== deletedId)
    const fallback = profiles.value[0]
    if (fallback) await selectProfile(fallback.id)
    else activeProfile.value = null
  } finally {
    deleting.value = false
  }
}

function openProfileMenu(e: MouseEvent) {
  const isUserProfile = activeProfile.value && !activeProfile.value.is_default
  contextMenu.open(e, [
    {
      label: t('settings.equalizer.new_profile'),
      icon: Plus,
      action: openAddModal,
    },
    ...(isUserProfile ? [
      { separator: true },
      {
        label: t('settings.equalizer.rename_profile'),
        icon: Pencil,
        action: openRenameModal,
      },
      {
        label: t('settings.equalizer.delete_profile'),
        icon: Trash2,
        danger: true,
        action: deleteProfile,
      },
    ] : []),
  ])
}
</script>

<template>
  <div class="space-y-4">
    <!-- Header row: enable toggle + profile selector + menu -->
    <div class="flex items-center gap-2">
      <!-- Enable/Disable toggle -->
      <button
        class="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm transition-colors w-18 h-10 border border-foreground/[0.08]"
        :class="appStore.eqEnabled
          ? 'bg-foreground/[0.1] text-foreground hover:bg-foreground/[0.14]'
          : 'bg-foreground/[0.03] text-foreground opacity-60 hover:bg-foreground/[0.06]'"
        @click="toggleEnabled">
        <span class="w-1.5 h-1.5 rounded-full" :class="appStore.eqEnabled ? 'bg-green-400' : 'bg-foreground/20'" />
        {{ appStore.eqEnabled ? t('common.on') : t('common.off') }}
      </button>

      <!-- Profile selector -->
      <Select v-if="profiles.length > 0" :model-value="activeProfile?.id" @update:model-value="selectProfile">
        <SelectTrigger
          class="flex-1 bg-foreground/[0.05] border border-foreground/[0.08] text-sm text-foreground rounded-lg px-3 py-1.5 focus:outline-none focus:ring-1 focus:ring-foreground/20">
          <SelectValue :placeholder="t('settings.equalizer.select_profile')" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="p in profiles" :key="p.id" :value="p.id">
            {{ p.name }}
          </SelectItem>
        </SelectContent>
      </Select>

      <!-- Profile actions menu -->
      <button
        class="flex items-center justify-center w-10 h-10 rounded-lg text-sm transition-colors border border-foreground/[0.08] bg-foreground/[0.05] text-foreground opacity-80 hover:bg-foreground/[0.1] hover:text-foreground"
        @click="openProfileMenu">
        <MoreHorizontal class="w-4 h-4" />
      </button>
    </div>

    <!-- 10-band vertical sliders -->
    <div class="flex items-end justify-between gap-1 h-40 px-1">
      <div v-for="(label, i) in FREQ_LABELS" :key="i" class="flex flex-col items-center flex-1 min-w-0 h-full">
        <!-- Gain value -->
        <p class="text-[10px] text-foreground opacity-80 mb-1 tabular-nums w-full text-center">
          {{ getBandGain(i) >= 0 ? '+' : '' }}{{ getBandGain(i).toFixed(1) }}
        </p>
        <!-- Vertical slider via CSS rotation wrapper -->
        <div class="flex-1 flex items-center justify-center w-full">
          <div class="relative" style="width: 24px; height: 80px;">
            <div class="absolute inset-0 flex items-center justify-center"
              style="transform: rotate(-90deg); transform-origin: center; width: 80px; height: 24px; top: 50%; left: 50%; margin-top: -12px; margin-left: -40px;">
              <Slider :model-value="getBandGain(i)" :min="-12" :max="12" :step="0.5" class="w-full"
                @update:model-value="(val: number) => onBandInput(i, val)"
                @mouseup="() => onBandRelease(i)"
                @touchend="() => onBandRelease(i)" />
            </div>
          </div>
        </div>
        <!-- Freq label -->
        <p class="text-[10px] text-foreground opacity-80 mt-1">{{ label }}</p>
      </div>
    </div>
  </div>

  <ContextMenu
    :visible="contextMenu.visible.value"
    :x="contextMenu.x.value"
    :y="contextMenu.y.value"
    :items="contextMenu.items.value"
    @close="contextMenu.close()" />

  <EQProfileDialog
    :open="showAddModal"
    :title="t('settings.equalizer.new_profile')"
    :confirm-label="t('settings.equalizer.add_profile')"
    @update:open="showAddModal = $event"
    @confirm="createProfile" />

  <EQProfileDialog
    :open="showRenameModal"
    :title="t('settings.equalizer.rename_profile')"
    :confirm-label="t('common.save')"
    :initial-name="activeProfile?.name"
    @update:open="showRenameModal = $event"
    @confirm="renameProfile" />
</template>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
