# Equalizer

## Summary

10-band parametric equalizer with named profiles and built-in presets. EQ is applied at the audio adapter level across all platforms (SFBAudioEngine on macOS, miniaudio on Windows/Linux). Users can create, rename, delete, and switch profiles. Band gains are applied live. The global enabled state is persisted across app restarts via `AppSettings`. Presets contain only their ten band gains; **Preamp** (dB) and **Stereo Widener** (mid/side width) are global settings.

## Files

| File                                     | Purpose                               |
| ---------------------------------------- | ------------------------------------- |
| `internal/app/eq/eq_service.go`          | EQ business logic, profile management |
| `internal/infra/sqlite/eq_repository.go` | Profile + band persistence            |
| `internal/infra/wails/eq_service.go`     | Wails binding                         |
| `internal/domain/audio.go`               | EQController interface                |

## Data Structures

```go
type EQBand struct {
    Index     int
    Frequency float64  // Hz
    Gain      float64  // dB, range -12 to +12
    Bandwidth float64  // Q factor, default 1.0
}

type EQProfile struct {
    ID        string
    Key       string   // stable built-in preset identifier, empty for user profiles
    Name      string
    IsActive  bool
    IsDefault bool
    Bands     []EQBand  // always 10 bands
}
```

`AppSettings.EQPreamp` (float64, `json:"eq_preamp"`) holds global EQ gain in dB (`-12..12`).
`AppSettings.StereoWidth` (float64, `json:"stereo_width"`) holds the global stereo-width
percentage: `0` = mono, `100` = neutral/identity (default), up to `200` = wider. Global rather
than per-profile — same value regardless of which EQ profile is active.

## Frequency Bands

Standard ISO 10-band frequencies:

| Index | Frequency |
| ----- | --------- |
| 0     | 32 Hz     |
| 1     | 64 Hz     |
| 2     | 125 Hz    |
| 3     | 250 Hz    |
| 4     | 500 Hz    |
| 5     | 1 kHz     |
| 6     | 2 kHz     |
| 7     | 4 kHz     |
| 8     | 8 kHz     |
| 9     | 16 kHz    |

## Built-in Presets

Seeded on first run via `SeedDefaults()`. Every built-in preset has a stable lowercase
`Key` (for example `rock` or `full_bass`); `Name` is display-only. Marked `is_default = 1`
(cannot be deleted).
Every built-in gain is constrained to `-12..12 dB` and uses 0.5 dB increments, matching the UI.
`EQService.GetAllProfiles()` returns built-ins in the canonical backend order, followed by user
profiles alphabetically, so every UI presents the same list. Retired default presets are removed
during seeding; `Electronic / Dance` is the only Electronic preset.

| Preset                | Description                                         |
| --------------------- | --------------------------------------------------- |
| Flat                  | All bands 0 dB (Neutral reference)                  |
| Classical             | Classical music preset (VLC style)                  |
| Club                  | Club music preset (VLC style)                       |
| Dance                 | Dance music preset (VLC style)                      |
| Full Bass             | Deep bass boost preset (VLC style)                  |
| Full Bass & Treble    | Classical smile curve preset (VLC style)            |
| Full Treble           | Extreme treble boost preset (VLC style)             |
| Headphones            | Headphones optimization preset (VLC style)          |
| Large Hall            | Large space acoustics simulation (VLC style)        |
| Live                  | Live soundstage simulation preset (VLC style)        |
| Party                 | High dynamic party sound preset (VLC style)         |
| Pop                   | Pop music preset (VLC style)                        |
| Reggae                | Reggae genre preset (VLC style)                     |
| Rock                  | Rock genre preset (VLC style)                       |
| Ska                   | Ska genre preset (VLC style)                        |
| Soft                  | Gentle sound signature preset (VLC style)           |
| Soft Rock             | Soft rock genre preset (VLC style)                  |
| Techno                | Techno genre preset (VLC style)                     |
| Harman Target         | Industry standard headphone/speaker target tuning   |
| Bass Booster          | Sub-bass and mid-bass boost (Spotify style)         |
| Treble Booster        | High-frequency detail boost (Spotify style)         |
| Acoustic / Vocal      | Vocal and string clarity boost (Apple Music style)   |
| Sony Excited          | Energetic V-shape tuning (Sony Headphones Connect)   |
| Sony Mellow           | Smooth, non-fatiguing tuning (Sony Headphones Connect) |
| Electronic / Dance    | V-shaped club/EDM beats boost (Pioneer DJ style)    |
| R&B / Soul            | Modern groovy bass and vocal presence               |
| Vocal Booster         | Dialogues and midrange presence enhancement         |
| Loudness              | Equal loudness contour low-volume listening boost   |
| Spoken Word / Podcast | Voice-centric dialogue focus                        |
| Jazz                  | Balanced mids with gentle treble lift                |
| Hip-Hop               | Bass-forward curve with restrained highs             |

