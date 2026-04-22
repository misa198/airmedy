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

### 2.1 Domain Models & Strict Normalization
The domain layer enforces strict normalization. The primary models `Track` and `Album` (`internal/domain/models.go`) contain only their own intrinsic properties and Foreign Key IDs for related entities. 

To facilitate UI rendering without multiple network round-trips or complex client-side joining, we use **Data Transfer Objects (DTOs)** like `TrackDTO` and `AlbumDTO`. These DTOs embed the base model and include pointers to populated related entities (Artist, Album, Genre, Composer).

### 2.2 SQL Tables & Relationships

**`artists`**, **`genres`**, **`composers`**
- `id` (TEXT PRIMARY KEY)
- `name` (TEXT NOT NULL)
- `sort_name` (TEXT NOT NULL) - *Artists only*
- `normalization_key` (TEXT) - Stripped lowercase key used for deduplication.
- `created_at`, `updated_at` (DATETIME)

**`albums`**
- `id` (TEXT PRIMARY KEY)
- `title` (TEXT NOT NULL), `sort_title` (TEXT NOT NULL)
- `normalization_key` (TEXT)
- `artist_id` (TEXT) -> Primary Album Artist.
- `year` (INTEGER), `artwork_key` (TEXT)
- `created_at`, `updated_at` (DATETIME)

**`tracks`** (The central entity)
- `id` (TEXT PRIMARY KEY)
- `path` (TEXT NOT NULL UNIQUE)
- `title`, `sort_title` (TEXT)
- `album_id` (TEXT) -> `FOREIGN KEY (album_id) REFERENCES albums(id) ON DELETE SET NULL`
- **Metadata fields:** `year`, `track_number`, `total_tracks`, `disc_number`, `total_discs`, `duration` (seconds), `bitrate`, `sample_rate`, `format`, `artwork_key`, `file_size`, `mtime`.
- **Raw tags:** `raw_artist_names`, `raw_album_artist_names`, `raw_genre_names`, `raw_composer_names` to retain original string values.
- `created_at`, `updated_at` (DATETIME)

**Junction Tables (Many-to-Many Relationships)**
- `track_artists`, `track_album_artists`, `track_genres`, `track_composers`, `album_artists`: 
  Map entities together, tracking the `position` of each relation to preserve the original tag ordering.

### 2.3 Query Logic & Optimized JOINs
All retrieval operations in `track_repository.go` and `album_repository.go` utilize SQL `LEFT JOIN`s to populate DTOs in a single query. Indexes are maintained on all foreign keys (`artist_id`, `album_id`, etc.) to ensure sub-millisecond join performance even with 10,000+ tracks.

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

---

## 5. Library Synchronization & File Watching

The `LibraryService` (`internal/app/library/service.go`) manages the ingestion of music files and keeps the database in sync with the physical file system.

### 5.1 Watched Folders & Persistence
- **Table:** `watched_folders` stores user-selected root directories.
- **Service Lifecycle:** On `OnStart`, the service loads all watched folders and recursively adds them to the `fsnotify` watcher.

### 5.2 The Import Pipeline
1. **Recursive Scan:** Uses `filepath.WalkDir` to find supported audio extensions (.mp3, .flac, .m4a, .wav, .ogg, .opus, .aiff).
2. **Metadata Extraction:** Calls the `MetadataExtractor` to parse tags.
3. **Entity Resolution:** Generates deterministic MD5-based UUIDs for Artists, Albums, and Tracks. This ensures that even if a file is moved, if the metadata is identical and we use the same seeds, we can maintain relational consistency (though Track IDs are currently path-based to simplify sync).
4. **Persistence:** Batch-upserts data into SQLite and indexes it in Bleve.
5. **Real-time Updates:** The `fsnotify` event loop handles:
    - `Create`/`Write`: Triggers `ImportFile`.
    - `Remove`/`Rename`: Deletes the entity from the database and index using the path-based deterministic ID.
6. Frontend Notification: Emits Wails events (`library:updated`, `library:sync-started`, `library:sync-finished`) to keep the UI reactive.

---

## 6. Frontend Architecture & Shell Layout

The Airmedy frontend is built as a Single Page Application (SPA) using Vue 3, Vite, and Wails v3.

### 6.1 Routing (Vue Router)
- **Mode:** `createWebHashHistory()` is utilized to avoid client-side routing conflicts within the Wails application context, ensuring hot-reloads and navigation functions without 404 errors.
- **Routes:** The core navigation structure includes:
  - `/` (Home)
  - `/recently-added`
  - `/artists`
  - `/albums`
  - `/tracks`
  - `/genres`
  - `/search`
  - `/settings`

### 6.2 Application Shell Layout
- **Resizable Panels:** The main application shell uses Resizable panel components (via ShadCN-vue / vue-resizable-panels) to provide a native macOS-like experience. This allows the user to click and drag the boundary between the Sidebar and the Main Content area.
- **Structure:**
  - **Sidebar (Left Panel):** Contains primary navigation links, Library sections, and user Playlists.
  - **Main Content (Right Panel):** A scrollable area where the `router-view` injects the active page.
  - **Player Footer (Fixed Bottom):** A persistent audio control bar that remains visible across all route transitions.

### 6.3 State Management (Pinia)
- The shell relies on Pinia stores (e.g., `usePlayerStore`, `useLibraryStore`) to maintain continuous playback state globally, decoupled from the active route.