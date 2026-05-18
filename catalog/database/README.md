# Database

## Summary

SQLite database managed via `golang-migrate` for schema versioning and `sqlx` for query execution. The schema uses a normalized relational model with many-to-many junction tables for artist/genre/composer relationships.

## Files

| File                                    | Purpose                      |
| --------------------------------------- | ---------------------------- |
| `internal/infra/sqlite/sqlite.go`       | Connection, migration runner |
| `internal/infra/sqlite/migrations/`     | SQL up/down migration files  |
| `internal/infra/sqlite/columns.go`      | Shared SQL column selections |
| `internal/infra/sqlite/*_repository.go` | Repository implementations   |

## Connection Setup

- Driver: `github.com/mattn/go-sqlite3` (cgo)
- Query builder: `github.com/jmoiron/sqlx`
- Migrations: `github.com/golang-migrate/migrate/v4`
- Write serialization: single write connection with WAL mode enabled.
- Migrations run automatically on startup before any service initializes.

## Migration History

| #      | File                           | Change                                                                                                                             |
| ------ | ------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| 000001 | `init_schema.up.sql`           | Full initial schema (tracks, albums, artists, genres, composers, playlists, lyrics, watched_folders, all junction tables, indexes) |
| 000002 | `eq_profiles.up.sql`           | Add `eq_profiles` and `eq_bands` tables                                                                                            |
| 000003 | `favorites.up.sql`             | `ALTER TABLE tracks ADD COLUMN is_favorite INTEGER DEFAULT 0`, add index                                                           |
| 000004 | `player_state.up.sql`          | Add `player_state` table (single-row, CHECK id = 1)                                                                                |
| 000005 | `app_settings.up.sql`          | Add `app_settings` table (single-row)                                                                                              |
| 000006 | `app_settings_theme.up.sql`    | `ALTER TABLE app_settings ADD COLUMN theme TEXT DEFAULT 'system'`                                                                  |
| 000007 | `playlist_artwork.up.sql`      | `ALTER TABLE playlists ADD COLUMN artwork_key TEXT`                                                                                |
| 000008 | `extra_track_metadata.up.sql`  | Add `bpm`, `label`, `isrc`, `play_count` to `tracks`                                                                               |
| 000009 | `eq_profile_is_default.up.sql` | Add `is_default` to `eq_profiles`, set all existing = 1                                                                            |
| 000010 | `app_settings_lastfm.up.sql`   | `ALTER TABLE app_settings ADD COLUMN lastfm_username TEXT`                                                                         |
| 000011 | `app_settings_updates.up.sql`  | Add `auto_check_update`, `start_at_login` to `app_settings`                                                                       |
| 000012 | `playlist_lexorank.up.sql`     | Convert `playlist_tracks.position` from INTEGER to TEXT (LexoRank string), migrate existing data with computed ranks              |
| 000013 | `app_settings_eq.up.sql`       | `ALTER TABLE app_settings ADD COLUMN eq_enabled BOOLEAN DEFAULT 0`                                                                |
| 000014 | `meta_lyrics.up.sql`                 | Add `meta_content` and `meta_source` to `lyrics` table; add `lrclib_mode` to `app_settings`; backfill lyrics from `other_metadata` |
| 000015 | `artist_artwork.up.sql`              | `ALTER TABLE artists ADD COLUMN artwork_key TEXT`                                                                                    |
| 000016 | `app_settings_artist_artwork.up.sql` | `ALTER TABLE app_settings ADD COLUMN use_online_artist_artwork BOOLEAN NOT NULL DEFAULT 1`                                          |
| 000017 | `lyrics_provider_settings.up.sql`    | Add `enable_lrclib`, `enable_kugou`, `prefer_metadata_lyrics` (all `BOOLEAN NOT NULL DEFAULT 1`) to `app_settings`                 |

## Full Schema

### Core Entity Tables

```sql
artists (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    sort_name TEXT NOT NULL,
    normalization_key TEXT,
    artwork_key TEXT,
    created_at DATETIME,
    updated_at DATETIME
)

albums (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    sort_title TEXT NOT NULL,
    normalization_key TEXT,
    year INTEGER,
    copyright TEXT,
    artwork_key TEXT,
    created_at DATETIME,
    updated_at DATETIME
)

genres (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    normalization_key TEXT
)

composers (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    normalization_key TEXT
)

tracks (
    id TEXT PRIMARY KEY,
    path TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    sort_title TEXT NOT NULL,
    album_id TEXT REFERENCES albums(id) ON DELETE SET NULL,
    year INTEGER,
    track_number INTEGER,
    total_tracks INTEGER,
    disc_number INTEGER,
    total_discs INTEGER,
    duration INTEGER,           -- seconds
    bitrate INTEGER,
    sample_rate INTEGER,
    format TEXT,
    artwork_key TEXT,
    raw_artist_names TEXT,
    raw_album_artist_names TEXT,
    raw_genre_names TEXT,
    raw_composer_names TEXT,
    copyright TEXT,
    other_metadata TEXT,        -- JSON blob of all raw tags
    file_size INTEGER DEFAULT 0,
    bpm INTEGER,
    label TEXT,
    isrc TEXT,
    play_count INTEGER DEFAULT 0,
    is_favorite INTEGER DEFAULT 0,
    mtime DATETIME,
    created_at DATETIME,
    updated_at DATETIME
)

playlists (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    artwork_key TEXT,
    created_at DATETIME,
    updated_at DATETIME
)

lyrics (
    track_id TEXT PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    source TEXT,
    meta_content TEXT NOT NULL DEFAULT '',
    meta_source TEXT NOT NULL DEFAULT '',
    created_at DATETIME,
    updated_at DATETIME
)

watched_folders (
    id TEXT PRIMARY KEY,
    path TEXT NOT NULL UNIQUE,
    created_at DATETIME
)
```

