# Playlists

## Summary

User-created playlists with ordered tracks, optional custom artwork, and color extraction. Playlists are persisted in SQLite and indexed in Bleve for search.

## Files

| File                                           | Purpose                       |
| ---------------------------------------------- | ----------------------------- |
| `internal/app/playlist/playlist_service.go`    | Business logic                |
| `internal/app/playlist/smart_rules.go`         | Smart playlist field/operator/order-by allowlist + WHERE builder |
| `internal/app/playlist/m3u8.go`               | M3U8 parser (import/export)   |
| `internal/infra/sqlite/playlist_repository.go` | SQLite persistence            |
| `internal/infra/wails/playlist_service.go`     | Wails binding                 |
| `frontend/src/stores/playlists.ts`             | Frontend state                |
| `frontend/src/lib/smartPlaylistFields.ts`      | Frontend field allowlist, types, validation, `MAX_RULES` |
| `frontend/src/components/RuleBuilder.vue`      | Root of the rule editor (group list + limit/live-updating) |
| `frontend/src/components/RuleGroupBox.vue`     | One rule group (its own ALL/ANY + rule rows) |
| `frontend/src/components/SmartPlaylistDialog.vue` | Create/edit dialog (name, rules, limit, live updating) |
| `frontend/src/composables/usePlaylistContextMenu.ts` | Shared playlist context menu (Rename, Edit Rules, Delete, ...) |
| `frontend/src/views/PlaylistDetailView.vue`    | Playlist detail page          |

## Playlist Model

```go
type Playlist struct {
    ID          string
    Name        string
    Description string
    ArtworkKey  *string    // nullable — user-provided artwork cache key
    IsSmart     bool       // true for rule-based playlists (see Smart Playlists below)
    Rules       *string    // raw JSON SmartPlaylistConfig, nil for normal playlists
    CreatedAt   time.Time
    UpdatedAt   time.Time
}

type SmartRule struct {
    Field string
    Op    string
    Value any
}

// SmartRuleGroup is one node of the rule tree. Its own Rules and its nested
// Groups all combine with each other via Match ("all" = AND, "any" = OR),
// recursively — this is what lets a playlist nest multiple AND/OR blocks
// instead of one flat rule list. Rules/Groups have no `omitempty` — the
// frontend expects both keys present (even as `[]`) on every node so it
// never has to null-check before indexing into them.
type SmartRuleGroup struct {
    Match  string           `json:"match"` // "all" | "any"
    Rules  []SmartRule      `json:"rules"`
    Groups []SmartRuleGroup `json:"groups"`
}

// SmartPlaylistLimit optionally caps a smart playlist's match to Count
// tracks, ordered by By before truncating. Ignored (unlimited) when
// Enabled is false.
type SmartPlaylistLimit struct {
    Enabled bool
    Count   int
    By      string // "random" | "album" | "artist" | "genre" | "title" | "most_played"
}

// SmartPlaylistConfig is the full shape persisted in Playlist.Rules.
type SmartPlaylistConfig struct {
    Root  SmartRuleGroup
    Limit SmartPlaylistLimit
    // LiveUpdating: true (default) recomputes the match on every read.
    // false freezes membership at whatever it was last saved — see
    // "Track Limit & Live Updating" below.
    LiveUpdating bool
}
```

## PlaylistService Methods

```go
Create(ctx, name, description string) (*Playlist, error)
Update(ctx, id, name, description string) error
Delete(ctx, id string) error           // also removes from search index
GetAll(ctx) ([]*Playlist, error)
GetByID(ctx, id string) (*Playlist, error)
GetTracks(ctx, playlistID string) ([]*TrackDTO, error)
AddTrack(ctx, playlistID, trackID string) error  // Calculates LexoRank position
AddTracks(ctx, playlistID string, trackIDs []string) error // Batch add; single transaction, no position duplicates
RemoveTrack(ctx, playlistID, trackID string) error
MoveTrack(ctx, playlistID, trackID, prevTrackID, nextTrackID string) error // O(1) LexoRank update
SetArtwork(ctx, playlistID, artworkPath string) error  // copies file to artwork cache
RemoveArtwork(ctx, playlistID string) error
GetPlaylistColors(ctx, id string) (*ThemeColors, error)
ExportM3U8(ctx, playlistID, destPath string) error
CreateSmart(ctx, name, description string, config SmartPlaylistConfig) (*Playlist, error)
UpdateSmartRules(ctx, id string, config SmartPlaylistConfig) error
```

