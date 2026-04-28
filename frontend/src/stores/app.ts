import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as SettingsService from '../../bindings/airmedy/internal/infra/wails/settingsservice'

export const useAppStore = defineStore('app', () => {
  const theme = ref<'system' | 'light' | 'dark'>('system')
  const language = ref('en')
  const startAtLogin = ref(false)
  const lastfmUsername = ref('')

  const applyTheme = (newTheme: 'system' | 'light' | 'dark') => {
    const root = document.documentElement
    if (newTheme === 'dark' || (newTheme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
      root.classList.add('dark')
    } else {
      root.classList.remove('dark')
    }
  }

  const loadSettings = async () => {
    try {
      const settings = await SettingsService.GetSettings()
      if (settings) {
        if (settings.theme) theme.value = settings.theme as any
        if (settings.language) language.value = settings.language
        startAtLogin.value = !!settings.start_at_login
        lastfmUsername.value = settings.lastfm_username || ''
        applyTheme(theme.value)
      }
    } catch (err) {
      console.error('Failed to load settings:', err)
    }
  }

  const updateTheme = async (newTheme: 'system' | 'light' | 'dark') => {
    theme.value = newTheme
    applyTheme(newTheme)
    try {
      await SettingsService.SaveSettings({ 
        theme: newTheme, 
        language: language.value,
        start_at_login: startAtLogin.value,
        lastfm_username: lastfmUsername.value
      })
    } catch (err) {
      console.error('Failed to save theme setting:', err)
    }
  }

  const updateLanguage = async (newLanguage: string) => {
    language.value = newLanguage
    try {
      await SettingsService.SaveSettings({ 
        theme: theme.value, 
        language: newLanguage,
        start_at_login: startAtLogin.value,
        lastfm_username: lastfmUsername.value
      })
    } catch (err) {
      console.error('Failed to save language setting:', err)
    }
  }

  const updateStartAtLogin = async (enabled: boolean) => {
    startAtLogin.value = enabled
    try {
      await SettingsService.SaveSettings({ 
        theme: theme.value, 
        language: language.value,
        start_at_login: enabled,
        lastfm_username: lastfmUsername.value
      })
    } catch (err) {
      console.error('Failed to save startup setting:', err)
    }
  }

  const updateLastFmUsername = (username: string) => {
    lastfmUsername.value = username
  }

  // Watch for system theme changes if set to 'system'
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (theme.value === 'system') {
      applyTheme('system')
    }
  })

  return {
    theme,
    language,
    startAtLogin,
    lastfmUsername,
    loadSettings,
    updateTheme,
    updateLanguage,
    updateStartAtLogin,
    updateLastFmUsername
  }
})