### Junction Tables (Many-to-Many)

```sql
track_artists        (track_id, artist_id, position) PK(track_id, artist_id)
track_album_artists  (track_id, artist_id, position) PK(track_id, artist_id)
track_genres         (track_id, genre_id,  position) PK(track_id, genre_id)
track_composers      (track_id, composer_id, position) PK(track_id, composer_id)
album_artists        (album_id, artist_id, position) PK(album_id, artist_id)
playlist_tracks      (playlist_id, track_id, position TEXT) PK(playlist_id, track_id)
```

All junction tables cascade delete when parent entity is deleted.

### State Tables (Single-Row)

```sql
player_state (
    id INTEGER PRIMARY KEY CHECK(id = 1),
    queue_track_ids TEXT DEFAULT '[]',   -- JSON array of track IDs
    current_track_id TEXT,
    position REAL DEFAULT 0,
    volume REAL DEFAULT 1.0,
    muted INTEGER DEFAULT 0,
    shuffle INTEGER DEFAULT 0,
    repeat_mode TEXT DEFAULT 'off',
    updated_at DATETIME
)

app_settings (
    id INTEGER PRIMARY KEY CHECK(id = 1),
    language TEXT DEFAULT 'en',
    theme TEXT DEFAULT 'system',
    lastfm_username TEXT,
    auto_check_update BOOLEAN DEFAULT 1,
    start_at_login BOOLEAN DEFAULT 0,
    eq_enabled BOOLEAN DEFAULT 0,
    lrclib_mode TEXT DEFAULT 'prefer_metadata',
    use_online_artist_artwork BOOLEAN NOT NULL DEFAULT 1,
    enable_lrclib BOOLEAN NOT NULL DEFAULT 1,
    enable_kugou BOOLEAN NOT NULL DEFAULT 1,
    prefer_metadata_lyrics BOOLEAN NOT NULL DEFAULT 1,
    updated_at DATETIME
)
```

### EQ Tables

```sql
eq_profiles (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    is_active INTEGER DEFAULT 0,
    is_default INTEGER DEFAULT 0,
    created_at DATETIME
)

eq_bands (
    profile_id TEXT REFERENCES eq_profiles(id) ON DELETE CASCADE,
    band_index INTEGER,
    frequency REAL NOT NULL,
    gain REAL DEFAULT 0.0,
    bandwidth REAL DEFAULT 1.0,
    PRIMARY KEY (profile_id, band_index)
)
```

### Indexes

```sql
idx_tracks_album_id             ON tracks(album_id)
idx_tracks_sort_title           ON tracks(sort_title)
idx_tracks_is_favorite          ON tracks(is_favorite)
idx_artists_normalization_key   ON artists(normalization_key)
idx_albums_normalization_key    ON albums(normalization_key)
idx_genres_normalization_key    ON genres(normalization_key)
idx_composers_normalization_key ON composers(normalization_key)
```

## Repository Patterns

### TrackRepository — Key Queries

**`GetAll`** and **`GetPaginated`**: Complex SELECT with `LEFT JOIN album_artists`, `LEFT JOIN track_artists`, `GROUP_CONCAT` for aggregating related artists into a comma-separated string, then parsed back into structs.

**`GetByIDs`**: Builds dynamic `IN (?, ?, ...)` placeholder, then re-orders results to match input ID order (SQLite doesn't guarantee order for IN queries).

**`GetByArtistID`**: Joins through `track_artists` junction table.

**`GetMostListened`**: `ORDER BY play_count DESC LIMIT ?`

**`GetRecentlyPlayed`**: `ORDER BY updated_at DESC LIMIT ?` (updated when play count increments)

**`Upsert`**: `INSERT OR REPLACE INTO tracks ...` using sqlx named parameters.

**Junction `Set*` methods**: Wrapped in a transaction — DELETE existing junction rows, then INSERT new ones with positional ordering.

### AlbumRepository — Key Queries

**`GetByArtistID`**: UNION of three conditions:

1. Albums where artist is in `album_artists`
2. Albums where artist is in `track_artists` of any track in the album
3. Albums where artist is in `track_album_artists` of any track in the album

**`DeleteOrphaned`**: `DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT album_id FROM tracks WHERE album_id IS NOT NULL)`

### PlaylistRepository — Track Ordering (LexoRank)

**`AddTrack`**: INSERT with LexoRank position string.

**`RemoveTrack`**: DELETE the row. No position reindexing needed — LexoRank strings are independent.

**`UpdateTrackPosition`**: UPDATE single track's LexoRank position.

**`UpdateTracksPositions`**: Batch UPDATE positions in a transaction (used for rebalancing).

**`GetTracks`**: `JOIN tracks ON ... ORDER BY pt.position, pt.track_id`

## ID Generation

All entity IDs are deterministic UUID v4-style strings derived from MD5 hash of a seed string:

- Track: MD5(file path)
- Artist: MD5(normalization_key)
- Album: MD5(normalization_key + primary_artist_normalization_key)
- Genre/Composer: MD5(normalization_key)
- Playlist: random UUID v4

This ensures the same file always gets the same track ID, and the same artist name always maps to the same artist entity, enabling safe upserts without collision.
