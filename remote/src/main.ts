import { createApp } from 'vue'
import { createPinia } from 'pinia'
import VueVirtualScroller from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import App from './App.vue'
import './style.css'
import i18n from './locales'

async function bootstrap() {
  const app = createApp(App)
  app.use(createPinia())
  app.use(VueVirtualScroller)

  try {
    const res = await fetch('/api/settings')
    if (res.ok) {
      const settings = await res.json()
      if (settings.language) {
        ;(i18n.global.locale as any).value = settings.language
      }
    }
  } catch (err) {
    console.warn('Failed to fetch settings, using default language:', err)
  }

  app.use(i18n)
  app.mount('#app')
}

bootstrap()
