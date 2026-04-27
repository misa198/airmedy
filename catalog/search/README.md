# Search

## Summary

Full-text search across tracks, albums, artists, playlists, and composers using **Bleve v2**. A single global index stores all entity types. Queries use prefix + exact matching with AND semantics across terms.

## Files

| File                                     | Purpose                         |
| ---------------------------------------- | ------------------------------- |
| `internal/infra/bleve/bleve.go`          | Index creation, indexing, query |
| `internal/domain/search.go`              | SearchService interface         |
| `internal/infra/wails/search_service.go` | Wails binding                   |

## SearchService Interface

```go
type SearchService interface {
    IndexTrack(ctx context.Context, track *TrackDTO) error
    IndexAlbum(ctx context.Context, album *AlbumDTO) error
    IndexArtist(ctx context.Context, artist *Artist) error
    IndexPlaylist(ctx context.Context, playlist *Playlist) error
    IndexComposer(ctx context.Context, composer *Composer) error
    Search(ctx context.Context, query string) ([]SearchResult, error)
    DeleteFromIndex(ctx context.Context, id string) error
    Close() error
}
```

## Index Structure

**Storage:** Bleve v2 on-disk index at `$XDG_DATA_HOME/airmedy/airmedy.bleve`.

**Document ID convention:** `{type}:{entity_id}` — e.g., `track:abc123`, `album:def456`.

**Field mappings per document type:**

| Type     | Fields indexed                                                                                 |
| -------- | ---------------------------------------------------------------------------------------------- |
| Track    | `id`, `type`, `title`, `artist_name` (primary), `artist_names` (array), `album_name`, `genres` |
| Album    | `id`, `type`, `title`, `artist_name`, `artist_names`                                           |
| Artist   | `id`, `type`, `title` (= name), `name`                                                         |
| Playlist | `id`, `type`, `title` (= name), `description`                                                  |
| Composer | `id`, `type`, `title` (= name)                                                                 |

**Field types:**

- `id`, `type` — keyword (exact match, stored, not analyzed)
- All other fields — text (analyzed with standard tokenizer, not stored)

All text values are normalized via `domain.FoldUnicode()` before indexing.

## Query Algorithm

```go
func Search(ctx, query string) ([]SearchResult, error)
```

1. **Normalize:** `domain.FoldUnicode(query)` — removes diacritics for accent-insensitive matching.
2. **Tokenize:** Split into terms, strip `*` wildcard characters.
3. **Per-term query:** For each term, build a disjunction (OR) of:
   - Exact term match (MatchQuery)
   - Prefix match (PrefixQuery)
4. **AND all terms:** All per-term disjunctions are combined with a ConjunctionQuery.
5. **Phrase boost:** If query has multiple terms, add an exact phrase MatchPhraseQuery with boost `2.0` — exact phrase matches rank higher.

**Result:** Returns `[]SearchResult{ID, Type, Score}` sorted by score descending. The Wails binding resolves full entity objects from the DB for each result ID.

## SearchResult

```go
type SearchResult struct {
    ID    string   // entity ID (without type prefix)
    Type  string   // "track", "album", "artist", "playlist", "composer"
    Score float64
}
```

## SearchResultSet (Wails binding output)

```typescript
interface SearchResultSet {
  tracks: TrackDTO[];
  albums: AlbumDTO[];
  artists: Artist[];
  playlists: Playlist[];
  playlist_tracks: Record<string, TrackDTO[]>; // first 4 tracks per playlist
  composers: Composer[];
}
```

## Index Lifecycle

| Operation           | When                                        |
| ------------------- | ------------------------------------------- |
| `IndexTrack()`      | After every file import                     |
| `IndexAlbum()`      | After album upsert                          |
| `IndexPlaylist()`   | On create/update                            |
| `DeleteFromIndex()` | On track/playlist delete                    |
| `ReindexAll()`      | Full rebuild (user-triggered from Settings) |

## Frontend Integration

**`stores/search.ts`:** Holds `query`, `results`, `loading`. `search(q)` is debounced 300ms.

**`SearchView`** renders `SearchResultSet` in sections: tracks (table), albums (grid), artists (grid), playlists (grid), composers (list).

**`ViewHeader`** in list views contains a search input that routes to `/search` with the query.
