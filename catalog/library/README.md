# Library Management

## Summary

The library feature manages the user's music collection: watching folders for changes, importing audio files, extracting metadata, persisting entities to SQLite, and keeping the search index in sync.

## Entry Points

| Layer               | File                                      |
| ------------------- | ----------------------------------------- |
| Wails binding       | `internal/infra/wails/library_service.go` |
| Application service | `internal/app/library/service.go`         |
| Domain interfaces   | `internal/domain/repositories.go`         |
| SQLite adapters     | `internal/infra/sqlite/*_repository.go`   |

## Watched Folders

Users register directories via `AddFolder(path)`. The path is stored in the `watched_folders` table and monitored by `fsnotify`.

```go
// WatchedFolder
type WatchedFolder struct {
    ID        string
    Path      string
    CreatedAt time.Time
}
```

File system events are debounced (500ms) before processing:

- `Create` / `Write` → import file
- `Remove` / `Rename` → delete track from DB and search index

## Import Pipeline

```
AddFolder(path)
  └─ SyncFolder(root)
       └─ Walk all files recursively
            └─ For each supported file: ImportFile(path)
                 ├─ MetadataExtractor.Extract() → TrackDTO
                 ├─ MetadataExtractor.ExtractArtwork() → []byte
                 ├─ ArtworkCache.Save() → artworkKey
                 ├─ Resolve entities (Album, Artists, Genres, Composers)
                 ├─ TrackRepository.Upsert()
                 ├─ Set M2M relationships (SetArtists, SetGenres, etc.)
                 └─ SearchService.IndexTrack()
```

**Supported formats:** `.mp3`, `.flac`, `.m4a`, `.wav`, `.ogg`, `.opus`, `.aiff`, `.aif`, `.ape`, `.wv`, `.dsf`, `.dff`

> ALAC (Apple Lossless) uses the `.m4a` container — it is covered by `.m4a`, not a separate extension.

## Entity Resolution

To avoid duplicates, entities are identified by **normalization key** (lowercased, Unicode-folded, trimmed). IDs are deterministic MD5-based UUIDs generated from the seed string.

**Artist deduplication:** `NormalizationKey(name)` → lookup existing artist → upsert if not found.

**Album deduplication:** `NormalizationKey(title + primaryArtist)` → lookup → upsert. Album artwork is set from the first track that provides it.

**Multi-artist splitting:** Raw artist strings like `"Artist A, Artist B feat. Artist C"` are split via `domain.SplitArtists()` into individual artist entities with positional ordering.

## Wails-Exposed Methods

```typescript
// Folder management
SelectFolder(): string                   // opens OS folder picker dialog
AddFolder(path: string): void
RemoveFolder(id: string): void           // optionally keeps tracks
GetWatchedFolders(): WatchedFolder[]
SyncAll(): void                          // re-scan all watched folders
ImportAll(): void                        // alias for SyncAll
ReindexAll(): void                       // rebuild Bleve index from DB
GetSyncStatus(): SyncProgress | null     // stub; used for frontend type generation
// Metadata & artwork
GetAlbumColors(id: string): ThemeColors
GetArtistArtwork(artistID: string, eventID: string): string | null
ToggleFavorite(trackID: string): boolean
UpdateTrackMetadata(trackID: string, update: MetadataUpdate): void
ShowInExplorer(trackID: string): void
// Track queries
GetAllTracks(): TrackDTO[]
GetTracksPaginated(offset, limit): TrackDTO[]
GetTrackCount(): number
GetTracksByAlbumID(albumID: string): TrackDTO[]
GetTracksByArtistID(artistID: string): TrackDTO[]
GetTracksByGenreID(genreID: string): TrackDTO[]
GetTracksByComposerID(composerID: string): TrackDTO[]
GetFavoriteTracks(): TrackDTO[]
GetRecentlyPlayedTracks(limit: number): TrackDTO[]
GetMostListenedTracks(limit: number): TrackDTO[]
GetLeastListenedTracks(limit: number): TrackDTO[]
// Album queries
GetAllAlbums(): AlbumDTO[]
GetAlbumByID(id: string): AlbumDTO
GetAlbumsByArtistID(artistID: string): AlbumDTO[]
GetRecentlyAddedAlbums(limit: number): AlbumDTO[]
// Artist / genre / composer queries
GetAllArtists(): Artist[]
GetArtistByID(id: string): Artist
GetAllGenres(): Genre[]
GetGenreByID(id: string): Genre
GetAllComposers(): Composer[]
GetComposerByID(id: string): Composer
```

## Events Emitted

| Event                   | Payload                    | When                     |
| ----------------------- | -------------------------- | ------------------------ |
| `library:sync-started`  | `{ total: number }`        | Before scan begins       |
| `library:sync-progress` | `{ current, total, path }` | Per file imported        |
| `library:sync-finished` | `{}`                       | Scan complete            |
| `library:track-updated` | `TrackDTO`                 | Metadata written to file |
| `library:updated`       | `{}`                       | General library change   |

## Frontend Integration

**`useLibraryUpdates(tracks)` composable** listens for `library:track-updated` and `library:track-deleted` events and mutates the provided reactive array in-place, so all views stay current without re-fetching.

**Home view** fetches `GetRecentlyPlayedTracks`, `GetMostListenedTracks`, `GetLeastListenedTracks` for carousel sections.

**Settings → Library tab** renders watched folders list, Add/Remove folder buttons, Sync All button.

## Orphan Cleanup

After syncing, `AlbumRepository.DeleteOrphaned()`, `ArtistRepository.DeleteOrphaned()`, `GenreRepository.DeleteOrphaned()`, `ComposerRepository.DeleteOrphaned()` remove entities no longer referenced by any track. Artwork cleanup is handled by `ArtworkCache.CleanupOrphaned()` using the set of active artwork keys from `TrackRepository.GetAllArtworkKeys()`.

## Metadata Update Flow

```
User edits metadata in MetadataEditDialog
  → Select new cover image (optional, auto-converted to JPEG)
  → LibraryService.UpdateTrackMetadata(id, MetadataUpdate)
  → MetadataWriter.WriteMetadata(path, fields)   // writes tags and artwork to file
  → Re-import file: ImportFile(path)              // re-extracts and updates DB
  → EmitEvent("library:track-updated", updated)
```

## Play Count & Recently Played

`IncrementPlayCount(trackID)` is called by `PlayerService` on each track load. The `updated_at` timestamp on the track is used for recently-played ordering (`GetRecentlyPlayed` orders by `updated_at DESC`). `GetMostListened` orders by `play_count DESC`. `GetLeastListened` orders by `play_count ASC` (excluding zero plays).
