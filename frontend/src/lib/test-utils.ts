import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'

export function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {}
    }
  })
}

export function setupTestPinia() {
  const pinia = createPinia()
  setActivePinia(pinia)
  return pinia
}
