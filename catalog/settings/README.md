# Settings

## Summary

Application-level settings: UI theme, display language, launch-at-login. Settings are persisted in SQLite and loaded at startup. A separate Settings view provides UI for all configuration including Library management and EQ (covered in their own catalog entries).

## Files

| File                                           | Purpose                     |
| ---------------------------------------------- | --------------------------- |
| `internal/app/config/config.go`                | Paths and AppSettings model |
| `internal/infra/sqlite/settings_repository.go` | SQLite persistence          |
| `internal/infra/wails/settings_service.go`     | Wails binding               |
| `frontend/src/stores/app.ts`                   | Frontend settings state     |
| `frontend/src/views/SettingsView.vue`          | Settings UI                 |

## AppSettings Model

```go
type AppSettings struct {
    Language     string  // BCP 47 language tag, e.g., "en", "zh", "ja"
    Theme        string  // "system", "light", "dark"
    StartAtLogin bool
}
```

Stored in the `app_settings` table (single-row, id always = 1).

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
  theme: "system" | "light" | "dark";
  language: string;
  startAtLogin: boolean;
  loadSettings(): Promise<void>;
  updateTheme(theme: string): Promise<void>;
  updateLanguage(lang: string): Promise<void>;
  updateStartAtLogin(enabled: boolean): Promise<void>;
  applyTheme(theme: string): void;
}
```

`applyTheme()` adds/removes the `dark` CSS class on `document.documentElement`. When theme is `system`, it respects `prefers-color-scheme` media query.

`updateLanguage()` sets `i18n.locale.value` immediately for instant locale switch without reload.

## Settings View Structure

`SettingsView.vue` uses a tab layout:

| Tab       | Content                                                    |
| --------- | ---------------------------------------------------------- |
| General   | Theme selector, Language picker, Start at Login toggle     |
| Library   | Watched folders list, Add/Remove folder, Sync All, Reindex |
| Equalizer | EQ profiles and band sliders (see equalizer catalog entry) |
| About     | App version, GitHub link, License, Open Data Folder button |

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
