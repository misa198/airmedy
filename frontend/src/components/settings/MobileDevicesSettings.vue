<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Browser, Events } from '@wailsio/runtime'
import { ChevronRight, CircleHelp, LoaderCircle, MoreHorizontal, Radio, RefreshCw, ShieldCheck, Smartphone, Trash2, Wifi, X } from '@lucide/vue'
import QRCodeStyling from 'qr-code-styling'
import { Badge } from '@airmedy/ui'
import * as MobilePairingService from '../../../bindings/airmedy/internal/infra/wails/mobilepairingservice'
import * as MobileLibrarySyncService from '../../../bindings/airmedy/internal/infra/wails/mobilelibrarysyncservice'
import ContextMenu from '@/components/ContextMenu.vue'
import { useContextMenu } from '@/composables/useContextMenu'
import SettingSection from './SettingSection.vue'
import NetworkAddressList, { type NetworkAddressEntry } from './NetworkAddressList.vue'

const { locale, t } = useI18n()
const router = useRouter()

interface LocalAddress { ip: string; iface: string; kind: string }
interface PairingStatus { running: boolean; port: number; device_id: string; desktop_name: string; public_key: string; error: string; addresses: LocalAddress[]; broadcasting: boolean; broadcasting_until: string }
interface TrustedDevice { device_id: string; display_name: string; platform: string; fingerprint: string; paired_at: string; last_seen_at: string; online: boolean }

const status = ref<PairingStatus | null>(null)
const devices = ref<TrustedDevice[]>([])
const selectedIP = ref('')
const loading = ref(false)
const revoking = ref('')
const broadcastingAction = ref(false)
const broadcastSecondsRemaining = ref(0)
const syncingDeviceIDs = ref(new Set<string>())
const lastSyncedAtByDeviceID = ref(new Map<string, string>())
const qrContainer = ref<HTMLElement | null>(null)
let qr: QRCodeStyling | null = null
let offTrustedDevicesChanged: (() => void) | null = null
let offBroadcastChanged: (() => void) | null = null
let offMobileSyncUpdated: (() => void) | null = null
let broadcastTimer: ReturnType<typeof setInterval> | null = null
const deviceContextMenu = useContextMenu()
const mobileSyncHelpURL = 'https://airmedy.pages.dev/faq/mobile-sync'

const usableAddresses = computed(() => status.value?.addresses ?? [])
const isBroadcasting = computed(() => !!status.value?.broadcasting)
const networkEntries = computed<NetworkAddressEntry[]>(() => usableAddresses.value.map(address => ({ ...address, value: address.ip, display: address.ip })))
const pairingURL = computed(() => {
  if (!status.value?.running || !selectedIP.value) return ''
  const params = new URLSearchParams({ host: selectedIP.value, port: String(status.value.port), desktop_id: status.value.device_id, desktop_name: status.value.desktop_name, public_key: status.value.public_key })
  return `airmedy://pair/v1?${params.toString()}`
})

async function load() {
  loading.value = true
  try {
    status.value = await MobilePairingService.GetStatus() as PairingStatus
    const trustedDevices = (await MobilePairingService.GetTrustedDevices() ?? []) as TrustedDevice[]
    devices.value = trustedDevices
    const plans = await Promise.all(trustedDevices.map(device => MobileLibrarySyncService.GetStatus(device.device_id).catch(() => null)))
    syncingDeviceIDs.value = new Set(plans.filter(plan => plan?.status === 'active').map(plan => plan!.device_id))
    lastSyncedAtByDeviceID.value = new Map(plans.flatMap(plan =>
      plan?.last_completed_at ? [[plan.device_id, plan.last_completed_at] as const] : [],
    ))
    if (!usableAddresses.value.some(address => address.ip === selectedIP.value)) {
      selectedIP.value = usableAddresses.value.find(address => address.kind === 'ethernet' || address.kind === 'wifi')?.ip ?? usableAddresses.value[0]?.ip ?? ''
    }
  } catch (error) {
    console.error('Failed to load mobile pairing status:', error)
  } finally {
    loading.value = false
  }
}

async function revoke(deviceID: string) {
  if (revoking.value || syncingDeviceIDs.value.has(deviceID)) return
  revoking.value = deviceID
  try { await MobilePairingService.RevokeDevice(deviceID); await load() }
  catch (error) { console.error('Failed to revoke mobile device:', error) }
  finally { revoking.value = '' }
}

function updateBroadcastCountdown() {
  const deadline = status.value?.broadcasting_until ? new Date(status.value.broadcasting_until).getTime() : 0
  broadcastSecondsRemaining.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
  if (broadcastSecondsRemaining.value === 0 && status.value?.broadcasting) void load()
}

function stopBroadcastTimer() {
  if (broadcastTimer) clearInterval(broadcastTimer)
  broadcastTimer = null
}

