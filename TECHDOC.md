# Airmedy Comprehensive Technical Documentation

This document provides an exhaustive technical deep-dive into the Airmedy music player. It details every architectural decision, SQL table, dependency injection mapping, indexing logic, and edge-case handling mechanism implemented in the codebase.

---

## 1. Architecture & Dependency Injection (Fx)

Airmedy adheres strictly to a **Hexagonal Architecture (Ports & Adapters)**. The application state and wiring are fully managed by `uber-go/fx`.

### 1.1 Module Wiring (`internal/app/module.go`)
The core application module wires infrastructure adapters to domain interfaces:
- **Configuration:** Loads `config.Config` containing paths for DB, Cache, and Index.
- **SQLite DB:** Initialized via `sqlite.NewDB` (requires config and slog.Logger). Provided as a singleton.
- **Search Service:** `bleve.NewBleveSearchService` provided as `domain.SearchService`.
- **Artwork Cache:** `artwork.NewDiskArtworkCache` provided as `domain.ArtworkCache`.
- **Metadata Extractor:** `metadata.NewTagLibExtractor()` provided as `domain.MetadataExtractor`.
- **Lifecycle Hooks:** Fx hooks are configured to safely close the SQLite DB and Bleve Index on application shutdown (`OnStop`).

---

## 2. Exhaustive Database Schema & Logic

The persistent data layer is managed by SQLite. Schema is versioned via migrations (`internal/infra/sqlite/migrations`).

### 2.1 Domain Models & Denormalization
The `Track` domain model (`internal/domain/models.go`) intentionally denormalizes data. It stores both IDs (e.g., `ArtistID`) and Names (e.g., `ArtistName`) of related entities. This optimizes UI rendering by avoiding massive JOINs on the `tracks` table when fetching lists of thousands of songs.

### 2.2 SQL Tables & Relationships

**`artists`**
- `id` (TEXT PRIMARY KEY)
- `name` (TEXT NOT NULL)
- `sort_name` (TEXT NOT NULL)
- `created_at`, `updated_at` (DATETIME)

**`albums`**
- `id` (TEXT PRIMARY KEY)
- `title` (TEXT NOT NULL), `sort_title` (TEXT NOT NULL)
- `artist_id` (TEXT) -> `FOREIGN KEY (artist_id) REFERENCES artists(id) ON DELETE SET NULL`
- `artist_name` (TEXT), `year` (INTEGER), `artwork_key` (TEXT)

**`genres`** & **`composers`**
- `id` (TEXT PRIMARY KEY), `name` (TEXT NOT NULL UNIQUE)

**`tracks`** (The central entity)
- `id` (TEXT PRIMARY KEY)
- `path` (TEXT NOT NULL UNIQUE): The critical uniqueness constraint for file system sync.
- `title`, `sort_title` (TEXT)
- **Relationships:**
    - `artist_id`, `album_id`, `genre_id`, `composer_id` -> All `ON DELETE SET NULL` to preserve track entries if taxonomies are purged.
- **Denormalized fields:** `artist_name`, `sort_artist_name`, `album_name`, `sort_album_name`, `album_artist_name`, `genre_name`, `composer_name`.
- **Metadata fields:** `year`, `track_number`, `total_tracks`, `disc_number`, `total_discs`, `duration` (seconds), `bitrate`, `sample_rate`, `format`, `artwork_key`.

**`playlists`** & **`playlist_tracks`**
- `playlists`: Standard `id`, `name`, `description` table.
- `playlist_tracks`: A junction table with a composite primary key (`playlist_id`, `track_id`).
    - Uses `ON DELETE CASCADE` for both foreign keys to auto-clean when playlists or tracks are deleted.
    - Includes `position` (INTEGER NOT NULL) to maintain custom user sorting.

**`lyrics`**
- `track_id` (TEXT PRIMARY KEY) -> `ON DELETE CASCADE` referencing `tracks(id)`.
- `content` (TEXT), `source` (TEXT).

