import { afterEach, describe, expect, it } from 'vitest'
import i18n from './index'

afterEach(() => { i18n.global.locale.value = 'en' })

describe('document language', () => {
  it('sets the initial locale and updates immediately for every supported language', () => {
    expect(document.documentElement.lang).toBe('en')
    for (const locale of i18n.global.availableLocales) {
      i18n.global.locale.value = locale
      expect(document.documentElement.lang).toBe(locale)
    }
  })
})