`GetTracks` branches on `playlist.IsSmart`: for a smart playlist it unmarshals
`Rules` into a `SmartPlaylistConfig` and delegates to `evaluateSmartTracks`
(see "Track Limit & Live Updating" below) instead of the `playlist_tracks`
join table. `AddTrack`, `AddTracks`, `RemoveTrack`, and `MoveTrack` reject
calls against a smart playlist (`guardNotSmart`) regardless of live-updating
state — its membership is always computed/materialized by the service, never
hand-edited.

## Smart Playlists

A smart playlist (`playlists.is_smart = 1`) has no rows in `playlist_tracks`
while live-updating (see below). Its membership is defined by a
`SmartPlaylistConfig` stored as JSON in `playlists.rules` and evaluated
against `tracks` at read time.

### Rule Schema

A `SmartRuleGroup` is a node with its own flat `rules` plus nested `groups`;
everything in a node (its rules AND its child groups) combines via that
node's `match` (`"all"` = AND, `"any"` = OR), recursively. This is what lets
the rule builder combine multiple AND/OR blocks instead of just one flat
list — e.g. "(genre is Rock AND year between 1990-1999) OR (genre is Jazz
AND bpm < 100)", capped to the 25 most-played matches:

```json
{
  "root": {
    "match": "any",
    "rules": [],
    "groups": [
      { "match": "all", "groups": [], "rules": [
        {"field": "genre", "op": "is", "value": "Rock"},
        {"field": "year", "op": "between", "value": [1990, 1999]}
      ]},
      { "match": "all", "groups": [], "rules": [
        {"field": "genre", "op": "is", "value": "Jazz"},
        {"field": "bpm", "op": "lt", "value": 100}
      ]}
    ]
  },
  "limit": { "enabled": true, "count": 25, "by": "most_played" },
  "live_updating": true
}
```

A playlist with just a flat rule list (no nesting) is simply a root group
with an empty `groups` array. The frontend rule builder only ever produces
this fixed two-level shape (root holds groups only, never its own rules;
groups hold rules only, never nested subgroups) even though the backend
schema — and `BuildWhereClause` — support arbitrary nesting depth;
`normalizeGroupForEditor`/`normalizeConfigForEditor` (`smartPlaylistFields.ts`)
flatten any stored shape (bare pre-groups rules, deeper nesting, or missing
`rules`/`groups` keys from an older build) into that two-level shape when
opening the edit dialog.

A rule tree is capped at `MAX_RULES = 16` total leaf rules, enforced both in
the frontend (disables "Add rule" past the cap, shows a live `x/16` counter)
and the backend (`marshalRules` in `playlist_service.go`, since the frontend
cap is UX only and not a security boundary). At least one rule is required —
`marshalRules` rejects an empty rule tree, and the frontend disables/guards
the Create button the same way. The frontend also validates each rule's
value shape client-side (`isRuleValid`/`isGroupValid`): non-empty strings,
finite numbers, and `lo <= hi` for `between` ranges — shown as an inline
error once the user has edited that rule, or on all invalid rules at once if
they try to submit with the dialog still invalid.

### Track Limit & Live Updating

`SmartPlaylistLimit` optionally caps the match to `Count` tracks, ordered by
`By` before truncating (`OrderBySQL` in `smart_rules.go` maps `By` to a safe
SQL `ORDER BY` fragment — `random` → `RANDOM()`, `album`/`artist`/`genre`/
`title` → the corresponding column, `most_played` → `play_count DESC` —
same allowlist-by-switch pattern as the field/operator table, never
interpolating `By` directly). Unset/disabled means unlimited.

`LiveUpdating` controls whether `evaluateSmartTracks` (`playlist_service.go`)
recomputes the rule tree on every read or serves a frozen snapshot:

