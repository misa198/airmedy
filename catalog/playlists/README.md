# Playlists

## Summary

User-created playlists with ordered tracks, optional custom artwork, and color extraction. Playlists are persisted in SQLite and indexed in Bleve for search.

## Files

| File                                           | Purpose                       |
| ---------------------------------------------- | ----------------------------- |
| `internal/app/playlist/playlist_service.go`    | Business logic                |
| `internal/app/playlist/m3u8.go`               | M3U8 parser (import/export)   |
| `internal/infra/sqlite/playlist_repository.go` | SQLite persistence            |
| `internal/infra/wails/playlist_service.go`     | Wails binding                 |
| `frontend/src/stores/playlists.ts`             | Frontend state                |
| `frontend/src/views/PlaylistDetailView.vue`    | Playlist detail page          |

## Playlist Model

```go
type Playlist struct {
    ID          string
    Name        string
    Description string
    ArtworkKey  *string    // nullable — user-provided artwork cache key
    CreatedAt   time.Time
    UpdatedAt   time.Time
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
AddTrack(ctx, playlistID, trackID string) error
RemoveTrack(ctx, playlistID, trackID string) error
SetArtwork(ctx, playlistID, artworkPath string) error  // copies file to artwork cache
RemoveArtwork(ctx, playlistID string) error
GetPlaylistColors(ctx, id string) (*ThemeColors, error)
ExportM3U8(ctx, playlistID, destPath string) error
```

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
AddTrackToPlaylist(playlistID: string, trackID: string): void
RemoveTrackFromPlaylist(playlistID: string, trackID: string): void
SelectAndSetPlaylistArtwork(id: string): string   // opens file picker, returns key
RemovePlaylistArtwork(id: string): void
MovePlaylistTrack(playlistID: string, trackID: string, position: number): void
ExportPlaylistToM3U8(playlistID: string): void    // opens save dialog, writes UTF-8 M3U8
SelectAndParseM3U8(): M3U8Preview | null          // opens file picker, returns parsed preview
ImportM3U8Playlist(filePath: string, name: string): M3U8ImportResult
```

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
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE playlist_tracks (
    playlist_id TEXT NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    track_id    TEXT NOT NULL REFERENCES tracks(id)    ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    PRIMARY KEY (playlist_id, track_id)
);
```

Track order is maintained by the `position` column. `RemoveTrack` uses a transaction that deletes the row and shifts positions of subsequent tracks. `MovePlaylistTrack` updates positions in a transaction.

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

## Frontend State (`stores/playlists.ts`)

```typescript
interface PlaylistsStore {
  playlists: Playlist[];
  loading: boolean;
  loadAll(): Promise<void>;
  create(name: string, description: string): Promise<void>;
  rename(id: string, name: string): Promise<void>;
  deletePlaylist(id: string): Promise<void>;
}
```

`loadAll()` is called on app startup and after any create/delete operation.

## Sidebar Navigation

Playlists appear in the sidebar below the main navigation items, ordered by creation date. A "Create Playlist" button opens a name input dialog. Clicking a playlist navigates to `/playlists/:id`.

## Track Context Menu Integration

The `Add to Playlist` context menu item fetches `GetPlaylistsForTrack(trackID)` to show a checkmark next to playlists that already contain the track. Clicking a playlist name calls `AddTrackToPlaylist` or `RemoveTrackFromPlaylist` depending on current membership.
