import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'

vi.mock('../../bindings/airmedy/internal/infra/wails/mobilepairingservice', () => ({ Respond: vi.fn() }))
vi.mock('@wailsio/runtime', () => ({ Events: { On: vi.fn(() => vi.fn()) } }))

import MobilePairingDialog from './MobilePairingDialog.vue'
import { useMobilePairingStore } from '@/stores/mobilePairing'

describe('MobilePairingDialog', () => {
  it('shows the requesting device and accepts it', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useMobilePairingStore()
    store.pendingRequest = { request_id: 'r1', mobile_id: 'm1', display_name: 'My phone', platform: 'android', fingerprint: 'ABCD-1234' }
    const respond = vi.spyOn(store, 'respond').mockResolvedValue()
    const i18n = createI18n({ legacy: false, locale: 'en', messages: { en: { settings: { mobile_pairing: { request_title: 'Connect', request_desc: 'Description', fingerprint: 'Fingerprint', accept: 'Accept', decline: 'Decline' } } } } })
    const wrapper = mount(MobilePairingDialog, { global: { plugins: [pinia, i18n], stubs: { Modal: { template: '<div><slot /><slot name="footer" /></div>' } } } })
    expect(wrapper.text()).toContain('My phone')
    await wrapper.get('button:last-child').trigger('click')
    expect(respond).toHaveBeenCalledWith(true)
  })
})
