import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Events } from '@wailsio/runtime'
import * as MobilePairingService from '../../bindings/airmedy/internal/infra/wails/mobilepairingservice'

export interface PendingMobilePairingRequest {
  request_id: string
  mobile_id: string
  display_name: string
  platform: string
  fingerprint: string
}

export const useMobilePairingStore = defineStore('mobilePairing', () => {
  const pendingRequest = ref<PendingMobilePairingRequest | null>(null)
  const responding = ref(false)
  let _offFns: (() => void)[] = []
  let _initialized = false

  function init() {
    if (_initialized) return
    _initialized = true
    _offFns = [
      Events.On('pairing:request', (event: Events.WailsEvent) => {
        pendingRequest.value = event.data as PendingMobilePairingRequest
      }),
    ]
  }

  async function respond(accepted: boolean) {
    const request = pendingRequest.value
    if (!request || responding.value) return
    responding.value = true
    try {
      await MobilePairingService.Respond(request.request_id, accepted)
      pendingRequest.value = null
    } catch (error) {
      console.error('Failed to answer mobile pairing request:', error)
    } finally {
      responding.value = false
    }
  }

  function dispose() {
    _offFns.forEach(off => off())
    _offFns = []
    _initialized = false
    pendingRequest.value = null
  }

  return { pendingRequest, responding, init, respond, dispose }
})
