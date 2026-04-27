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

## Entity Resolution

To avoid duplicates, entities are identified by **normalization key** (lowercased, Unicode-folded, trimmed). IDs are deterministic MD5-based UUIDs generated from the seed string.

**Artist deduplication:** `NormalizationKey(name)` → lookup existing artist → upsert if not found.

**Album deduplication:** `NormalizationKey(title + primaryArtist)` → lookup → upsert. Album artwork is set from the first track that provides it.

**Multi-artist splitting:** Raw artist strings like `"Artist A, Artist B feat. Artist C"` are split via `domain.SplitArtists()` into individual artist entities with positional ordering.

## Wails-Exposed Methods

```typescript
AddFolder(path: string): void
RemoveFolder(id: string): void          // optionally keeps tracks
GetFoldersToSync(): WatchedFolder[]
SyncAll(): void                          // re-scan all watched folders
ReindexAll(): void                       // rebuild Bleve index from DB
GetAlbumColors(id: string): ThemeColors
ToggleFavorite(trackID: string): boolean
UpdateTrackMetadata(trackID: string, update: MetadataUpdate): void
ShowInExplorer(trackID: string): void
// Read methods:
GetAllTracks(): TrackDTO[]
GetTracksPaginated(offset, limit): TrackDTO[]
GetTrackCount(): number
GetAllAlbums(): AlbumDTO[]
GetAlbumByID(id): AlbumDTO
GetAllArtists(): Artist[]
GetArtistByID(id): Artist
GetAlbumsByArtistID(artistID): AlbumDTO[]
GetAllGenres(): Genre[]
GetGenreByID(id): Genre
GetAllComposers(): Composer[]
GetComposerByID(id): Composer
GetTracksByComposerID(composerID): TrackDTO[]
GetFavoriteTracks(): TrackDTO[]
GetRecentlyPlayedTracks(limit): TrackDTO[]
GetMostListenedTracks(limit): TrackDTO[]
GetLeastListenedTracks(limit): TrackDTO[]
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
  → LibraryService.UpdateTrackMetadata(id, MetadataUpdate)
  → MetadataWriter.WriteMetadata(path, fields)   // writes tags to file
  → Re-import file: ImportFile(path)              // re-extracts and updates DB
  → EmitEvent("library:track-updated", updated)
```

## Play Count & Recently Played

`IncrementPlayCount(trackID)` is called by `PlayerService` on each track load. The `updated_at` timestamp on the track is used for recently-played ordering (`GetRecentlyPlayed` orders by `updated_at DESC`). `GetMostListened` orders by `play_count DESC`. `GetLeastListened` orders by `play_count ASC` (excluding zero plays).
