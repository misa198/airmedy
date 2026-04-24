import { createI18n } from 'vue-i18n'
import en from './en.json'
import zh from './zh.json'
import vi from './vi.json'
import ja from './ja.json'
import de from './de.json'
import fr from './fr.json'
import es from './es.json'
import pt from './pt.json'
import it from './it.json'
import ko from './ko.json'
import th from './th.json'
import ru from './ru.json'

const messages = {
  en,
  zh,
  vi,
  ja,
  de,
  fr,
  es,
  pt,
  it,
  ko,
  th,
  ru
}

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  messages,
})

export default i18n
