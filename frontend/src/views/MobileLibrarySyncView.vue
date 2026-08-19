<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Events } from '@wailsio/runtime'
import { ArrowLeft, LoaderCircle, Search, RefreshCcw, X } from '@lucide/vue'
import { Badge, Checkbox, Input, Radio, TabSwitcher } from '@airmedy/ui'
import { RecycleScroller } from 'vue-virtual-scroller'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import SettingSection from '@/components/settings/SettingSection.vue'
import { useRowBackground } from '@/composables/useRowBackground'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import * as MobilePairingService from '../../bindings/airmedy/internal/infra/wails/mobilepairingservice'
import * as MobileLibrarySyncService from '../../bindings/airmedy/internal/infra/wails/mobilelibrarysyncservice'
import { MobileLibrarySyncScope, type AlbumDTO, type Artist, type Genre, type MobileLibrarySyncPlan, type Playlist } from '../../bindings/airmedy/internal/domain/models'

type ScopeKind = 'artists' | 'albums' | 'genres' | 'playlists'
type Selectable = { id: string; label: string; detail?: string }
type Device = { device_id: string; display_name: string; online: boolean }
type PairingStatus = { addresses: { ip: string; kind: string }[] }

const route = useRoute()
const router = useRouter()
const deviceID = computed(() => String(route.params.deviceId ?? ''))
const device = ref<Device | null>(null)
const plan = ref<MobileLibrarySyncPlan | null>(null)
const loading = ref(true)
const syncing = ref(false)
const query = ref('')
const mode = ref<'all' | 'selected'>('all')
const activeTab = ref<ScopeKind>('artists')
const items = ref<Record<ScopeKind, Selectable[]>>({ artists: [], albums: [], genres: [], playlists: [] })
const selected = ref<Record<ScopeKind, Set<string>>>({ artists: new Set(), albums: new Set(), genres: new Set(), playlists: new Set() })
const host = ref('')
const replaceOpen = ref(false)
const { rowBg } = useRowBackground()
let offPairing: (() => void) | null = null
let offSync: (() => void) | null = null

const tabs = [
  { value: 'artists', label: 'Artists' }, { value: 'albums', label: 'Albums' },
  { value: 'genres', label: 'Genres' }, { value: 'playlists', label: 'Playlists' },
]
const activeItems = computed(() => items.value[activeTab.value].filter(item => item.label.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase())))
const activeSelected = computed(() => selected.value[activeTab.value])
const currentScope = computed(() => new MobileLibrarySyncScope({ kind: mode.value === 'all' ? 'all' : activeTab.value, selected_ids: mode.value === 'all' ? [] : Array.from(activeSelected.value).sort() }))
const isSyncInProgress = computed(() => syncing.value || plan.value?.status === 'active')
const canSync = computed(() => !!device.value?.online && !isSyncInProgress.value && (mode.value === 'all' || activeSelected.value.size > 0))
const progressPercent = computed(() => {
  if (!plan.value || plan.value.total <= 0) return 0
  return Math.floor((plan.value.completed / plan.value.total) * 100)
})

function sameScope() {
  if (!plan.value || plan.value.status !== 'active') return false
  const planned = [...plan.value.scope.selected_ids].sort().join('\u0000')
  return plan.value.scope.kind === currentScope.value.kind && planned === [...currentScope.value.selected_ids].sort().join('\u0000')
}

function toggle(id: string) {
  const set = activeSelected.value
  if (set.has(id)) set.delete(id); else set.add(id)
  selected.value = { ...selected.value, [activeTab.value]: new Set(set) }
}

