import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as SettingsService from '../../bindings/airmedy/internal/infra/wails/settingsservice'
import * as UpdaterService from '../../bindings/airmedy/internal/infra/wails/updaterservice'
import { UpdateInfo } from '../../bindings/airmedy/internal/app/updater/models'

export const useAppStore = defineStore('app', () => {
  const theme = ref<'system' | 'light' | 'dark'>('system')
  const language = ref('en')
  const startAtLogin = ref(false)
  const autoCheckUpdate = ref(true)
  const lastfmUsername = ref('')
  const eqEnabled = ref(true)
  
  const updateInfo = ref<UpdateInfo | null>(null)
  const isCheckingUpdate = ref(false)
  const isUpdateDialogOpen = ref(false)
  const isUpdating = ref(false)
  const updateApplied = ref(false)

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
        autoCheckUpdate.value = !!settings.auto_check_update
        lastfmUsername.value = settings.lastfm_username || ''
        eqEnabled.value = settings.eq_enabled !== false
        applyTheme(theme.value)
      }
      
      // Check for updates on startup if enabled
      if (autoCheckUpdate.value) {
        checkForUpdate()
      }
    } catch (err) {
      console.error('Failed to load settings:', err)
    }
  }

  const checkForUpdate = async () => {
    if (isCheckingUpdate.value) return
    isCheckingUpdate.value = true
    try {
      const info = await UpdaterService.CheckForUpdate()
      updateInfo.value = info
    } catch (err) {
      console.error('Failed to check for updates:', err)
    } finally {
      isCheckingUpdate.value = false
    }
  }

  const applyUpdate = async () => {
    if (isUpdating.value || updateApplied.value) return
    isUpdating.value = true
    try {
      await UpdaterService.DownloadAndApply()
      updateApplied.value = true
    } catch (err) {
      console.error('Failed to apply update:', err)
      throw err
    } finally {
      isUpdating.value = false
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
        auto_check_update: autoCheckUpdate.value,
        lastfm_username: lastfmUsername.value,
        eq_enabled: eqEnabled.value
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
        auto_check_update: autoCheckUpdate.value,
        lastfm_username: lastfmUsername.value,
        eq_enabled: eqEnabled.value
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
        auto_check_update: autoCheckUpdate.value,
        lastfm_username: lastfmUsername.value,
        eq_enabled: eqEnabled.value
      })
    } catch (err) {
      console.error('Failed to save startup setting:', err)
    }
  }

  const updateAutoCheckUpdate = async (enabled: boolean) => {
    autoCheckUpdate.value = enabled
    try {
      await SettingsService.SaveSettings({ 
        theme: theme.value, 
        language: language.value,
        start_at_login: startAtLogin.value,
        auto_check_update: enabled,
        lastfm_username: lastfmUsername.value,
        eq_enabled: eqEnabled.value
      })
    } catch (err) {
      console.error('Failed to save auto update setting:', err)
    }
  }

  const updateEQEnabled = async (enabled: boolean) => {
    eqEnabled.value = enabled
    try {
      await SettingsService.SaveSettings({
        theme: theme.value,
        language: language.value,
        start_at_login: startAtLogin.value,
        auto_check_update: autoCheckUpdate.value,
        lastfm_username: lastfmUsername.value,
        eq_enabled: enabled
      })
    } catch (err) {
      console.error('Failed to save EQ enabled setting:', err)
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
    autoCheckUpdate,
    lastfmUsername,
    eqEnabled,
    updateInfo,
    isCheckingUpdate,
    isUpdateDialogOpen,
    isUpdating,
    updateApplied,
    loadSettings,
    checkForUpdate,
    applyUpdate,
    updateTheme,
    updateLanguage,
    updateStartAtLogin,
    updateAutoCheckUpdate,
    updateEQEnabled,
    updateLastFmUsername
  }
})
