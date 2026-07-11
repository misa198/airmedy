# Equalizer

## Summary

10-band parametric equalizer with named profiles and built-in presets. EQ is applied at the audio adapter level across all platforms (SFBAudioEngine on macOS, miniaudio on Windows/Linux). Users can create, rename, delete, and switch profiles. Band gains are applied live. The global enabled state is persisted across app restarts via `AppSettings`.

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
    Name      string
    IsActive  bool
    IsDefault bool
    Bands     []EQBand  // always 10 bands
}
```

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

Seeded on first run via `SeedDefaults()`. Marked `is_default = 1` (cannot be deleted).

| Preset     | Description                      |
| ---------- | -------------------------------- |
| Flat       | All bands 0 dB                   |
| Rock       | Boosted lows and highs           |
| Pop        | Mid-forward, attenuated extremes |
| Jazz       | Warm mid-bass boost              |
| Classical  | Subtle, wide                     |
| Hip-Hop    | Heavy sub-bass, presence boost   |
| Electronic | Sub-bass + air boost             |

## EQController Interface (optional)

```go
type EQController interface {
    SetEQBand(index int, frequency, gain, bandwidth float64) error
    SetEQEnabled(enabled bool) error
}
```

Implemented by `player_darwin.go` (SFBAudioEngine) and `player_miniaudio.go` (miniaudio). The `EQService` checks if the audio adapter implements this interface before calling it.

**macOS note:** `SetEQEnabled` bypasses each of the 10 `AVAudioUnitEQFilterParameters`
bands individually, not the shared `AVAudioUnitEQ` unit's own `bypass` property.
The same persistent EQ node also carries [Volume Normalization](../normalization/README.md)'s
`globalGain`, and a unit-level bypass silences the *whole* Audio Unit — `globalGain`
included. Per-band bypass keeps EQ toggling and normalization gain independent.

## EQService Methods

```go
SeedDefaults(ctx) error                           // populate presets on first run
ApplyActiveProfile(ctx) error                     // apply current active profile to player (on startup)
GetActiveProfile(ctx) (*EQProfile, error)
GetAllProfiles(ctx) ([]*EQProfile, error)
ApplyProfile(ctx, id string) error                // set active + apply all bands to player
CreateProfile(ctx, name string) (*EQProfile, error)  // flat bands, not default
UpdateBand(ctx, profileID string, bandIndex int, gain float64) error  // live update
RenameProfile(ctx, id, name string) error
DeleteProfile(ctx, id string) error               // error if default profile
SetEnabled(ctx, enabled bool) error               // toggle EQ globally
```

## Wails-Exposed Methods

```typescript
GetAllProfiles(): EQProfile[]
GetActiveProfile(): EQProfile
CreateProfile(name: string): EQProfile
ApplyProfile(id: string): void
UpdateBand(profileID: string, bandIndex: number, gain: number): void
RenameProfile(id: string, name: string): void
DeleteProfile(id: string): void
SetEnabled(enabled: boolean): void
```

## Wails Events

- `"eq:active-profile-changed"` (data: `id` string): Emitted when a profile is applied/switched.
- `"eq:profiles-updated"` (data: `nil`): Emitted when a profile is created, renamed, or deleted.

## Database Tables

```sql
eq_profiles (id, name, is_active, is_default, created_at)
eq_bands    (profile_id FK, band_index, frequency, gain, bandwidth)
```

Profile has exactly one active profile at any time. `SetActive()` uses a transaction: clear all `is_active`, set the selected one.

## Frontend Component (`EQPanel.vue`)

Located in Settings → Equalizer tab.

- Uses global `app` store for EQ enabled state and profile management.
- Fetches all profiles on mount.
- Listens to Wails events `"eq:active-profile-changed"` and `"eq:profiles-updated"` to synchronize its local state if the active profile or the profile list is modified from another component (like the player quick settings).
- Renders 10 vertical sliders (one per band), range -12 to +12 dB.
- Moving a slider calls `UpdateBand()` immediately — live effect while playing.
- Profile dropdown switches active profile (`ApplyProfile()`).
- The player footer quick-settings menu also loads and applies profiles on demand;
  it marks the active profile and enables EQ when a profile is selected.
- Create / Rename / Delete profile buttons with confirmation dialogs.
- Global enable/disable toggle (persisted).
- Platform note: EQ interaction is live on all platforms.
