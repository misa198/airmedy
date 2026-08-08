<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Events } from '@wailsio/runtime'
import { LoaderCircle, MoreHorizontal, RefreshCw, ShieldCheck, Smartphone, Trash2, Wifi } from '@lucide/vue'
import QRCodeStyling from 'qr-code-styling'
import { Badge } from '@airmedy/ui'
import * as MobilePairingService from '../../../bindings/airmedy/internal/infra/wails/mobilepairingservice'
import ContextMenu from '@/components/ContextMenu.vue'
import { useContextMenu } from '@/composables/useContextMenu'
import SettingSection from './SettingSection.vue'
import NetworkAddressList, { type NetworkAddressEntry } from './NetworkAddressList.vue'

const { t } = useI18n()

interface LocalAddress { ip: string; iface: string; kind: string }
interface PairingStatus { running: boolean; port: number; device_id: string; desktop_name: string; public_key: string; error: string; addresses: LocalAddress[] }
interface TrustedDevice { device_id: string; display_name: string; platform: string; fingerprint: string; paired_at: string; last_seen_at: string; online: boolean }

const status = ref<PairingStatus | null>(null)
const devices = ref<TrustedDevice[]>([])
const selectedIP = ref('')
const loading = ref(false)
const revoking = ref('')
const qrContainer = ref<HTMLElement | null>(null)
let qr: QRCodeStyling | null = null
let offTrustedDevicesChanged: (() => void) | null = null
const deviceContextMenu = useContextMenu()

const usableAddresses = computed(() => status.value?.addresses ?? [])
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
    devices.value = (await MobilePairingService.GetTrustedDevices() ?? []) as TrustedDevice[]
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
  if (revoking.value) return
  revoking.value = deviceID
  try { await MobilePairingService.RevokeDevice(deviceID); await load() }
  catch (error) { console.error('Failed to revoke mobile device:', error) }
  finally { revoking.value = '' }
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
    disabled: revoking.value === device.device_id,
    action: () => void revoke(device.device_id),
  }])
}

function platformLabel(platform: string) {
  switch (platform.toLowerCase()) {
    case 'android': return 'Android'
    case 'ios': return 'iOS'
    case 'ipados': return 'iPadOS'
    default: return platform
  }
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

onMounted(() => {
  void load()
  offTrustedDevicesChanged = Events.On('pairing:trusted-devices-changed', load)
})

onUnmounted(() => {
  offTrustedDevicesChanged?.()
  offTrustedDevicesChanged = null
})
</script>

<template>
  <div class="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <SettingSection v-if="status?.running" :icon="Wifi" :label="t('settings.mobile_pairing.scan_title')">
      <div class="flex items-center justify-between gap-4 p-5">
        <p class="text-xs text-dim">{{ t('settings.mobile_pairing.scan_desc') }}</p>
        <button class="rounded-lg p-2 text-dim transition-all hover:bg-foreground/[0.04] hover:opacity-70" :disabled="loading" :aria-label="t('settings.mobile_pairing.scan_title')" @click="load"><RefreshCw class="size-4" :class="{ 'animate-spin': loading }" /></button>
      </div>
      <NetworkAddressList :entries="networkEntries" :model-value="selectedIP" :show-copy="false" @update:model-value="selectedIP = $event" />
      <div v-if="pairingURL" class="flex flex-col items-center gap-2 p-5">
        <div class="mb-2 w-full"><p class="text-sm font-semibold">{{ t('settings.mobile_pairing.scan_title') }}</p></div>
        <div ref="qrContainer" class="overflow-hidden rounded-2xl" />
      </div>
    </SettingSection>

    <SettingSection :icon="ShieldCheck" :label="t('settings.mobile_pairing.trusted_title')">
      <div v-if="devices.length === 0" class="p-5 text-sm text-dim">{{ t('settings.mobile_pairing.no_devices') }}</div>
      <div v-else class="divide-y divide-foreground/[0.08]">
        <div
          v-for="device in devices"
          :key="device.device_id"
          class="trusted-device-row flex cursor-pointer items-center gap-3 p-5"
          role="button"
          tabindex="0"
          @contextmenu="openDeviceMenu($event, device)"
          @keydown.enter="openDeviceMenu($event, device)"
          @keydown.space.prevent="openDeviceMenu($event, device)"
        >
          <div class="flex size-9 items-center justify-center rounded-full bg-foreground/[0.06]"><Smartphone class="size-4 text-dim" /></div>
          <div class="min-w-0 flex-1 flex flex-col gap-y-1">
            <div class="flex items-center gap-2"><p class="truncate text-sm font-medium text-foreground opacity-80">{{ device.display_name }}</p><Badge data-testid="device-status-badge" class="gap-1" :color="device.online ? 'var(--primary)' : 'var(--text-muted)'"><span class="size-1 rounded-full bg-current" />{{ t(device.online ? 'settings.mobile_pairing.online' : 'settings.mobile_pairing.offline') }}</Badge></div>
            <p class="mt-0.5 text-xs text-dim">{{ platformLabel(device.platform) }} · {{ device.fingerprint }}</p>
          </div>
          <button
            data-testid="device-actions-button"
            class="rounded-lg p-2 text-dim transition-all hover:bg-foreground/[0.04] hover:opacity-70"
            :aria-label="t('common.delete')"
            :disabled="revoking === device.device_id"
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