function syncBroadcastTimer() {
  stopBroadcastTimer()
  if (!isBroadcasting.value) {
    broadcastSecondsRemaining.value = 0
    return
  }
  updateBroadcastCountdown()
  broadcastTimer = setInterval(updateBroadcastCountdown, 250)
}

async function startBroadcast() {
  if (broadcastingAction.value) return
  broadcastingAction.value = true
  try {
    await MobilePairingService.StartBroadcast()
    await load()
  } catch (error) {
    console.error('Failed to start mobile pairing broadcast:', error)
  } finally {
    broadcastingAction.value = false
  }
}

async function stopBroadcast() {
  if (broadcastingAction.value) return
  broadcastingAction.value = true
  try {
    await MobilePairingService.StopBroadcast()
    await load()
  } catch (error) {
    console.error('Failed to stop mobile pairing broadcast:', error)
  } finally {
    broadcastingAction.value = false
  }
}

function openDeviceMenu(event: MouseEvent | KeyboardEvent, device: TrustedDevice) {
  const menuEvent = event instanceof MouseEvent
    ? event
    : new MouseEvent('click', {
        clientX: (event.currentTarget as HTMLElement).getBoundingClientRect().left,
        clientY: (event.currentTarget as HTMLElement).getBoundingClientRect().bottom,
      })

  deviceContextMenu.open(menuEvent, [{
    label: t('common.delete'),
    icon: Trash2,
    danger: true,
    disabled: revoking.value === device.device_id || syncingDeviceIDs.value.has(device.device_id),
    action: () => void revoke(device.device_id),
  }])
}

function openSync(device: TrustedDevice) {
  if (!device.online) return
  void router?.push(`/settings/mobile-devices/${device.device_id}/sync`)
}

function openMobileSyncHelp() {
  void Browser.OpenURL(mobileSyncHelpURL)
}

function platformLabel(platform: string) {
  switch (platform.toLowerCase()) {
    case 'android': return 'Android'
    case 'ios': return 'iOS'
    case 'ipados': return 'iPadOS'
    default: return platform
  }
}

function lastSyncedLabel(deviceID: string) {
  const timestamp = lastSyncedAtByDeviceID.value.get(deviceID)
  if (!timestamp) return t('settings.mobile_pairing.never_synced')
  return t('settings.mobile_pairing.last_synced', {
    time: new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(timestamp)),
  })
}

watch([pairingURL, qrContainer], ([url, container]) => {
  if (!container) { qr = null; return }
  if (!url) return
  if (!qr) {
    qr = new QRCodeStyling({
      width: 160,
      height: 160,
      type: 'canvas',
      data: url,
      image: '/airmedy.png',
      imageOptions: { hideBackgroundDots: true, imageSize: 0.3, margin: 4 },
      dotsOptions: { type: 'square', color: '#000000' },
      backgroundOptions: { color: '#ffffff' },
      qrOptions: { errorCorrectionLevel: 'H' },
    })
    container.innerHTML = ''
    qr.append(container)
  } else qr.update({ data: url })
}, { immediate: true })

watch(isBroadcasting, syncBroadcastTimer, { immediate: true })

onMounted(() => {
  void load()
  offTrustedDevicesChanged = Events.On('pairing:trusted-devices-changed', load)
  offBroadcastChanged = Events.On('pairing:broadcast-changed', load)
  offMobileSyncUpdated = Events.On('mobile-library-sync:updated', load)
})

onUnmounted(() => {
  offTrustedDevicesChanged?.()
  offTrustedDevicesChanged = null
  offBroadcastChanged?.()
  offBroadcastChanged = null
  offMobileSyncUpdated?.()
  offMobileSyncUpdated = null
  stopBroadcastTimer()
})
</script>