### 2.3 Query Edge Cases & Idempotency
- **Track Upserts:** The `Upsert` method in `track_repository.go` uses SQLite's `ON CONFLICT(path) DO UPDATE SET...`. This ensures that if the file watcher detects a change to an existing file path, the database updates all metadata and timestamps without duplicating the track or requiring a prior `SELECT` check.
- **Sorting:** `GetAll` in `track_repository.go` enforces a strict canonical sort order: `ORDER BY sort_artist_name, sort_album_name, disc_number, track_number`.
- **Playlist Fetching:** `GetTracks` in `playlist_repository.go` uses a JOIN on `playlist_tracks` ordered by `pt.position` to guarantee the UI receives the exact user-defined queue sequence.

---

## 3. Metadata Extraction Logic & Fallbacks

The `TagLibExtractor` (`internal/infra/metadata/taglib.go`) maps raw audio tags to the domain model using a robust fallback chain to handle messy ID3v2/MP4/FLAC metadata.

### 3.1 Tag Resolution Chains
The `firstTag` helper aggressively scans multiple tag frame standards to find values:
- **Year:** Checks `DATE` then `YEAR` and extracts the first 4 characters to safely parse `YYYY-MM-DD` strings into a strict integer.
- **Track/Disc Numbers:** Checks `TRACKNUMBER` / `TRACK` (or `DISCNUMBER` / `DISC`). It parses "1/12" formats by splitting by `/` and assigning the first index to the number, and optionally falling back to the second index for `TOTALTRACKS` if the dedicated total tag is missing.
- **Sort Title:** Tries `TITLESORT` -> `TSOT` -> `sonm`.
- **Sort Artist:** Tries `ARTISTSORT` -> `TSOP` -> `soar`.
- **Album Artist:** Tries `ALBUMARTIST` -> `TPE2` -> `aART`.

### 3.2 Programmatic Sort String Generation
If a sort tag (like `SortArtistName`) is entirely missing, the application automatically generates one via `applySortFallbacks`:
1. Uses the base string (e.g., `ArtistName`).
2. Converts to lowercase.
3. Checks for and strips leading common English articles: `"the "`, `"a "`, `"an "`.
*Edge Case:* "The Beatles" becomes "beatles", ensuring they appear under 'B' rather than 'T' in the library view.

### 3.3 Artwork Extraction Quirks
`ExtractArtwork` attempts to read embedded images. TagLib only returns raw bytes via `ReadImage()`. To get the MIME type necessary for the web frontend to render data-URIs, the extractor parses `ReadProperties().Images` and falls back to `"image/jpeg"` if the MIME type cannot be parsed.

---

## 4. Search Engine Indexing (Bleve)

Airmedy embeds a Bleve indexing engine (`internal/infra/bleve/bleve.go`) mapped to disk, providing highly performant full-text search capabilities without requiring external services like Elasticsearch.

### 4.1 Index Mapping Topology
The Bleve index is strictly segmented by `DocumentMapping` types to prevent bleeding across entities:
- **TextFieldMappings:** The fields `title`, `artist_name`, and `album_name` are mapped using Bleve's `"en"` (English) analyzer. This applies lowercasing, stemming, and stop-word removal automatically.
- **Document Types:**
    - `track`: Maps `title`, `artist_name`, and `album_name`.
    - `album`: Maps `title`, and `artist_name`.
    - `artist`: Maps `name` to the `artist_name` analyzer.

### 4.2 Query Execution
When the user executes a search, `Search(queryStr)`:
1. Constructs a `bleve.NewMatchQuery` (which is fuzzy/typo-tolerant by default).
2. Requests the `id` and `type` fields to be returned in the hit results.
3. Limits the `Size` to 50 results to ensure rapid UI rendering.
4. Returns a unified slice of `SearchResult` objects containing the entity ID, the entity Type (Track, Album, Artist), and the relevance `Score`, allowing the frontend to split the results into separate categorized carousel lists.