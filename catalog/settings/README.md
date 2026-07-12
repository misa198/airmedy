# Settings

## Summary

Application-level settings: UI theme, display language, launch-at-login. Settings are persisted in SQLite and loaded at startup. A separate Settings view provides UI for all configuration including Library management and EQ (covered in their own catalog entries).

## Files

| File                                           | Purpose                     |
| ---------------------------------------------- | --------------------------- |
| `internal/app/config/config.go`                | Paths and AppSettings model |
| `internal/app/config/meta.go`                  | App constants: Version, RepoOwner, RepoName, AppName, AppDesc, GitHubURL, LicenseURL |
| `internal/app/lastfm/service.go`               | Last.fm scrobbling and auth |
| `internal/infra/sqlite/settings_repository.go` | SQLite persistence          |
| `internal/infra/wails/settings_service.go`     | Wails binding               |
| `frontend/src/stores/app.ts`                   | Frontend settings state     |
| `frontend/src/views/SettingsView.vue`          | Settings UI                 |

## AppSettings Model

```go
type AppSettings struct {
    Language               string              // BCP 47 language tag, e.g., "en", "zh", "ja"
    Theme                  string              // "system", "light", "dark", "black"
    StartAtLogin           bool
    AutoCheckUpdate        bool
    LastFmUsername         string              // Connected Last.fm account name
    EQEnabled              bool
    EnableLrclib           bool                // enable LRClib lyrics provider
    EnableKugou            bool                // enable Kugou lyrics provider
    PreferLocalLyrics      bool                // prefer local/embedded lyrics over fetched
    LyricsFolderEnabled    bool                // also search a dedicated lyrics folder
    LyricsFolderPath       string              // chosen dedicated lyrics folder (flat, basename match)
    LyricsSubfolderEnabled bool                // also search a subfolder next to each track
    LyricsSubfolderName    string              // subfolder name (single safe path segment)
    UseOnlineArtistArtwork bool                // gate: off → Deezer image never shown (even if cached)
    PreferLocalArtistArtwork bool              // nested (online on only): local/manual image suppresses online
    LastScanVersion        string              // app version of last artist-image rescan
    PreventSleepWhilePlaying bool             // prevent OS sleep during playback
    LibraryAnalysisEnabled  bool               // opt-in: gates the analysis worker pool entirely (default off)
    LibraryAnalysisWorkerCount int             // desired concurrent analysis workers; default 2, runtime-clamped to [1, numCPU/2]
    NormalizationEnabled    bool               // volume normalization on/off; cannot be true while LibraryAnalysisEnabled is false
    NormalizationMode       string             // "off", "track", "album"
    NormalizationTargetLUFS float64            // target loudness, default -14 (domain.DefaultTargetLUFS)
    NormalizationPreventClip bool              // clamp gain so gain+truePeak ≤ 0 dBFS
    ArtistDelimiters       []string            // multi-value split delimiters (default [";","\\",","]; empty = no split)
    AlbumArtistDelimiters  []string
    GenreDelimiters        []string
    ComposerDelimiters     []string
    MaxQueueSize            int                // play queue cap, incl. current track; one of domain.ValidMaxQueueSizes (100/500/1000/2000/3000), default 1000
    CrossfadeSeconds        int                // track-transition overlap in seconds, clamped to [0, domain.MaxCrossfadeSeconds]=12; 0 = off (gapless), default 0
    BlendArtworkDuringCrossfade bool           // fullscreen cover blend during automatic crossfade, default true
    HighContrastLyrics       bool              // fullscreen glass lyrics panel; false renders lyrics directly over artwork, default true
    AutoAdvanceNotificationsEnabled bool       // macOS-only silent notification when playback automatically advances, default true
}
```

The four `*Delimiters` lists are stored as JSON TEXT columns. `domain.ValidateDelimiters` runs
in `SettingsService.SaveSettings` before persisting (empty list allowed; rejects empty/dup/>5-char
entries). See [metadata](../metadata/README.md) for `SplitNames` and [library](../library/README.md)
for the delimiter-aware re-sync that applies changes to existing tracks.

Stored in the `app_settings` table (single-row, id always = 1). Sensitive session keys are stored in the OS-native secure vault (Keychain, Credential Manager, etc.) via `github.com/zalando/go-keyring`.

## Config (Data Paths)

```go
type Config struct {
    DataDir string  // $XDG_DATA_HOME/airmedy
}

func (c *Config) DBPath() string          // airmedy.db
func (c *Config) IndexPath() string       // airmedy.bleve
func (c *Config) ArtworkCachePath() string // artwork/
func (c *Config) LogPath() string         // logs/airmedy.log
```