let pollTimer: ReturnType<typeof setInterval> | null = null

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (!deviceID.value || plan.value?.status !== 'active') {
      stopPolling()
      return
    }
    const polledPlanID = plan.value.id
    try {
      const status = await MobileLibrarySyncService.GetStatus(deviceID.value)
      if (plan.value?.id !== polledPlanID || plan.value.status !== 'active') return
      if (status) {
        plan.value = status
        if (status.status !== 'active') {
          stopPolling()
        }
      }
    } catch (error) {
      console.error('Failed to poll mobile sync status:', error)
    }
  }, 1000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function load() {
  loading.value = true
  try {
    const [devices, pairing, status, artists, albums, genres, playlists] = await Promise.all([
      MobilePairingService.GetTrustedDevices(), MobilePairingService.GetStatus(), MobileLibrarySyncService.GetStatus(deviceID.value),
      LibraryService.GetAllArtists(), LibraryService.GetAllAlbums(), LibraryService.GetAllGenres(), PlaylistService.GetAllPlaylists(),
    ])
    device.value = (devices ?? []).find(entry => entry?.device_id === deviceID.value) ?? null
    const network = pairing as PairingStatus
    host.value = network.addresses?.find(address => address.kind === 'ethernet' || address.kind === 'wifi')?.ip ?? network.addresses?.[0]?.ip ?? ''
    plan.value = status
    if (plan.value?.status === 'active') {
      startPolling()
    }
    items.value = {
      artists: (artists ?? []).filter((item): item is Artist => !!item).map(item => ({ id: item.id, label: item.name })),
      albums: (albums ?? []).filter((item): item is AlbumDTO => !!item).map(item => ({ id: item.id, label: item.title, detail: item.artists?.filter(Boolean).map(artist => artist!.name).join(', ') })),
      genres: (genres ?? []).filter((item): item is Genre => !!item).map(item => ({ id: item.id, label: item.name })),
      playlists: (playlists ?? []).filter((item): item is Playlist => !!item).map(item => ({ id: item.id, label: item.name })),
    }
    if (plan.value?.scope.kind && plan.value.scope.kind !== 'all') {
      mode.value = 'selected'
      activeTab.value = plan.value.scope.kind as ScopeKind
      selected.value = { ...selected.value, [activeTab.value]: new Set(plan.value.scope.selected_ids) }
    }
  } catch (error) { console.error('Failed to load mobile library sync:', error) }
  finally { loading.value = false }
}

async function refreshDeviceStatus() {
  try {
    const devices = await MobilePairingService.GetTrustedDevices()
    device.value = (devices ?? []).find(entry => entry?.device_id === deviceID.value) ?? null
  } catch (error) {
    console.error('Failed to refresh mobile device status:', error)
  }
}

async function sync(replace = false) {
  if (!canSync.value || !host.value) return
  if (!replace && plan.value?.status === 'active' && !sameScope()) { replaceOpen.value = true; return }
  syncing.value = true
  try {
    plan.value = await MobileLibrarySyncService.Sync(deviceID.value, currentScope.value, host.value, replace)
    if (plan.value?.status === 'active') {
      startPolling()
    }
  }
  catch (error) { console.error('Failed to start mobile library sync:', error) }
  finally { syncing.value = false }
}

async function cancelSync() {
  if (plan.value?.status !== 'active') return
  syncing.value = true
  try {
    plan.value = await MobileLibrarySyncService.Cancel(deviceID.value)
    stopPolling()
  }
  catch (error) { console.error('Failed to cancel mobile library sync:', error) }
  finally { syncing.value = false }
}

onMounted(() => {
  void load()
  offPairing = Events.On('pairing:trusted-devices-changed', refreshDeviceStatus)
  offSync = Events.On('mobile-library-sync:updated', event => {
    const raw = Array.isArray(event.data) ? event.data[0] : (event.data?.data ?? event.data)
    const updated = raw as MobileLibrarySyncPlan
    if (updated && updated.device_id === deviceID.value) {
      plan.value = updated
      if (updated.status !== 'active') {
        stopPolling()
      }
    }
  })
})
onUnmounted(() => {
  stopPolling()
  offPairing?.()
  offSync?.()
})
</script>

