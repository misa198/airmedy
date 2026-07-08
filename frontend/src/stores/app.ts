import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Events } from '@wailsio/runtime'
import * as SettingsService from '../../bindings/airmedy/internal/infra/wails/settingsservice'
import * as UpdaterService from '../../bindings/airmedy/internal/infra/wails/updaterservice'
import * as WindowService from '../../bindings/airmedy/internal/infra/wails/windowservice'
import * as NormalizationService from '../../bindings/airmedy/internal/infra/wails/normalizationservice'
import * as AnalysisService from '../../bindings/airmedy/internal/infra/wails/analysisservice'
import * as EQService from '../../bindings/airmedy/internal/infra/wails/eqservice'
import { UpdateInfo } from '../../bindings/airmedy/internal/app/updater/models'
import { DEFAULT_SYNC_INTERVAL, isSyncInterval, type SyncInterval } from '@/lib/librarySync'
import { DEFAULT_MAX_QUEUE_SIZE, isMaxQueueSize, type MaxQueueSize } from '@/lib/queue'

export const NORMALIZATION_TARGET_LUFS_MIN = -24
export const NORMALIZATION_TARGET_LUFS_MAX = -6

// Mirrors domain.MaxCrossfadeSeconds; 0 = crossfade off (gapless).
export const CROSSFADE_MAX_SECONDS = 12

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
  const preferLocalLyrics = ref(true)
  const lyricsFolderEnabled = ref(false)
  const lyricsFolderPath = ref('')
  const lyricsSubfolderEnabled = ref(false)
  const lyricsSubfolderName = ref('')
  const useOnlineArtistArtwork = ref(true)
  const preferLocalArtistArtwork = ref(true)
  // Round-tripped only (set by the backend's version-gated rescan); never edited
  // in the UI, but must be preserved across saves so saving settings doesn't
  // wipe it and re-trigger a rescan.
  const lastScanVersion = ref('')
  // Round-tripped only (bumped by the mood-derivation percentile refresh job);
  // never edited in the UI, but must be preserved across saves so saving
  // settings doesn't wipe it and re-trigger mood re-derivation for all tracks.
  const moodDerivationVersion = ref(0)
  const preventSleepWhilePlaying = ref(false)
  const libraryAnalysisEnabled = ref(false)
  const normalizationEnabled = ref(false)
  const normalizationMode = ref<'off' | 'track' | 'album'>('track')
  const normalizationTargetLufs = ref(-14)
  const normalizationPreventClip = ref(true)
  const showPlayerIndicator = ref(true)
  const librarySyncInterval = ref<SyncInterval>(DEFAULT_SYNC_INTERVAL)
  const maxQueueSize = ref<MaxQueueSize>(DEFAULT_MAX_QUEUE_SIZE)
  const crossfadeSeconds = ref(0)
  const remoteServerEnabled = ref(false)
  const remoteServerPort = ref(0)
  const remoteServerPassword = ref('')

  // User-configurable delimiters for splitting multi-value tags.
  const DEFAULT_DELIMITERS = [';', '\\\\', ',']
  const artistDelimiters = ref<string[]>([...DEFAULT_DELIMITERS])
  const albumArtistDelimiters = ref<string[]>([...DEFAULT_DELIMITERS])
  const genreDelimiters = ref<string[]>([...DEFAULT_DELIMITERS])
  const composerDelimiters = ref<string[]>([...DEFAULT_DELIMITERS])

  const updateInfo = ref<UpdateInfo | null>(null)
  const isCheckingUpdate = ref(false)
  const isUpdateDialogOpen = ref(false)
  const isUpdating = ref(false)
  const updateApplied = ref(false)
  const updateProgress = ref(0)
  const updateChecked = ref(false)

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
    // Resolve 'system' to the actual dark/light value before sending to Go so
    // the backend doesn't need to re-detect the OS preference.
    const resolved = newTheme === 'system' ? (systemDark ? 'dark' : 'light') : newTheme
    WindowService.SetTitleBarTheme(resolved).catch(() => {})
  }

  const loadSettings = async (skipUpdateCheck = false) => {
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
        preferLocalLyrics.value = settings.prefer_local_lyrics !== false
        lyricsFolderEnabled.value = !!settings.lyrics_folder_enabled
        lyricsFolderPath.value = settings.lyrics_folder_path || ''
        lyricsSubfolderEnabled.value = !!settings.lyrics_subfolder_enabled
        lyricsSubfolderName.value = settings.lyrics_subfolder_name || ''
        useOnlineArtistArtwork.value = settings.use_online_artist_artwork !== false
        preferLocalArtistArtwork.value = settings.prefer_local_artist_artwork !== false
        lastScanVersion.value = settings.last_scan_version || ''
        moodDerivationVersion.value = settings.mood_derivation_version ?? 0
        preventSleepWhilePlaying.value = !!settings.prevent_sleep_while_playing
        showPlayerIndicator.value = settings.show_player_indicator !== false
        if (isSyncInterval(settings.library_sync_interval)) {
          librarySyncInterval.value = settings.library_sync_interval
        }
        if (isMaxQueueSize(settings.max_queue_size)) {
          maxQueueSize.value = settings.max_queue_size
        }
        crossfadeSeconds.value = Math.min(CROSSFADE_MAX_SECONDS, Math.max(0, Math.round(settings.crossfade_seconds ?? 0)))
        libraryAnalysisEnabled.value = !!settings.library_analysis_enabled
        normalizationEnabled.value = !!settings.normalization_enabled
        // DB default is 'track'; the mode select only offers Track/Album (never 'off').
        normalizationMode.value = (
          settings.normalization_mode && settings.normalization_mode !== 'off'
            ? settings.normalization_mode
            : 'track'
        ) as 'track' | 'album'
        normalizationTargetLufs.value = settings.normalization_target_lufs || -14
        normalizationPreventClip.value = settings.normalization_prevent_clip !== false
        remoteServerEnabled.value = !!settings.remote_server_enabled
        remoteServerPort.value = settings.remote_server_port ?? 0
        remoteServerPassword.value = settings.remote_server_password ?? ''
        // Preserve an intentional empty array (splitting disabled); only fall
        // back to defaults when the field is missing entirely.
        artistDelimiters.value = Array.isArray(settings.artist_delimiters) ? [...settings.artist_delimiters] : [...DEFAULT_DELIMITERS]
        albumArtistDelimiters.value = Array.isArray(settings.album_artist_delimiters) ? [...settings.album_artist_delimiters] : [...DEFAULT_DELIMITERS]
        genreDelimiters.value = Array.isArray(settings.genre_delimiters) ? [...settings.genre_delimiters] : [...DEFAULT_DELIMITERS]
        composerDelimiters.value = Array.isArray(settings.composer_delimiters) ? [...settings.composer_delimiters] : [...DEFAULT_DELIMITERS]
        applyTheme(theme.value)
      }

      // Check for updates on startup if enabled
      if (autoCheckUpdate.value && !skipUpdateCheck) {
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
      updateChecked.value = true
      if (info) {
        isUpdateDialogOpen.value = true
      }
    } catch (err) {
      throw err
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
        prefer_local_lyrics: preferLocalLyrics.value,
        lyrics_folder_enabled: lyricsFolderEnabled.value,
        lyrics_folder_path: lyricsFolderPath.value,
        lyrics_subfolder_enabled: lyricsSubfolderEnabled.value,
        lyrics_subfolder_name: lyricsSubfolderName.value,
        use_online_artist_artwork: useOnlineArtistArtwork.value,
        prefer_local_artist_artwork: preferLocalArtistArtwork.value,
        last_scan_version: lastScanVersion.value,
        mood_derivation_version: moodDerivationVersion.value,
        prevent_sleep_while_playing: preventSleepWhilePlaying.value,
        show_player_indicator: showPlayerIndicator.value,
        library_sync_interval: librarySyncInterval.value,
        max_queue_size: maxQueueSize.value,
        crossfade_seconds: crossfadeSeconds.value,
        library_analysis_enabled: libraryAnalysisEnabled.value,
        normalization_enabled: normalizationEnabled.value,
        normalization_mode: normalizationMode.value,
        normalization_target_lufs: normalizationTargetLufs.value,
        normalization_prevent_clip: normalizationPreventClip.value,
        remote_server_enabled: remoteServerEnabled.value,
        remote_server_port: remoteServerPort.value,
        remote_server_password: remoteServerPassword.value,
        artist_delimiters: [...artistDelimiters.value],
        album_artist_delimiters: [...albumArtistDelimiters.value],
        genre_delimiters: [...genreDelimiters.value],
        composer_delimiters: [...composerDelimiters.value],
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
    await EQService.SetEnabled(enabled)
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

  const updatePreferLocalLyrics = async (enabled: boolean) => {
    preferLocalLyrics.value = enabled
    await saveSettings()
  }

  const updateLyricsFolderEnabled = async (enabled: boolean) => {
    lyricsFolderEnabled.value = enabled
    await saveSettings()
  }

  const updateLyricsFolderPath = async (path: string) => {
    lyricsFolderPath.value = path
    await saveSettings()
  }

  const updateLyricsSubfolderEnabled = async (enabled: boolean) => {
    lyricsSubfolderEnabled.value = enabled
    await saveSettings()
  }

  const updateLyricsSubfolderName = async (name: string) => {
    lyricsSubfolderName.value = name
    await saveSettings()
  }

  const updateUseOnlineArtistArtwork = async (enabled: boolean) => {
    useOnlineArtistArtwork.value = enabled
    await saveSettings()
  }

  const updatePreferLocalArtistArtwork = async (enabled: boolean) => {
    preferLocalArtistArtwork.value = enabled
    await saveSettings()
  }

  const updatePreventSleepWhilePlaying = async (enabled: boolean) => {
    preventSleepWhilePlaying.value = enabled
    await saveSettings()
  }

  const updateShowPlayerIndicator = async (enabled: boolean) => {
    showPlayerIndicator.value = enabled
    await saveSettings()
  }

  const updateLibrarySyncInterval = async (interval: SyncInterval) => {
    librarySyncInterval.value = interval
    await saveSettings()
  }

  const updateMaxQueueSize = async (size: MaxQueueSize) => {
    maxQueueSize.value = size
    await saveSettings()
  }

  const updateCrossfadeSeconds = async (seconds: number) => {
    crossfadeSeconds.value = Math.min(CROSSFADE_MAX_SECONDS, Math.max(0, Math.round(seconds)))
    await saveSettings()
  }

  const updateLibraryAnalysisEnabled = async (enabled: boolean) => {
    libraryAnalysisEnabled.value = enabled
    // Mirrors the backend cross-toggle (AnalysisService.SetEnabled forces
    // NormalizationEnabled off when disabling): keep the local switch in
    // sync immediately instead of waiting on a refetch.
    if (!enabled) normalizationEnabled.value = false
    console.debug('[analysis] libraryAnalysisEnabled ->', enabled)
    await AnalysisService.SetLibraryAnalysisEnabled(enabled)
  }

  const updateNormalizationEnabled = async (enabled: boolean) => {
    normalizationEnabled.value = enabled
    console.debug('[normalization] enabled ->', enabled)
    await NormalizationService.SetEnabled(enabled)
  }

  const updateNormalizationMode = async (mode: 'off' | 'track' | 'album') => {
    normalizationMode.value = mode
    console.debug('[normalization] mode ->', mode)
    await NormalizationService.SetMode(mode)
  }

  const updateNormalizationTargetLufs = async (targetLufs: number) => {
    if (!Number.isFinite(targetLufs)) return
    const clamped = Math.min(
      NORMALIZATION_TARGET_LUFS_MAX,
      Math.max(NORMALIZATION_TARGET_LUFS_MIN, targetLufs)
    )
    normalizationTargetLufs.value = clamped
    console.debug('[normalization] targetLufs ->', clamped, targetLufs !== clamped ? `(clamped from ${targetLufs})` : '')
    await NormalizationService.SetTarget(clamped)
  }

  const updateNormalizationPreventClip = async (enabled: boolean) => {
    normalizationPreventClip.value = enabled
    console.debug('[normalization] preventClip ->', enabled)
    await NormalizationService.SetPreventClip(enabled)
  }

  const updateRemoteServerPassword = (password: string) => {
    remoteServerPassword.value = password
  }

  const updateDelimiters = async (
    field: 'artist' | 'albumArtist' | 'genre' | 'composer',
    value: string[],
  ) => {
    const target = {
      artist: artistDelimiters,
      albumArtist: albumArtistDelimiters,
      genre: genreDelimiters,
      composer: composerDelimiters,
    }[field]
    target.value = [...value]
    await saveSettings()
  }

  // Watch for system theme changes if set to 'system'
  const _darkMQ = typeof window.matchMedia === 'function' ? window.matchMedia('(prefers-color-scheme: dark)') : null
  const _onDarkMQChange = () => {
    if (theme.value === 'system') applyTheme('system')
  }
  _darkMQ?.addEventListener('change', _onDarkMQChange)

  const _offUpdaterProgress = Events.On('updater:progress', (e: any) => {
    const data = e?.data ?? e
    if (data?.total > 0) {
      updateProgress.value = Math.round((data.downloaded / data.total) * 100)
    }
  })

  function dispose() {
    _darkMQ?.removeEventListener('change', _onDarkMQChange)
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
    preferLocalLyrics,
    lyricsFolderEnabled,
    lyricsFolderPath,
    lyricsSubfolderEnabled,
    lyricsSubfolderName,
    useOnlineArtistArtwork,
    preferLocalArtistArtwork,
    preventSleepWhilePlaying,
    showPlayerIndicator,
    librarySyncInterval,
    maxQueueSize,
    crossfadeSeconds,
    libraryAnalysisEnabled,
    normalizationEnabled,
    normalizationMode,
    normalizationTargetLufs,
    normalizationPreventClip,
    updateInfo,
    isCheckingUpdate,
    isUpdateDialogOpen,
    isUpdating,
    updateApplied,
    updateProgress,
    updateChecked,
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
    updatePreferLocalLyrics,
    updateLyricsFolderEnabled,
    updateLyricsFolderPath,
    updateLyricsSubfolderEnabled,
    updateLyricsSubfolderName,
    updateUseOnlineArtistArtwork,
    updatePreferLocalArtistArtwork,
    updatePreventSleepWhilePlaying,
    updateShowPlayerIndicator,
    updateLibrarySyncInterval,
    updateMaxQueueSize,
    updateCrossfadeSeconds,
    updateLibraryAnalysisEnabled,
    updateNormalizationEnabled,
    updateNormalizationMode,
    updateNormalizationTargetLufs,
    updateNormalizationPreventClip,
    remoteServerEnabled,
    remoteServerPort,
    remoteServerPassword,
    updateRemoteServerPassword,
    artistDelimiters,
    albumArtistDelimiters,
    genreDelimiters,
    composerDelimiters,
    updateDelimiters,
    dispose,
  }
})