## Wails-Exposed Methods

```typescript
GetSettings(): AppSettings
SaveSettings(settings: AppSettings): void
GetAppInfo(): AppInfo      // name, version, build info
OpenAppDataFolder(): void  // opens $XDG_DATA_HOME/airmedy in Finder/Explorer
GetProgress(): AnalysisProgress
GetWorkerCountInfo(): { count: number; max: number }
SetWorkerCount(count: number): void
```

## Frontend Store (`stores/app.ts`)

```typescript
interface AppStore {
  // Settings state
  theme: "system" | "light" | "dark" | "black";
  language: string;
  startAtLogin: boolean;
  autoCheckUpdate: boolean;
  lastfmUsername: string;
  eqEnabled: boolean;
  enableLrclib: boolean;
  enableKugou: boolean;
  preferLocalLyrics: boolean;
  lyricsFolderEnabled: boolean;
  lyricsFolderPath: string;
  lyricsSubfolderEnabled: boolean;
  lyricsSubfolderName: string;
  useOnlineArtistArtwork: boolean;
  preventSleepWhilePlaying: boolean;
  crossfadeSeconds: number; // 0–CROSSFADE_MAX_SECONDS (12); 0 = off; slider in PlaybackSettings.vue
  blendArtworkDuringCrossfade: boolean; // default true; fullscreen only
  highContrastLyrics: boolean; // default true; fullscreen lyrics only
  autoAdvanceNotificationsEnabled: boolean; // default true; macOS-only automatic track-change notification
  artistDelimiters: string[];
  albumArtistDelimiters: string[];
  genreDelimiters: string[];
  composerDelimiters: string[];
  libraryAnalysisWorkerCount: number;
  libraryAnalysisMaxWorkerCount: number;
  // Update state
  updateInfo: UpdateInfo | null;
  isCheckingUpdate: boolean;
  isUpdateDialogOpen: boolean;
  isUpdating: boolean;
  updateApplied: boolean;
  updateChecked: boolean; // true after first CheckForUpdate completes
  updateProgress: number; // 0-100, driven by updater:progress event
  // Methods
  loadSettings(): Promise<void>;
  applyTheme(theme: string): void;
  updateTheme(theme: string): Promise<void>;
  updateLanguage(lang: string): Promise<void>;
  updateStartAtLogin(enabled: boolean): Promise<void>;
  updateAutoCheckUpdate(enabled: boolean): Promise<void>;
  updateEQEnabled(enabled: boolean): Promise<void>;
  updateLastFmUsername(username: string): void;
  updateEnableLrclib(enabled: boolean): Promise<void>;
  updateEnableKugou(enabled: boolean): Promise<void>;
  updatePreferLocalLyrics(enabled: boolean): Promise<void>;
  updateLyricsFolderEnabled(enabled: boolean): Promise<void>;
  updateLyricsFolderPath(path: string): Promise<void>;
  updateLyricsSubfolderEnabled(enabled: boolean): Promise<void>;
  updateLyricsSubfolderName(name: string): Promise<void>;
  updateUseOnlineArtistArtwork(enabled: boolean): Promise<void>;
  updatePreferLocalArtistArtwork(enabled: boolean): Promise<void>;
  updatePreventSleepWhilePlaying(enabled: boolean): Promise<void>;
  updateDelimiters(field: "artist" | "albumArtist" | "genre" | "composer", value: string[]): Promise<void>;
  updateLibraryAnalysisWorkerCount(count: number): Promise<void>;
  checkForUpdate(): Promise<void>;
  applyUpdate(): Promise<void>;
  restartApp(): Promise<void>;
  dispose(): void;
}
```

Each `update*()` method calls `SettingsService.SaveSettings()` with the full settings object (all fields at once, not partial).

`loadSettings()` also fetches `AnalysisService.GetWorkerCountInfo()` separately to
hydrate the library-analysis worker slider's current value and its runtime max.

`applyTheme()` manages CSS classes on `document.documentElement`. `dark` theme adds `.dark`; `black` theme adds both `.dark` and `.black` (pure black bg override for OLED screens); `light` removes both. When theme is `system`, it respects `prefers-color-scheme` media query (resolves to dark, not black).

`updateLanguage()` sets `i18n.locale.value` immediately for instant locale switch without reload.

## Auto-Update Implementation

`internal/app/updater/Service` uses the GitHub Releases API directly (`api.github.com/repos/misa198/airmedy/releases/latest`) — no third-party updater library. Flow:

1. `CheckForUpdate()` → fetches latest release JSON, selects platform asset by OS/arch + extension (`.zip` for macOS/Windows, `.tar.gz` for Linux), optionally fetches `SHA256SUMS` for verification. Caches the pending release.
2. `DownloadAndApply(ctx, progress)` → downloads asset with streaming progress callback, verifies SHA256 if available, then calls the per-platform `applyUpdate` (build-tagged: `service_darwin.go` / `service_windows.go` / `service_other.go`). `applyUpdate` returns a **staging path** that is stashed on the service for the relaunch step (no in-place patch of the running binary on any platform).
3. `infra/wails/UpdaterService.DownloadAndApply()` wraps the above and emits `updater:progress` events (`{ downloaded, total, percentage }`) to the frontend event bus during download.
4. `RestartApp()` → `Service.PrepareRestart()` → per-platform `relaunch(bundlePath, exe, pid)`, which waits for the current process to exit before swapping/installing, then relaunches.

Per-platform `applyUpdate` + `relaunch`:

- **macOS** (`service_darwin.go`): extracts the full `.app` bundle from the zip to a staging path (`<bundle>.app.update`); `postUpdate` updates `Info.plist` version fields. `relaunch` spawns a background `sh -c` that polls `kill -0 <pid>` until exit, swaps the bundle, ad-hoc codesigns (`codesign --force --deep --sign -`), removes quarantine (`xattr -d com.apple.quarantine`), and reopens via `open`.
- **Windows** (`service_windows.go`): the app installs under Program Files (admin), so a normal-user process cannot swap the running `.exe`. `applyUpdate` extracts the NSIS **installer** `.exe` from the release zip to a temp staging path; `relaunch` waits for exit then runs the installer **silently and elevated (UAC)** and relaunches the app.
- **Linux** (`service_other.go`, `!darwin && !windows`): `applyUpdate` extracts the `airmedy` binary. `relaunch` replaces the binary in place when the install dir is writable (user-local tar.gz install); for root-owned installs (e.g. `/usr/local/bin`) it installs the staged binary elevated via `pkexec sh -c 'cp -f … && chmod 755 …'`.

Version constant moved from `internal/domain/version.go` (deleted) to `internal/app/config/meta.go` as `config.Version`.

## Settings View Structure

`SettingsView.vue` uses a tab layout. When navigation to the settings view occurs, the application automatically closes the fullscreen player overlay and the mini player window to provide a focused configuration environment.

| Tab          | Content                                                                    |
| ------------ | -------------------------------------------------------------------------- |
| General      | Theme selector, Language picker, Start at Login, Auto-check updates toggle |
| Library      | Watched folders list, Add/Remove folder, Sync All, Reindex; **Tag Delimiters** section — 4 chip inputs (`DelimiterInput.vue`) for artist/album-artist/genre/composer split delimiters with inline validation, plus a persistent "Sync Library to apply" hint (`DelimitersPendingResync`) shown while pending; **Library Analysis** section — enable toggle, live progress/readiness text, and a concurrent-worker slider when more than one worker is available |
| Integrations | Last.fm account + lyrics providers (LRClib, Kugou), prefer-local toggle, lyrics-subfolder toggle + validated name input (matched case-insensitively, with a hint), and dedicated lyrics folder toggle + picker (reuses `LibraryService.SelectFolder`). Toggles with conditional sub-settings use `SettingExpandableRow.vue` (header + `#control` slot + animated, inset `#expanded` slot) so the sub-setting reads as nested under its toggle. |
| Playback     | EQ profiles and band sliders, prevent-sleep toggle, Fullscreen High Contrast Lyrics toggle, and Volume Normalization controls (`PlaybackSettings.vue`) |
| Remote       | Control remote server (enable/disable), change or regenerate access PIN, show QR code, and choose between reachable IP addresses grouped by network interface (`RemoteServerSettings.vue`) |
| About        | App version, GitHub link, License, Open Data Folder button                 |

The Playback route accepts `?section=equalizer`. `SettingsView` waits for the
Playback panel to render, then smoothly scrolls the Equalizer section into view;
the player footer quick-settings menu uses this shortcut.

## Last.fm Integration

Authentication is handled via Wails v3 custom protocol (`airmedy://auth`). When a user authorizes the app, Last.fm redirects to this deep link, which is captured by the Go backend to exchange the token for a permanent session key.