<template>
  <main class="mx-auto flex h-full min-h-0 w-full max-w-3xl flex-col gap-6 p-8 select-none">
    <button type="button" class="relative z-[99] w-fit rounded-full p-2 transition-colors hover:bg-foreground/[0.06]"
      :aria-label="$t('mobile_sync.back')" @click="router.back()">
      <ArrowLeft class="size-6" />
    </button>
    <header class="flex items-start justify-between gap-4">
      <div class="flex gap-1 items-center gap-4">
        <h1 class="mt-1 text-3xl font-bold tracking-[-0.02em]">{{ device?.display_name ?? $t('mobile_sync.title') }}
        </h1>
        <div>
          <Badge class="gap-1" :color="device?.online ? 'var(--status-online)' : 'var(--text-muted)'">
            <span class="size-1 rounded-full bg-current" />
            {{ $t(device?.online ? 'settings.mobile_pairing.online' : 'settings.mobile_pairing.offline') }}
          </Badge>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <div class="flex items-center justify-end gap-2">
          <div class="flex items-center gap-2">
            <button v-if="plan?.status === 'active'" data-testid="cancel-sync-button" type="button"
              class="flex items-center gap-2 rounded-lg bg-foreground/[0.04] px-3 py-2 text-sm font-medium text-foreground/70 transition-all hover:bg-foreground/[0.08] disabled:opacity-50"
              :disabled="syncing" @click="cancelSync">
              <X class="size-4" />
              {{ $t('common.cancel') }}
            </button>
            <button data-testid="sync-button" type="button"
              class="flex items-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground transition-all hover:scale-[1.02] disabled:opacity-50"
              :disabled="!canSync" @click="sync()">
              <RefreshCcw data-testid="sync-icon" class="size-4" :class="{ 'animate-spin': isSyncInProgress }" />
              {{ $t('mobile_sync.sync') }}
            </button>
          </div>
        </div>
      </div>
    </header>

    <SettingSection :icon="RefreshCcw" :label="$t('mobile_sync.sync')" variant="panel" hide-header
      :class="mode === 'selected' ? 'flex min-h-0 flex-1 flex-col' : ''"
      :content-class="mode === 'selected' ? 'flex min-h-0 flex-1 flex-col' : ''">
      <div class="flex justify-between">
        <div class="space-y-4" role="radiogroup">
          <label class="flex items-center gap-3 text-sm"
            :class="isSyncInProgress ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'">
            <Radio data-testid="scope-all" v-model="mode" value="all" :disabled="isSyncInProgress" />{{
              $t('mobile_sync.entire_library') }}
          </label>
          <label class="flex items-center gap-3 text-sm"
            :class="isSyncInProgress ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'">
            <Radio data-testid="scope-selected" v-model="mode" value="selected" :disabled="isSyncInProgress" />{{
              $t('mobile_sync.selected_items') }}
          </label>
        </div>
      </div>
      <div v-if="plan" class="mt-5 border-t border-foreground/[0.06] pt-4">
        <div class="flex items-baseline justify-between gap-4">
          <p class="text-sm font-medium">{{ plan.status === 'complete' ? $t('mobile_sync.complete') :
            $t('mobile_sync.pending') }}</p>
          <span class="w-10 text-right text-sm font-semibold tabular-nums text-foreground/70">
            {{ progressPercent }}%
          </span>
        </div>
        <div v-if="plan" class="mt-2 flex items-center gap-2">
          <div
            class="h-1 flex-1 overflow-hidden rounded-full bg-foreground/[0.06] transition-all duration-300 hover:h-1.5">
            <div class="h-full bg-foreground transition-all duration-300" :style="{ width: `${progressPercent}%` }" />
          </div>
        </div>
      </div>
      <div v-if="mode === 'selected'"
        class="relative mt-6 flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-[var(--border-glass)] bg-foreground/[0.03] p-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <TabSwitcher v-model="activeTab" :options="tabs" mandatory variant="label" />
          <div class="relative w-full max-w-xs">
            <Search
              class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[color:var(--text-muted)]" />
            <Input v-model="query" :placeholder="$t('mobile_sync.search', { type: $t(`mobile_sync.${activeTab}`) })"
              class="pl-9" />
          </div>
        </div>
        <div class="mt-4 min-h-0 flex-1 overflow-hidden">
          <div
            class="grid h-9 grid-cols-[2.25rem_minmax(0,1fr)_minmax(0,0.8fr)] items-center border-b border-foreground/[0.06] bg-background text-[10px] font-semibold uppercase tracking-widest text-foreground opacity-80">
            <span class="sr-only">{{ $t('mobile_sync.selected_items') }}</span>
            <span class="px-3">{{ $t(`mobile_sync.${activeTab}`) }}</span>
            <span class="px-3" />
          </div>
          <RecycleScroller class="h-[calc(100%-2.25rem)] custom-scrollbar" :items="activeItems" :item-size="56"
            key-field="id" v-slot="{ item, index }">
            <button type="button"
              class="grid h-14 w-full grid-cols-[2.25rem_minmax(0,1fr)_minmax(0,0.8fr)] items-center text-left text-sm transition-colors hover:bg-foreground/[0.04]"
              :style="{ background: rowBg(index) }" :disabled="isSyncInProgress" @click="toggle(item.id)">
              <span class="flex justify-center">
                <Checkbox :checked="activeSelected.has(item.id)" :disabled="isSyncInProgress" />
              </span>
              <span class="truncate px-3 font-medium">{{ item.label }}</span>
              <span class="truncate px-3 text-xs text-foreground opacity-80">{{ item.detail ?? '' }}</span>
            </button>
          </RecycleScroller>
        </div>
        <div v-if="isSyncInProgress" data-testid="sync-selection-overlay" aria-hidden="true"
          class="absolute inset-0 z-10 flex cursor-not-allowed items-center justify-center bg-disabled-overlay">
          <LoaderCircle data-testid="sync-selection-spinner" class="size-5 animate-spin text-foreground/60" />
        </div>
      </div>
    </SettingSection>

    <ConfirmDialog :open="replaceOpen" :title="$t('mobile_sync.replace_title')"
      :message="$t('mobile_sync.replace_desc')" :confirm-label="$t('mobile_sync.replace')" danger
      @cancel="replaceOpen = false" @confirm="sync(true)" />
  </main>
</template>
