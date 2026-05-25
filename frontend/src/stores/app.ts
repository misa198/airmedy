import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Events } from '@wailsio/runtime'
import * as SettingsService from '../../bindings/airmedy/internal/infra/wails/settingsservice'
import * as UpdaterService from '../../bindings/airmedy/internal/infra/wails/updaterservice'
import { UpdateInfo } from '../../bindings/airmedy/internal/app/updater/models'

export const useAppStore = defineStore('app', () => {
  const theme = ref<'system' | 'light' | 'dark' | 'black'>('system')
  const language = ref('en')
  const startAtLogin = ref(false)
  const showTrayIcon = ref(true)
  const autoCheckUpdate = ref(true)
  const lastfmUsername = ref('')
  const eqEnabled = ref(true)
  const enableLrclib = ref(true)
  const enableKugou = ref(true)
  const preferMetadataLyrics = ref(true)
  const useOnlineArtistArtwork = ref(true)

  const updateInfo = ref<UpdateInfo | null>(null)
  const isCheckingUpdate = ref(false)
  const isUpdateDialogOpen = ref(false)
  const isUpdating = ref(false)
  const updateApplied = ref(false)
  const updateProgress = ref(0)

  const applyTheme = (newTheme: 'system' | 'light' | 'dark' | 'black') => {
    const root = document.documentElement
    const systemDark = newTheme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches
    if (newTheme === 'dark' || newTheme === 'black' || systemDark) {
      root.classList.add('dark')
    } else {
      root.classList.remove('dark')
    }
    if (newTheme === 'black') {
      root.classList.add('black')
    } else {
      root.classList.remove('black')
    }
  }

  const loadSettings = async () => {
    try {
      const settings = await SettingsService.GetSettings()
      if (settings) {
        if (settings.theme) theme.value = settings.theme as any
        if (settings.language) language.value = settings.language
        startAtLogin.value = !!settings.start_at_login
        showTrayIcon.value = settings.show_tray_icon !== false
        autoCheckUpdate.value = !!settings.auto_check_update
        lastfmUsername.value = settings.lastfm_username || ''
        eqEnabled.value = settings.eq_enabled !== false
        enableLrclib.value = settings.enable_lrclib !== false
        enableKugou.value = settings.enable_kugou !== false
        preferMetadataLyrics.value = settings.prefer_metadata_lyrics !== false
        useOnlineArtistArtwork.value = settings.use_online_artist_artwork !== false
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

  const restartApp = async () => {
    await UpdaterService.RestartApp()
  }

  const saveSettings = async () => {
    try {
      await SettingsService.SaveSettings({
        theme: theme.value,
        language: language.value,
        start_at_login: startAtLogin.value,
        show_tray_icon: showTrayIcon.value,
        auto_check_update: autoCheckUpdate.value,
        lastfm_username: lastfmUsername.value,
        eq_enabled: eqEnabled.value,
        enable_lrclib: enableLrclib.value,
        enable_kugou: enableKugou.value,
        prefer_metadata_lyrics: preferMetadataLyrics.value,
        use_online_artist_artwork: useOnlineArtistArtwork.value,
      })
    } catch (err) {
      console.error('Failed to save settings:', err)
      throw err
    }
  }

  const updateTheme = async (newTheme: 'system' | 'light' | 'dark' | 'black') => {
    theme.value = newTheme
    applyTheme(newTheme)
    await saveSettings()
  }

  const updateLanguage = async (newLanguage: string) => {
    language.value = newLanguage
    await saveSettings()
    Events.Emit('language:changed', newLanguage)
  }

  const updateStartAtLogin = async (enabled: boolean) => {
    startAtLogin.value = enabled
    await saveSettings()
  }

  const updateShowTrayIcon = async (enabled: boolean) => {
    showTrayIcon.value = enabled
    await saveSettings()
  }

  const updateAutoCheckUpdate = async (enabled: boolean) => {
    autoCheckUpdate.value = enabled
    await saveSettings()
  }

  const updateEQEnabled = async (enabled: boolean) => {
    eqEnabled.value = enabled
    await saveSettings()
  }

  const updateLastFmUsername = (username: string) => {
    lastfmUsername.value = username
  }

  const updateEnableLrclib = async (enabled: boolean) => {
    enableLrclib.value = enabled
    await saveSettings()
  }

  const updateEnableKugou = async (enabled: boolean) => {
    enableKugou.value = enabled
    await saveSettings()
  }

  const updatePreferMetadataLyrics = async (enabled: boolean) => {
    preferMetadataLyrics.value = enabled
    await saveSettings()
  }

  const updateUseOnlineArtistArtwork = async (enabled: boolean) => {
    useOnlineArtistArtwork.value = enabled
    await saveSettings()
  }

  // Watch for system theme changes if set to 'system'
  const _darkMQ = window.matchMedia('(prefers-color-scheme: dark)')
  const _onDarkMQChange = () => {
    if (theme.value === 'system') applyTheme('system')
  }
  _darkMQ.addEventListener('change', _onDarkMQChange)

  const _offUpdaterProgress = Events.On('updater:progress', (e: any) => {
    const data = e?.data ?? e
    if (data?.total > 0) {
      updateProgress.value = Math.round((data.downloaded / data.total) * 100)
    }
  })

  function dispose() {
    _darkMQ.removeEventListener('change', _onDarkMQChange)
    _offUpdaterProgress()
  }

  return {
    theme,
    language,
    startAtLogin,
    showTrayIcon,
    autoCheckUpdate,
    lastfmUsername,
    eqEnabled,
    enableLrclib,
    enableKugou,
    preferMetadataLyrics,
    useOnlineArtistArtwork,
    updateInfo,
    isCheckingUpdate,
    isUpdateDialogOpen,
    isUpdating,
    updateApplied,
    updateProgress,
    loadSettings,
    checkForUpdate,
    applyUpdate,
    restartApp,
    updateTheme,
    updateLanguage,
    updateStartAtLogin,
    updateShowTrayIcon,
    updateAutoCheckUpdate,
    updateEQEnabled,
    updateLastFmUsername,
    updateEnableLrclib,
    updateEnableKugou,
    updatePreferMetadataLyrics,
    updateUseOnlineArtistArtwork,
    dispose,
  }
})
