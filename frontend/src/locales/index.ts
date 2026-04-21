import { createI18n } from 'vue-i18n'

const messages = {
  en: {
    common: {
      play: 'Play',
      shuffle: 'Shuffle',
      home: 'Home',
      library: 'Library',
    }
  }
}

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  messages,
})

export default i18n