- **Scrobbling**: Automatic when a track playback exceeds 50% duration or 4 minutes.
- **Now Playing**: Updated immediately on track start.
- **Love Sync**: Favoriting a track in Airmedy automatically "Loves" it on Last.fm.
- **Secure Storage**: Session keys never touch the database or disk in plain text; they are stored in the OS-native keyring.

## Theme Application

At app startup (`App.vue` onMounted):

1. `appStore.loadSettings()` — fetches from backend.
2. `appStore.applyTheme(theme)` — applies dark/light class.
3. On `appStore.theme` change (watcher) → re-apply.

Dynamic artwork colors (`--dynamic-primary`, etc.) are layered on top of the theme and also re-applied when the theme changes (to recompute RGBA opacity variants).

`applyTheme()` also calls `WindowService.SetTitleBarTheme(resolvedTheme)` after updating CSS classes. This updates the native Windows title bar colour (via `DwmSetWindowAttribute` — caption colour attribute 35, text colour 36) to match `--bg-main` for the current theme. On macOS the title bar is already hidden; on Linux this is a no-op (no Wails API available). The initial title bar colour is also set at window creation in `main.go` via `Windows.CustomTheme` / `Windows.Theme` (read from persisted settings).

## Language Support

12 locales available: `de`, `en`, `es`, `fr`, `it`, `ja`, `ko`, `pt`, `ru`, `th`, `vi`, `zh`.

The language picker in Settings renders all 12 options. On select, `updateLanguage()` saves to DB and immediately switches `vue-i18n` locale — no restart needed.

## Start at Login

Implemented via `github.com/emersion/go-autostart`. On macOS, creates a Launch Agent plist. On Linux, creates a `.desktop` entry in `~/.config/autostart/`. On Windows, creates a registry entry. Toggled by `SaveSettings()` when `start_at_login` changes.

## Database Migration History

Settings evolved across multiple migrations:

| Migration | Change                                                           |
| --------- | ---------------------------------------------------------------- |
| 000005    | Create `app_settings` table with `language`, `id = 1` constraint |
| 000006    | Add `theme TEXT DEFAULT 'system'` column                         |
| 000010    | Add `lastfm_username` column for integration UI                  |
| 000011    | Add `auto_check_update`, `start_at_login`                        |
| 000013    | Add `eq_enabled` column for persistent EQ toggle                 |
| 000014    | Add `lrclib_mode` setting; metadata lyrics columns in `lyrics` table |
| 000015    | Add `artwork_key` column to `artists` table                      |
| 000016    | Add `use_online_artist_artwork` setting column                   |
| 000017    | Add `enable_lrclib`, `enable_kugou`, `prefer_metadata_lyrics`; all `BOOLEAN NOT NULL DEFAULT 1` |
| 000019    | Add `prevent_sleep_while_playing BOOLEAN NOT NULL DEFAULT 0`     |
| 000023    | Add `artwork_source` column to `artists` table                   |
| 000024    | Add `prefer_local_artist_artwork`, `last_scan_version` settings  |
| 000026    | Drop `prefer_local_artist_artwork` (derived from online toggle)   |
| 000025    | Split artist artwork into per-source key columns                 |
| 000027    | Add `lyrics_folder_enabled`, `lyrics_folder_path`, `lyrics_subfolder_enabled`, `lyrics_subfolder_name` (folder + subfolder lyrics lookup) |
| 000028    | Re-add `prefer_local_artist_artwork BOOLEAN NOT NULL DEFAULT 1` (nested sub-toggle under online artwork) |
| 000049    | Add `blend_artwork_during_crossfade BOOLEAN NOT NULL DEFAULT 1` |
| 000060    | Add `auto_advance_notifications_enabled BOOLEAN NOT NULL DEFAULT 1` for the macOS silent automatic-track notification |
| 000030    | Add `artist_delimiters`, `album_artist_delimiters`, `genre_delimiters`, `composer_delimiters` (TEXT JSON arrays, default `'[";","\\",","]'`) |
| 000032    | Add `,` to the default delimiter set for rows still on the previous default `'[";","\\"]'` |
| 000033    | Update default delimiters: change single backslash `\` to double backslash `\\` (JSON `'[";","\\\\",","]'`) for rows still on the previous default |
| 000034    | Add `normalization_enabled` (0), `normalization_mode` ('off'), `normalization_target_lufs` (-14), `normalization_prevent_clip` (1), `library_analysis_enabled` (0) — volume normalization + library analysis opt-in settings (also adds `track_features` table + `tracks.analyzed_version`) |
| 000047    | Add `library_analysis_worker_count INTEGER NOT NULL DEFAULT 2` — persists the desired concurrent library-analysis worker count; down intentionally keeps the column |
