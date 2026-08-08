import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const { respond, on } = vi.hoisted(() => ({
  respond: vi.fn(),
  on: vi.fn(() => vi.fn()),
}))

vi.mock('../../bindings/airmedy/internal/infra/wails/mobilepairingservice', () => ({ Respond: respond }))
vi.mock('@wailsio/runtime', () => ({ Events: { On: on } }))

import { useMobilePairingStore } from './mobilePairing'

describe('mobile pairing store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    respond.mockReset()
    on.mockClear()
  })

  it('subscribes once and disposes its Wails event listener', () => {
    const off = vi.fn()
    on.mockReturnValueOnce(off)
    const store = useMobilePairingStore()
    store.init()
    store.init()
    expect(on).toHaveBeenCalledTimes(1)
    store.dispose()
    expect(off).toHaveBeenCalledOnce()
  })

  it('answers a pending request and closes it after a successful response', async () => {
    respond.mockResolvedValue(undefined)
    const store = useMobilePairingStore()
    store.pendingRequest = { request_id: 'request-1', mobile_id: 'device-1', display_name: 'My phone', platform: 'android', fingerprint: 'ABCD' }
    await store.respond(true)
    expect(respond).toHaveBeenCalledWith('request-1', true)
    expect(store.pendingRequest).toBeNull()
  })
})
