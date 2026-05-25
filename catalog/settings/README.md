# Settings

## Summary

Application-level settings: UI theme, display language, launch-at-login. Settings are persisted in SQLite and loaded at startup. A separate Settings view provides UI for all configuration including Library management and EQ (covered in their own catalog entries).

## Files

| File                                           | Purpose                     |
| ---------------------------------------------- | --------------------------- |
| `internal/app/config/config.go`                | Paths and AppSettings model |
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
    PreferMetadataLyrics   bool                // prefer embedded lyrics over fetched
    UseOnlineArtistArtwork bool                // fetch artist artwork from Deezer
}
```

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
  preferMetadataLyrics: boolean;
  useOnlineArtistArtwork: boolean;
  // Update state
  updateInfo: UpdateInfo | null;
  isCheckingUpdate: boolean;
  isUpdateDialogOpen: boolean;
  isUpdating: boolean;
  updateApplied: boolean;
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
  updatePreferMetadataLyrics(enabled: boolean): Promise<void>;
  updateUseOnlineArtistArtwork(enabled: boolean): Promise<void>;
  checkForUpdate(): Promise<void>;
  applyUpdate(): Promise<void>;
  restartApp(): Promise<void>;
  dispose(): void;
}
```

Each `update*()` method calls `SettingsService.SaveSettings()` with the full settings object (all 10 fields at once, not partial).

`applyTheme()` manages CSS classes on `document.documentElement`. `dark` theme adds `.dark`; `black` theme adds both `.dark` and `.black` (pure black bg override for OLED screens); `light` removes both. When theme is `system`, it respects `prefers-color-scheme` media query (resolves to dark, not black).

`updateLanguage()` sets `i18n.locale.value` immediately for instant locale switch without reload.

## Auto-Update Implementation

`internal/app/updater/Service` uses the GitHub Releases API directly (`api.github.com/repos/misa198/airmedy/releases/latest`) — no third-party updater library. Flow:

1. `CheckForUpdate()` → fetches latest release JSON, selects platform asset by OS/arch + extension (`.zip` for macOS/Windows, `.tar.gz` for Linux), optionally fetches `SHA256SUMS` for verification. Caches the pending release.
2. `DownloadAndApply(ctx, progress)` → downloads asset with streaming progress callback, verifies SHA256 if available, extracts named binary from archive, applies atomically via `github.com/inconshreveable/go-update`, then runs platform-specific `postUpdate`.
3. `infra/wails/UpdaterService.DownloadAndApply()` wraps the above and emits `updater:progress` events (`{ downloaded, total, percentage }`) to the frontend event bus during download.
4. `restartApp()` on macOS uses `open <bundle>.app`; on other platforms re-execs the binary directly.

macOS `postUpdate` updates `Info.plist` version fields. Pre-signed CI artifacts should not be re-signed with ad-hoc — the `codesign` call was removed.

## Settings View Structure

`SettingsView.vue` uses a tab layout. When navigation to the settings view occurs, the application automatically closes the fullscreen player overlay and the mini player window to provide a focused configuration environment.

| Tab          | Content                                                                    |
| ------------ | -------------------------------------------------------------------------- |
| General      | Theme selector, Language picker, Start at Login, Auto-check updates toggle |
| Library      | Watched folders list, Add/Remove folder, Sync All, Reindex                 |
| Integrations | Last.fm account + lyrics providers (LRClib, Kugou, metadata preference)   |
| Playback     | EQ profiles and band sliders (`PlaybackSettings.vue`) |
| About        | App version, GitHub link, License, Open Data Folder button                 |

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
