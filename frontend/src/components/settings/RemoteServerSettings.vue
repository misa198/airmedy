<script setup lang="ts">
import { Input, Switch } from '@airmedy/ui'
import { useAppStore } from '@/stores/app'
import { useDeviceStore } from '@/stores/device'
import { Dices, Save, Wifi, Info } from '@lucide/vue'
import QRCodeStyling from 'qr-code-styling'
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import * as RemoteServerService from '../../../bindings/airmedy/internal/infra/wails/remoteserverservice'
import SettingSection from './SettingSection.vue'
import SettingRow from './SettingRow.vue'
import NetworkAddressList from './NetworkAddressList.vue'

const { t } = useI18n()
const appStore = useAppStore()
const deviceStore = useDeviceStore()

interface LocalAddress {
  ip: string
  iface: string
  kind: string
}

interface ServerStatus {
  enabled: boolean
  running: boolean
  port: number
  password: string
  addresses: LocalAddress[]
}

const status = ref<ServerStatus | null>(null)

const regenerating = ref(false)
const toggling = ref(false)
const copiedUrl = ref('')
let copyTimeoutId: any = null
const selectedUrl = ref('')
const pinInput = ref('')
const pinSaving = ref(false)
const pinError = ref('')
const qrContainer = ref<HTMLElement | null>(null)
let qrInstance: QRCodeStyling | null = null

async function loadStatus() {
  try {
    const s = await RemoteServerService.GetStatus() as ServerStatus
    console.debug('[RemoteSettings] Loaded status:', s)
    status.value = s
    if (status.value?.password) {
      pinInput.value = status.value.password
      appStore.updateRemoteServerPassword(status.value.password)
    }
  } catch (err) {
    console.error('Failed to load remote server status:', err)
  }
}

async function toggleEnabled() {
  if (toggling.value || !status.value) return
  toggling.value = true
  const newState = !status.value.enabled
  console.debug('[RemoteSettings] Toggling enabled to:', newState)
  try {
    await RemoteServerService.SetEnabled(newState)
    await loadStatus()
  } catch (err) {
    console.error('Failed to toggle remote server:', err)
  } finally {
    toggling.value = false
  }
}

async function regeneratePin() {
  if (regenerating.value) return
  regenerating.value = true
  console.debug('[RemoteSettings] Regenerating PIN')
  try {
    const newPin = await RemoteServerService.RegeneratePassword()
    if (status.value) {
      status.value = { ...status.value, password: newPin as string }
    }
    pinInput.value = newPin as string
    pinError.value = ''
    appStore.updateRemoteServerPassword(newPin as string)
  } catch (err) {
    console.error('Failed to regenerate PIN:', err)
  } finally {
    regenerating.value = false
  }
}

async function savePin() {
  if (pinSaving.value) return
  const pin = pinInput.value.replace(/\D/g, '').slice(0, 4)
  pinInput.value = pin
  if (pin.length !== 4) {
    pinError.value = 'PIN must be 4 digits'
    return
  }
  pinError.value = ''
  pinSaving.value = true
  console.debug('[RemoteSettings] Saving PIN:', pin)
  try {
    await RemoteServerService.SetPassword(pin)
    if (status.value) {
      status.value = { ...status.value, password: pin }
    }
    appStore.updateRemoteServerPassword(pin)
  } catch (err) {
    pinError.value = 'Failed to save PIN'
    console.error('Failed to set PIN:', err)
  } finally {
    pinSaving.value = false
  }
}

function copyUrl(url: string) {
  if (copyTimeoutId) {
    clearTimeout(copyTimeoutId)
  }
  navigator.clipboard.writeText(url).then(() => {
    copiedUrl.value = url
    copyTimeoutId = setTimeout(() => {
      copiedUrl.value = ''
      copyTimeoutId = null
    }, 3000)
  })
}

const urls = computed(() => {
  if (!status.value?.running || !status.value.port || !status.value.addresses) return []
  const port = status.value.port
  return status.value.addresses.map(a => ({
    url: `http://${a.ip}:${port}`,
    ip: a.ip,
    iface: a.iface,
    kind: a.kind,
  }))
})