## EQController Interface (optional)

```go
type EQController interface {
    SetEQBand(index int, frequency, gain, bandwidth float64) error
    SetEQEnabled(enabled bool) error
}

// Global user preamp — independent from and composes with Volume
// Normalization's automatic gain (see catalog/normalization/README.md).
type EQPreampController interface {
    SetEQPreamp(db float64) error
}

// Global stereo width (mid/side). 100 = neutral/identity.
type StereoWidthController interface {
    SetStereoWidth(widthPercent float64) error
}
```

Implemented by `player_darwin.go` (SFBAudioEngine) and `player_miniaudio.go` (miniaudio). The `EQService` checks if the audio adapter implements each interface before calling it (same optional-interface pattern for all three).

**macOS note:** `SetEQEnabled` bypasses each of the 10 `AVAudioUnitEQFilterParameters`
bands individually, not the shared `AVAudioUnitEQ` unit's own `bypass` property.
The same persistent EQ node also carries [Volume Normalization](../normalization/README.md)'s
`globalGain`, and a unit-level bypass silences the *whole* Audio Unit — `globalGain`
included. Per-band bypass keeps EQ toggling and normalization gain independent.

## Preamp (global)

A user-adjustable gain (dB, -12..12) stored in `AppSettings.EQPreamp`, applied at startup and
live via `EQService.SetPreamp`. It is never changed by loading, creating, or editing a preset;
presets always contain exactly the ten bands. This is a **different value** from Volume
Normalization's automatic loudness-matching gain — the two compose rather than share storage:

- **macOS** (`native_player_darwin.m`): `AirmedyDeck.normPreampDB` (normalization, per-deck/source)
  and `AirmedyPlayer.eqPreampDB` (user preamp, global) are summed into
  `AVAudioUnitEQ.globalGain` via `applyGlobalGainForDeck:` whenever either changes.
- **Windows/Linux** (`miniaudio_wrapper_{linux,windows}.c`): `preamp_cur_linear`/`preamp_nxt_linear`
  (normalization) and `eq_preamp_linear` (user preamp) are both multiplied into every
  `ma_sound_set_volume` call site.

## Stereo Widener (global)

A mid/side width adjustment applied independently of the active EQ profile, stored in
`AppSettings.StereoWidth` (0=mono, 100=neutral/identity, up to 200=wider):

```
M = (L + R) / 2
S = (L - R) / 2 * width
L' = M + S
R' = M - S
```

At `width = 1.0` (100%) this is mathematically the identity transform (`L'=L, R'=R`), so the
node stays permanently in the audio graph with no special-casing for the default.

- **macOS**: a custom in-process `AUAudioUnit` subclass (`AirmedyStereoWidener`, registered once
  via `registerSubclass:asComponentDescription:` and instantiated synchronously per deck) whose
  `internalRenderBlock` performs the M/S math in place on the pulled output buffer. Inserted
  `equalizer → widener → mainMixerNode` in both `AirmedyDeck initWithDelegate:` (initial graph
  construction) and `reconfigureProcessingGraph:withFormat:` (format-change reconnect) — the
  `eq` node remains the "head" node SFBAudioEngine connects the source into; only the tail-side
  wiring changes to route through the widener.