```go
if !config.LiveUpdating {
    return s.repo.GetTracks(ctx, p.ID)   // frozen: ordinary playlist_tracks read
}
return s.matchSmartConfig(ctx, config)  // live: re-run BuildWhereClause + GetByRules
```

The snapshot is materialized by `applySmartConfig`, called from both
`CreateSmart` and `UpdateSmartRules` right after the rules are persisted: it
always clears any existing `playlist_tracks` rows for the playlist first,
then — only when `LiveUpdating` is false — runs the rule match once and
writes the resulting track IDs into `playlist_tracks` via `AddTracks`
(assigning LexoRank positions like a normal playlist). This mirrors iTunes'
"Live updating" smart-playlist checkbox: turning it off freezes membership
at whatever matched at that moment; the only way to refresh a frozen
playlist is to reopen the rule editor and save again (even with no changes).
Track mutation methods (`AddTrack`/`RemoveTrack`/`MoveTrack`) still reject a
smart playlist regardless of `LiveUpdating` — freezing is not the same as
becoming a hand-editable playlist.

Recomputation of a *live* smart playlist is otherwise purely pull-based —
there is no background watcher, timer, or push on library changes. It only
happens when the frontend calls `GetPlaylistTracks`: opening the playlist
detail page (and on `playlist:tracks-changed`/`playlist:rules-changed`
events), loading playlist artwork thumbnails in the grid view, and Play/
Shuffle/Play Next/Add to Queue/Add to Playlist from the context menu. The
big Play/Shuffle buttons on the detail page reuse whatever was already
loaded onto the page rather than re-fetching, so they do not force a
recompute.

### Field/Operator Allowlist (`internal/app/playlist/smart_rules.go`)

| Field         | Column/relation                    | Type    | Operators                            |
| ------------- | ----------------------------------- | ------- | ------------------------------------- |
| `genre`       | `track_genres` → `genres.name`      | string  | `is`, `is_not`, `contains`            |
| `artist`      | `track_artists` → `artists.name`    | string  | `is`, `is_not`, `contains`            |
| `year`        | `tracks.year`                       | number  | `gt`, `lt`, `gte`, `lte`, `between`   |
| `bpm`         | `tracks.bpm`                        | number  | `gt`, `lt`, `gte`, `lte`, `between`   |
| `play_count`  | `tracks.play_count`                 | number  | `gt`, `lt`, `gte`, `lte`              |
| `duration`    | `tracks.duration`                   | number  | `gt`, `lt`, `gte`, `lte`, `between`   |
| `bitrate`     | `tracks.bitrate`                    | number  | `gt`, `lt`, `gte`, `lte`, `between`   |
| `is_favorite` | `tracks.is_favorite`                | bool    | `is`                                   |
| `added_at`    | `tracks.created_at` (days-ago diff) | number  | `in_last_days`                        |

`BuildWhereClause(group SmartRuleGroup) (whereSQL string, args []any, err error)`
recursively translates a rule tree into a bound `WHERE` clause. Field and
operator names are looked up in this allowlist and never interpolated into
SQL directly — this is the sole gate against injection through a crafted
rule, since the resulting `whereSQL` is spliced into a raw query string in
`track_repository.go` (`GetByRules`). Both `CreateSmart` and
`UpdateSmartRules` call `BuildWhereClause` to validate before persisting, so
a malformed rule tree fails at write time rather than at every read.

Mood-based fields (`track_features.valence/energy/danceability/tempo`) are
intentionally not in the allowlist yet — that data is reserved-null pending
the audio analysis pipeline (see `catalog/audio_analysis` if present). The
frontend reserves a "Mood" tab in the rule builder dialog for a future
valence×energy quadrant picker; it is UI scaffolding only, not backed by rules
yet.

## Track Ordering (LexoRank)

Playlist track order is maintained using the LexoRank algorithm (via `github.com/misa198/lexorank-go`). This provides:

- **O(1) Reordering:** Moving a track only requires calculating a new rank between its new neighbors (`Between(prev, next)`), without updating other rows.
- **Automatic Rebalancing:** If a rank string's length exceeds 10 characters (indicating a deep sequence of insertions), the service triggers a synchronous rebalance of all tracks in that playlist to keep rank strings short.
- **Stable Sorting:** SQLite queries use `ORDER BY position, track_id` to ensure deterministic ordering even in the rare event of a rank collision.

