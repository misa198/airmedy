import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import * as SettingsService from '../../bindings/airmedy/internal/infra/wails/settingsservice'

export const useAppStore = defineStore('app', () => {
  const theme = ref<'system' | 'light' | 'dark'>('system')
  const language = ref('en')

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
      await SettingsService.SaveSettings({ theme: newTheme, language: language.value })
    } catch (err) {
      console.error('Failed to save theme setting:', err)
    }
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
    loadSettings,
    updateTheme
  }
})