watch(urls, (list) => {
  if (list.length && !list.find(a => a.url === selectedUrl.value)) {
    const best = list.find(a => (a.kind === 'ethernet' || a.kind === 'wifi') && (a.url.includes('192.168.') || a.url.includes('10.') || a.url.includes('172.')))
      || list.find(a => a.kind === 'ethernet' || a.kind === 'wifi')
      || list[0]
    selectedUrl.value = best.url
  }
}, { immediate: true })

const selectedEntry = computed(() => urls.value.find(a => a.url === selectedUrl.value) ?? urls.value[0])
const qrUrl = computed(() => selectedEntry.value?.url ?? '')

watch([qrUrl, qrContainer], ([url, container]) => {
  if (!container) {
    qrInstance = null
    return
  }
  if (!url) return
  console.debug('[RemoteSettings] QR URL changed:', url)
  if (!qrInstance) {
    qrInstance = new QRCodeStyling({
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
    container.innerHTML = '' // Ensure container is empty
    qrInstance.append(container)
  } else {
    qrInstance.update({ data: url })
  }
}, { immediate: true })

const pinChanged = computed(() => pinInput.value !== (status.value?.password ?? ''))

onMounted(loadStatus)
</script>

<template>
  <div class="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
    <SettingSection :icon="Wifi" :label="t('settings.remote.title')">
        <!-- Toggle -->
        <SettingRow :title="t('settings.remote.enable')" :description="t('settings.remote.enable_desc')">
          <Switch :model-value="status?.enabled ?? false" :disabled="toggling" @update:model-value="toggleEnabled" />
        </SettingRow>

        <!-- Firewall info -->
        <div class="px-5 py-3 flex items-start gap-2 text-xs text-dim">
          <Info class="w-3 h-3 mt-0.5 shrink-0" />
          <span v-if="deviceStore.isMac">{{ t('settings.remote.firewall_macos') }}</span>
          <span v-else-if="deviceStore.isWindows">{{ t('settings.remote.firewall_windows') }}</span>
          <span v-else-if="deviceStore.isLinux">{{ t('settings.remote.firewall_linux', { port: status?.port ?? '…' }) }}</span>
        </div>

        <!-- Server URLs (when running) -->
        <template v-if="status?.running && urls.length > 0">
          <NetworkAddressList :entries="urls.map(item => ({ ...item, value: item.url, display: item.url }))" :model-value="selectedUrl" :copied-value="copiedUrl" @update:model-value="selectedUrl = $event" @copy="copyUrl($event.value)" />

          <!-- QR Code -->
          <div v-if="qrUrl" class="p-5 flex flex-col items-center gap-2">
            <div class="w-full mb-2">
              <p class="text-sm font-semibold">{{ t('settings.remote.scan_to_connect') }}</p>
              <p class="text-xs text-dim mt-1">{{ t('settings.remote.qr_select_hint') }}</p>
            </div>
            <div ref="qrContainer" class="rounded-2xl overflow-hidden" />
          </div>

          <!-- PIN -->
          <div class="p-5">
            <div class="flex items-start justify-between gap-x-2">
              <div>
                <p class="text-sm font-semibold">{{ t('settings.remote.access_pin') }}</p>
                <p class="text-xs text-dim mt-1">{{ t('settings.remote.access_pin_desc') }}</p>
              </div>
              <div class="flex items-center gap-4">
                <Input type="text" inputmode="numeric" maxlength="4" :model-value="pinInput"
                  @update:model-value="(v) => { pinInput = String(v).replace(/\D/g, '').slice(0, 4); pinError = '' }"
                  class="w-20 font-mono text-lg tracking-widest font-bold text-center"
                  :class="pinError ? 'border-red-500!' : ''" />
                <button v-if="pinChanged" @click="savePin" :disabled="pinSaving"
                  class="text-xs text-foreground opacity-80 hover:opacity-100 transition-opacity disabled:opacity-30">
                  <Save class="w-4 h-4" :class="{ 'animate-spin': pinSaving }" />
                </button>
                <button @click="regeneratePin" :disabled="regenerating"
                  class="text-xs text-subdued hover:opacity-100 transition-opacity disabled:opacity-30">
                  <Dices class="w-4 h-4" :class="{ 'animate-spin': regenerating }" />
                </button>
              </div>
            </div>
            <p v-if="pinError" class="text-xs text-red-500 mt-2">{{ pinError }}</p>
          </div>
        </template>
    </SettingSection>
  </div>
</template>