## M3U8 Parser (`m3u8.go`)

`ParseM3U8(filePath string) (*M3U8File, error)` reads an extended M3U8 file and
returns the playlist name and a slice of `M3U8Entry` (Path, Title, Artist, Album,
Genre, Duration). Unknown directives are ignored. The file must begin with
`#EXTM3U`.

## LibraryService Methods (import-related)

```go
IsPathValid(ctx, path string) error
// Returns nil if path: exists on disk, has a supported extension, is under a watched folder.

EnsureTrack(ctx, path, fallbackTitle, fallbackArtist string) (*TrackDTO, error)
// Returns existing track from DB, or imports it first if missing.
// Applies fallback values only to empty tag fields of newly imported tracks.
```

## Wails-Exposed Methods

```typescript
GetAllPlaylists(): Playlist[]
GetPlaylistByID(id: string): Playlist
GetPlaylistTracks(playlistID: string): TrackDTO[]
GetPlaylistsForTrack(trackID: string): string[]   // returns playlist IDs
GetPlaylistColors(id: string): ThemeColors
CreatePlaylist(name: string, description: string): Playlist
UpdatePlaylist(id: string, name: string, description: string): void
DeletePlaylist(id: string): void
AddTrackToPlaylist(playlistID: string, trackID: string, senderID: string): void
AddTracksToPlaylist(playlistID: string, trackIDs: string[], senderID: string): void  // batch; single transaction
RemoveTrackFromPlaylist(playlistID: string, trackID: string, senderID: string): void
SelectAndSetPlaylistArtwork(id: string): string   // opens file picker, returns key
RemovePlaylistArtwork(id: string): void
MoveTrack(playlistID: string, trackID: string, prevTrackID: string, nextTrackID: string, senderID: string): void
ExportPlaylistToM3U8(playlistID: string): void    // opens save dialog, writes UTF-8 M3U8
SelectAndParseM3U8(): M3U8Preview | null          // opens file picker, returns parsed preview
ImportM3U8Playlist(filePath: string, name: string): M3U8ImportResult
CreateSmartPlaylist(name: string, description: string, config: SmartPlaylistConfig): Playlist
UpdateSmartPlaylistRules(id: string, config: SmartPlaylistConfig, senderID: string): void
```

### Event Echo Guarding (senderID)

To prevent race conditions where a frontend optimistic update is overwritten by a stale "tracks-changed" event from the backend, the service uses a `senderID` (correlation ID) pattern:

1. Frontend generates a `sessionId` on mount.
2. Frontend passes `sessionId` to `MoveTrack`, `AddTrackToPlaylist`, etc.
3. Backend includes this `senderID` in the `playlist:tracks-changed` event payload.
4. Frontend ignores any event where `payload.sender_id === localSessionId`.

### Return Types (import/export)

```typescript
interface M3U8Preview {
  file_path: string
  playlist_name: string
  entry_count: number
}

interface M3U8ImportResult {
  playlist_id: string
  imported_count: number
  skipped_count: number
}
```

## Database Schema

```sql
CREATE TABLE playlists (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    artwork_key TEXT,
    is_smart INTEGER NOT NULL DEFAULT 0,
    rules TEXT,  -- JSON SmartPlaylistConfig, NULL for normal playlists
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE playlist_tracks (
    playlist_id TEXT NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    track_id    TEXT NOT NULL REFERENCES tracks(id)    ON DELETE CASCADE,
    position    TEXT NOT NULL, -- LexoRank string
    PRIMARY KEY (playlist_id, track_id)
);
```

## Artwork

- User selects an image file via the OS file picker (`SelectAndSetPlaylistArtwork`).
- The image is saved to `ArtworkCache` (same disk cache as album artwork).
- The returned key is stored in `playlists.artwork_key`.
- Color extraction works identically to album artwork (`GetPlaylistColors` → palette extraction).
- Removing artwork sets `artwork_key = NULL` and deletes the cached file if no other entity references it.

## Search Indexing

