<script setup lang="ts">
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { useAppStore } from '@/stores/app'
import { Copy, Dices, Save, Wifi } from 'lucide-vue-next'
import QRCodeStyling from 'qr-code-styling'
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import * as RemoteServerService from '../../../bindings/airmedy/internal/infra/wails/remoteserverservice'

const { t } = useI18n()
const appStore = useAppStore()

interface ServerStatus {
  enabled: boolean
  running: boolean
  port: number
  password: string
  local_ips: string[]
}

const status = ref<ServerStatus | null>(null)

const regenerating = ref(false)
const toggling = ref(false)
const copiedUrl = ref('')
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
  navigator.clipboard.writeText(url).then(() => {
    copiedUrl.value = url
    setTimeout(() => { copiedUrl.value = '' }, 2000)
  })
}

const urls = computed(() => {
  if (!status.value?.running || !status.value.port || !status.value.local_ips) return []
  return status.value.local_ips.map(ip => `http://${ip}:${status.value!.port}`)
})

const qrUrl = computed(() => urls.value[0] ?? '')

watch([qrUrl, qrContainer], ([url, container]) => {
  if (!url || !container) return
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
    <section>
      <div class="flex items-center gap-2 mb-6 text-foreground opacity-60 select-none">
        <Wifi class="w-4 h-4" />
        <h2 class="text-sm font-bold uppercase tracking-wider">{{ t('settings.remote.title') }}</h2>
      </div>
      <div class="bg-card rounded-2xl border border-foreground/[0.06] divide-y divide-foreground/[0.06]">
        <!-- Toggle -->
        <div class="p-5 flex items-center justify-between gap-x-2">
          <div>
            <p class="text-sm font-semibold">{{ t('settings.remote.enable') }}</p>
            <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.remote.enable_desc') }}</p>
          </div>
          <Switch :model-value="status?.enabled ?? false" :disabled="toggling" @update:model-value="toggleEnabled" />
        </div>

        <!-- Server URLs (when running) -->
        <template v-if="status?.running && urls.length > 0">
          <div class="p-5">
            <p class="text-sm font-semibold mb-3">{{ t('settings.remote.access_urls') }}</p>
            <div class="space-y-2">
              <div v-for="url in urls" :key="url"
                class="flex items-center justify-between gap-2 bg-foreground/[0.02] border border-foreground/[0.04] rounded-lg px-3 h-[32px]">
                <code class="text-xs text-foreground opacity-80 truncate">{{ url }}</code>
                <button @click="copyUrl(url)"
                  class="text-xs text-foreground opacity-50 hover:opacity-100 transition-opacity shrink-0">
                  <span v-if="copiedUrl === url">{{ t('settings.remote.copied') }}</span>
                  <Copy v-else class="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>

          <!-- QR Code -->
          <div v-if="qrUrl" class="p-5 flex flex-col items-center gap-2">
            <div class="w-full mb-2">
              <p class="text-sm font-semibold">{{ t('settings.remote.scan_to_connect') }}</p>
              <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.remote.pin_required') }}</p>
            </div>
            <div ref="qrContainer" class="rounded-lg overflow-hidden" />
          </div>

          <!-- PIN -->
          <div class="p-5">
            <div class="flex items-start justify-between gap-x-2">
              <div>
                <p class="text-sm font-semibold">{{ t('settings.remote.access_pin') }}</p>
                <p class="text-xs text-foreground opacity-60 mt-1">{{ t('settings.remote.access_pin_desc') }}</p>
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
                  class="text-xs text-foreground opacity-50 hover:opacity-100 transition-opacity disabled:opacity-30">
                  <Dices class="w-4 h-4" :class="{ 'animate-spin': regenerating }" />
                </button>
              </div>
            </div>
            <p v-if="pinError" class="text-xs text-red-500 mt-2">{{ pinError }}</p>
          </div>
        </template>
      </div>
    </section>
  </div>
</template>