- **Windows/Linux**: a custom `ma_node` (`ma_widener_node`, one input/output bus, vtable-based
  process callback) always attached as the tail of the chain, before the engine endpoint — so it
  applies regardless of whether the EQ is enabled. The 3 sites that route a sound to either
  `eq_bands[0]` or a fallback (`load_into_slot`, `ma_player_set_eq_enabled`,
  `ma_player_begin_crossfade`) target the widener instead of the raw engine endpoint when EQ is
  disabled.

## EQService Methods

```go
SeedDefaults(ctx) error                           // populate presets on first run
ApplyActiveProfile(ctx) error                     // apply current active profile to player (on startup)
GetActiveProfile(ctx) (*EQProfile, error)
GetAllProfiles(ctx) ([]*EQProfile, error)
ApplyProfile(ctx, id string) error                // set active + apply all bands to player
CreateProfile(ctx, name string) (*EQProfile, error)  // flat bands, not default
UpdateBand(ctx, profileID string, bandIndex int, gain float64) error  // live update; normalized to [-12, 12] in 0.5 dB steps
SetPreamp(ctx, gainDB float64) error                              // global live update, clamped [-12, 12]
RenameProfile(ctx, id, name string) error
DeleteProfile(ctx, id string) error               // error if default profile
SetEnabled(ctx, enabled bool) error               // toggle EQ globally
GetStereoWidth(ctx) (float64, error)              // global, 100 = neutral
SetStereoWidth(ctx, widthPercent float64) error   // clamped [0, 200]
```

## Wails-Exposed Methods

```typescript
GetAllProfiles(): EQProfile[]
GetActiveProfile(): EQProfile
CreateProfile(name: string): EQProfile
ApplyProfile(id: string): void
UpdateBand(profileID: string, bandIndex: number, gain: number): void
SetPreamp(gainDB: number): void
RenameProfile(id: string, name: string): void
DeleteProfile(id: string): void
SetEnabled(enabled: boolean): void
GetStereoWidth(): number
SetStereoWidth(width: number): void
```

## Wails Events

- `"eq:active-profile-changed"` (data: `id` string): Emitted when a profile is applied/switched.
- `"eq:profiles-updated"` (data: `nil`): Emitted when a profile is created, renamed, or deleted.

## Database Tables

```sql
eq_profiles (id, name, is_active, is_default, created_at)
eq_bands    (profile_id FK, band_index, frequency, gain, bandwidth)
app_settings (..., eq_preamp, stereo_width, ...)
```

Profile has exactly one active profile at any time. `SetActive()` uses a transaction: clear all `is_active`, set the selected one. Built-in profiles use unique non-empty `preset_key` values; user profiles retain an empty key.

Migration `000058` moves the active profile's legacy `preamp_gain` to `app_settings.eq_preamp`,
then removes `preamp_gain` from presets. `eq_preamp` defaults to `0`, so fresh installs start at
neutral gain.

## Frontend Component (`EQPanel.vue`)

Located in Settings → Equalizer tab.

- Uses global `app` store for EQ enabled state and profile management.
- Fetches all profiles on mount.
- Listens to Wails events `"eq:active-profile-changed"` and `"eq:profiles-updated"` to synchronize its local state if the active profile or the profile list is modified from another component (like the player quick settings).
- Renders the global Preamp vertical slider beside the 10 vertical preset-band sliders, range -12 to +12 dB.
- Moving a slider calls `UpdateBand()` immediately — live effect while playing.
- Profile dropdown switches active profile (`ApplyProfile()`).
- The player footer quick-settings menu also loads and applies profiles on demand;
  it marks the active profile and enables EQ when a profile is selected.
- Create / Rename / Delete profile buttons with confirmation dialogs.
- Global enable/disable toggle (persisted).
- Preamp slider (global, -12..12 dB) directly in `EQPanel.vue`, backed by
  `appStore.eqPreamp` / `updateEQPreamp`; switching presets leaves it unchanged.
- Stereo Width slider (global, 0..200%) in `PlaybackSettings.vue` below `EQPanel`, same
  live-drag + commit-on-release pattern as the Normalization Target LUFS slider. State lives in
  `frontend/src/stores/app.ts` (`stereoWidth`, `updateStereoWidth`); loaded with `?? 100`
  (nullish, not `||`) so a stored `0` (mono) is preserved rather than coerced back to neutral.
- Platform note: EQ interaction is live on all platforms.