On `Create` and `Update`, the playlist is indexed via:

```go
SearchService.IndexPlaylist(ctx, playlist)
```

Fields indexed: `name`, `description`.

On `Delete`, `SearchService.DeleteFromIndex(ctx, id)` removes the playlist.

## Event Emitted

| Event                     | When                                   |
| ------------------------- | -------------------------------------- |
| `playlist:tracks-changed` | Track added or removed from a playlist |
| `playlist:rules-changed`  | Smart playlist rules updated           |

## Frontend State (`stores/playlists.ts`)

```typescript
interface PlaylistsStore {
  playlists: Playlist[];
  loading: boolean;
  loadAll(): Promise<void>;
  create(name: string, description: string): Promise<void>;
  createSmart(name: string, description: string, config: SmartPlaylistConfig): Promise<Playlist | null>;
  updateSmartRules(id: string, config: SmartPlaylistConfig, senderID: string): Promise<void>;
  rename(id: string, name: string): Promise<void>;
  deletePlaylist(id: string): Promise<void>;
}
```

`frontend/src/lib/smartPlaylistFields.ts` mirrors the backend allowlist for
the rule builder UI: field id, value type (`string | number | boolean`), and
allowed operators per field (including `bitrate`, added alongside the
backend's). `RuleRow.vue` picks its value-editor by value type, not by field
id, so adding a field only means adding a registry entry — no new editor
component. It also holds `MAX_RULES`, `countRules`/`isRuleValid`/
`isGroupValid` (client-side validation), and `normalizeGroupForEditor`/
`normalizeConfigForEditor` (flattening described above).

The rule editor UI is a fixed two-level component pair, not a recursive
tree: `RuleBuilder.vue` is the root — it renders the flat list of
`SmartRuleGroup` boxes plus, only when there are 2+ groups, a top "Combine
groups" ALL/ANY selector between them, and an "Add group" button with a
live `x/16` rule counter. `RuleGroupBox.vue` is one group — its own ALL/ANY
selector, `RuleRow` list, "Add rule" button (disabled once the playlist-wide
`MAX_RULES` cap is hit), and a remove-group button. `SmartPlaylistDialog.vue`
(name/description, Filters/Mood tabs, the root `RuleBuilder`, and the Limit/
Live-updating checkboxes described above) sits on top. The Mood tab shows a
"enable library analysis" hint when analysis is off, otherwise a "coming
soon" placeholder — mood-derived fields are reserved-null pending the audio
analysis pipeline, not gated behind analysis being on for the rest of the
dialog (the "New Smart Playlist" entry point itself is always visible).

Field-level validation only shows once a rule has been edited (tracked by
object identity in a `WeakSet`, so it survives reordering) or once the user
has tried to submit an invalid form (`showAllErrors`, revealed by clicking
Create/Save rather than disabling the button outright, so the click always
gives feedback).

The playlist context menu (`usePlaylistContextMenu.ts`, shared by the
sidebar, the all-playlists grid, and the detail page) has a dedicated
"Edit Rules" entry (`onEditSmartRules`) shown only for smart playlists,
separate from the generic "Rename" entry — each call site keeps its own
`SmartPlaylistDialog` instance mounted unconditionally (mirroring the
existing rename-dialog pattern) rather than conditionally with `v-if`, since
mounting a dialog and setting `open` true in the same tick crashed Vue.

`loadAll()` is called on app startup and after any create/delete operation.

## Sidebar Navigation

Playlists appear in the sidebar below the main navigation items, ordered by creation date. A "Create Playlist" button opens a name input dialog. Clicking a playlist navigates to `/playlists/:id`.

## Track Context Menu Integration

The `Add to Playlist` context menu item fetches `GetPlaylistsForTrack(trackID)` to show a checkmark next to playlists that already contain the track. Clicking a playlist name calls `AddTracksToPlaylist` (batch, for multi-track selection) or `RemoveTrackFromPlaylist` depending on current membership.

`AddTracksToPlaylist` uses a single DB transaction to assign sequential LexoRank positions, preventing the race condition that occurred when multiple `AddTrackToPlaylist` calls fired concurrently and all read the same max position.
