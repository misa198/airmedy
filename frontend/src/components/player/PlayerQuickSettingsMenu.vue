<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Check, Moon, CircleDot, SlidersHorizontal, Blend, Settings2 } from '@lucide/vue'
import type { EQProfile } from '../../../bindings/airmedy/internal/domain/models'
import * as EQService from '../../../bindings/airmedy/internal/infra/wails/eqservice'
import { useAppStore } from '@/stores/app'
import { useContextMenu, type ContextMenuItem } from '@/composables/useContextMenu'
import ContextMenu from '@/components/ContextMenu.vue'

const CROSSFADE_DEFAULT_SECONDS = 4

const { t } = useI18n()
const router = useRouter()
const appStore = useAppStore()
const contextMenu = useContextMenu()
const profiles = ref<EQProfile[]>([])
const activeProfileID = ref<string | null>(null)

async function loadProfiles() {
  try {
    const [all, active] = await Promise.all([
      EQService.GetAllProfiles(),
      EQService.GetActiveProfile(),
    ])
    profiles.value = (all.filter(Boolean) as EQProfile[])
    activeProfileID.value = active?.id ?? null
  } catch (error) {
    console.error('Failed to load EQ profiles for player quick settings:', error)
    profiles.value = []
    activeProfileID.value = null
  }
}

async function selectProfile(profile: EQProfile) {
  await EQService.ApplyProfile(profile.id)
  await appStore.updateEQEnabled(true)
  activeProfileID.value = profile.id
}

function goToEqualizerSettings() {
  router.push({
    name: 'settings',
    params: { category: 'playback' },
    query: { section: 'equalizer' },
  })
}

function buildItems(): ContextMenuItem[] {
  const eqChildren: ContextMenuItem[] = profiles.value.map((profile) => ({
    label: profile.name,
    iconRight: profile.id === activeProfileID.value ? Check : undefined,
    action: () => selectProfile(profile),
  }))

  if (eqChildren.length) eqChildren.push({ separator: true })
  eqChildren.push({
    label: t('settings.quick_menu.go_to_equalizer'),
    icon: Settings2,
    action: goToEqualizerSettings,
  })

  return [
    {
      label: t('settings.playback.prevent_sleep'),
      icon: Moon,
      iconRight: appStore.preventSleepWhilePlaying ? Check : undefined,
      action: () => appStore.updatePreventSleepWhilePlaying(!appStore.preventSleepWhilePlaying),
    },
    {
      label: t('settings.playback.show_player_indicator'),
      icon: CircleDot,
      iconRight: appStore.showPlayerIndicator ? Check : undefined,
      action: () => appStore.updateShowPlayerIndicator(!appStore.showPlayerIndicator),
    },
    { separator: true },
    {
      label: t('settings.playback.crossfade'),
      icon: Blend,
      iconRight: appStore.crossfadeSeconds > 0 ? Check : undefined,
      action: () => appStore.updateCrossfadeSeconds(
        appStore.crossfadeSeconds > 0 ? 0 : CROSSFADE_DEFAULT_SECONDS,
      ),
    },
    { separator: true },
    {
      label: t('settings.quick_menu.eq_presets'),
      icon: SlidersHorizontal,
      children: eqChildren,
    },
  ]
}

async function open(event: MouseEvent) {
  await loadProfiles()
  contextMenu.open(event, buildItems())
}

defineExpose({ open })
</script>

<template>
  <ContextMenu
    :visible="contextMenu.visible.value"
    :x="contextMenu.x.value"
    :y="contextMenu.y.value"
    :items="contextMenu.items.value"
    @close="contextMenu.close()"
  />
</template>