<template>
  <div class="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <SettingSection v-if="status?.running" :icon="Wifi" :label="t('settings.mobile_pairing.scan_title')">
      <div class="flex items-center justify-between gap-4 p-5">
        <div class="min-w-0">
          <p class="text-xs text-dim">{{ t('settings.mobile_pairing.scan_desc') }}</p>
          <button data-testid="mobile-sync-help" type="button" class="mt-3 flex items-center gap-2 text-xs text-dim transition-opacity cursor-pointer hover:opacity-100 hover:underline" @click="openMobileSyncHelp">
            <CircleHelp class="size-4 shrink-0" />
            <span class="underline-offset-2">{{ t('settings.mobile_pairing.sync_help') }}</span>
          </button>
        </div>
        <button class="rounded-lg p-2 text-dim transition-all hover:bg-foreground/[0.04] hover:opacity-70" :disabled="loading" :aria-label="t('settings.mobile_pairing.scan_title')" @click="load"><RefreshCw class="size-4" :class="{ 'animate-spin': loading }" /></button>
      </div>
      <NetworkAddressList :entries="networkEntries" :model-value="selectedIP" :show-copy="false" @update:model-value="selectedIP = $event" />
      <div v-if="pairingURL" class="flex flex-col items-center gap-2 p-5">
        <div class="mb-2 w-full"><p class="text-sm font-semibold">{{ t('settings.mobile_pairing.scan_title') }}</p></div>
        <div ref="qrContainer" class="overflow-hidden rounded-2xl" />
      </div>
    </SettingSection>

    <SettingSection v-if="status?.running" :icon="Radio" :label="t('common.mobile_pairing_broadcast.title')">
      <div class="flex items-center justify-between gap-4 p-5">
        <div class="min-w-0">
          <p class="text-xs text-dim">{{ isBroadcasting ? t('common.mobile_pairing_broadcast.broadcasting_desc', { seconds: broadcastSecondsRemaining }) : t('common.mobile_pairing_broadcast.description') }}</p>
          <p data-testid="broadcast-status" class="mt-2 flex h-4 items-center gap-2 text-xs text-foreground opacity-70" :class="{ invisible: !isBroadcasting }"><span class="size-2 animate-pulse rounded-full bg-primary" />{{ t('common.mobile_pairing_broadcast.broadcasting') }}</p>
        </div>
        <button
          data-testid="broadcast-button"
          class="inline-flex shrink-0 items-center justify-center whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium transition-all hover:scale-[1.02] disabled:opacity-50"
          :class="isBroadcasting ? 'text-subdued hover:bg-foreground/[0.04]' : 'bg-primary text-primary-foreground'"
          :disabled="broadcastingAction"
          @click="isBroadcasting ? stopBroadcast() : startBroadcast()"
        >
          <LoaderCircle v-if="broadcastingAction" class="size-4 animate-spin" />
          <X v-else-if="isBroadcasting" class="size-4" />
          <Radio v-else class="size-4" />
          <span class="ml-1.5">{{ t(isBroadcasting ? 'common.mobile_pairing_broadcast.stop' : 'common.mobile_pairing_broadcast.start') }}</span>
        </button>
      </div>
    </SettingSection>

    <SettingSection :icon="ShieldCheck" :label="t('settings.mobile_pairing.trusted_title')">
      <div v-if="devices.length === 0" class="p-5 text-sm text-dim">{{ t('settings.mobile_pairing.no_devices') }}</div>
      <div v-else class="divide-y divide-foreground/[0.08]">
        <div
          v-for="device in devices"
          :key="device.device_id"
          class="trusted-device-row flex items-center gap-3 p-5"
          :class="device.online ? 'cursor-pointer' : 'cursor-not-allowed'"
          :role="device.online ? 'button' : undefined"
          :tabindex="device.online ? 0 : undefined"
          @click="openSync(device)"
          @contextmenu="openDeviceMenu($event, device)"
          @keydown.enter="openSync(device)"
          @keydown.space.prevent="openSync(device)"
        >
          <div class="flex size-9 items-center justify-center rounded-full bg-foreground/[0.06]"><Smartphone class="size-4 text-dim" /></div>
          <div class="min-w-0 flex-1 flex flex-col gap-y-1">
            <div class="flex items-center gap-2"><p class="truncate text-sm font-medium text-foreground opacity-80">{{ device.display_name }}</p><Badge data-testid="device-status-badge" class="gap-1" :color="device.online ? 'var(--status-online)' : 'var(--text-muted)'"><span class="size-1 rounded-full bg-current" />{{ t(device.online ? 'settings.mobile_pairing.online' : 'settings.mobile_pairing.offline') }}</Badge></div>
            <p class="mt-0.5 text-xs text-dim">{{ platformLabel(device.platform) }} · {{ device.fingerprint }}</p>
            <p data-testid="device-last-synced" class="text-xs text-dim">{{ lastSyncedLabel(device.device_id) }}</p>
          </div>
          <button
            data-testid="device-actions-button"
            class="rounded-lg p-2 text-dim transition-all hover:bg-foreground/[0.04] hover:opacity-70"
            :aria-label="t('common.delete')"
            :disabled="revoking === device.device_id || syncingDeviceIDs.has(device.device_id)"
            @click.stop="openDeviceMenu($event, device)"
          >
            <LoaderCircle v-if="revoking === device.device_id" class="size-4 animate-spin" />
            <MoreHorizontal v-else class="size-4" />
          </button>
        </div>
      </div>
    </SettingSection>
    <ContextMenu
      :visible="deviceContextMenu.visible.value"
      :x="deviceContextMenu.x.value"
      :y="deviceContextMenu.y.value"
      :items="deviceContextMenu.items.value"
      @close="deviceContextMenu.close()"
    />
  </div>
</template>
