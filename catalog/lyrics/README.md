# Lyrics

## Summary

Fetches and displays synchronized (LRC) or plain-text lyrics for the current track. Lyrics are sourced from **lrclib.net** and **KuGou Music** and cached in SQLite. The frontend parses LRC timestamps and auto-scrolls to the current line.

## Files

| File                                        | Purpose                              |
| ------------------------------------------- | ------------------------------------ |
| `internal/domain/repositories.go`           | `LyricsProvider` port interface      |
| `internal/app/lyrics/lyrics_service.go`     | Use-case orchestration, CRUD, racing |
| `internal/infra/lyrics/lrclib.go`           | lrclib.net HTTP adapter              |
| `internal/infra/lyrics/kugou.go`            | KuGou Music HTTP adapter             |
| `internal/infra/lyrics/module.go`           | FX wiring for provider group         |
| `internal/infra/sqlite/lyric_repository.go` | SQLite persistence                   |
| `internal/infra/wails/lyrics_service.go`    | Wails binding                        |
| `frontend/src/composables/useLyrics.ts`     | LRC parser, synced/plain view        |

## Lyric Model

```go
type Lyric struct {
    TrackID     string
    Content     string    // LRC format or plain text from lrclib
    Source      string    // e.g., "lrclib-synced", "lrclib-plain"
    MetaContent string    // Lyrics extracted from file metadata
    MetaSource  string    // e.g., "metadata-synced", "metadata-plain"
    CreatedAt   time.Time
    UpdatedAt   time.Time
}
```

## Resolution Strategy

`LyricsService.ResolveLyrics(ctx, trackID, preferMetadata bool)` determines which lyrics to display based on three boolean settings stored in the DB:

| Setting | Effect |
| --- | --- |
| `prefer_metadata_lyrics=true` | (Default) Use `MetaContent` if available, otherwise fall back to `Content` (external provider). |
| `prefer_metadata_lyrics=false` | Use `Content` (external provider) if available, otherwise fall back to `MetaContent`. |
| `enable_lrclib=false` | lrclib.net provider disabled; not queried on fetch. |
| `enable_kugou=false` | KuGou provider disabled; not queried on fetch. |

If both `enable_lrclib` and `enable_kugou` are false, no external fetch is attempted. If resolution returns no cached result and at least one provider is enabled, `PlayerService` triggers an external fetch.

## Fetch Strategy

### 1. Exact Fetch

Query `lrclib.net/api/get` with:

- `track_name` — cleaned title (see Title Cleaning below)
- `artist_name` — primary artist name
- `album_name` — album title
- `duration` — track duration in seconds

### 2. Fallback Without Album

If exact fetch returns 404, retry with `album_name` omitted (handles compilation albums where the song is listed under a different album).

### 3. Search and Rank

If both exact attempts fail:

1. `GET lrclib.net/api/search?q={title}+{artist}` → array of candidates
2. Score each candidate:
   - Title similarity ≥ 0.7 (normalized Levenshtein distance) — required threshold
   - Duration diff ≤ 5 seconds — required threshold
   - Final score: `titleSim × 0.5 + artistSim × 0.3 + durationScore × 0.2`
3. Select the highest-scoring candidate above thresholds.

### Title Cleaning

Before sending to lrclib, the title is normalized:

1. Lowercase
2. Trim whitespace
3. Collapse multiple spaces
4. Remove noise patterns (regex): `(feat.`, `[feat.`, `(official`, `[official`, `(live`, `(remaster`, etc.

### Synced vs. Plain Preference

Synced lyrics (with timestamps) are preferred over plain text. If only plain is available, `source` is set to `"lrclib-plain"`.

## Ports

```go
type LyricRepository interface {
    GetByTrackID(ctx, trackID string) (*Lyric, error)
    Save(ctx, lyric *Lyric) error
    Upsert(ctx, lyric *Lyric) error
    Delete(ctx, trackID string) error
}

type LyricsProvider interface {
    Fetch(ctx context.Context, track *TrackDTO) (*Lyric, error)
    Name() string
}
```

Lyrics are stored in the `lyrics` table. It maintains both `content`/`source` (from external providers) and `meta_content`/`meta_source` (from file metadata).

## Provider Adapters

Providers live in `internal/infra/lyrics/` and implement `domain.LyricsProvider`. They return `*domain.Lyric` with content populated but **not persisted** — `LyricsService` calls `saveLyric` after receiving a result.

| Provider | Name() | Endpoint |
|----------|--------|---------|
| `LrclibProvider` | `"lrclib"` | `https://lrclib.net/api/` |
| `KugouProvider` | `"kugou"` | `http://krcs.kugou.com/search` + `https://lyrics.kugou.com/download` |

Providers are wired via FX value group `lyrics_providers`. `LyricsService` receives `[]domain.LyricsProvider` and filters by name based on enabled flags.

## Wails-Exposed Methods

```typescript
GetLyrics(trackID: string): Lyric | null
FetchLyrics(trackID: string, track: TrackDTO): Lyric | null
SaveLyrics(trackID: string, content: string, source: string): void
DeleteLyrics(trackID: string): void
```

`GetLyrics` returns the cached DB entry. `FetchLyrics` always hits the network and currently queries both providers regardless of `enable_lrclib`/`enable_kugou` settings (known limitation — manual refresh bypasses provider toggles).

## Event Delivery

On track load, `PlayerService` calls `LyricsService.GetLyrics()` (or fetches if uncached) and emits:

```
player:lyrics → { track_id, content, source } | null
```

The player store receives this and sets `playerStore.lyrics`.

## LRC Format Parser (`useLyrics.ts`)

**LRC format:**

```
[MM:SS.ms]Lyric text here
[01:23.45]Another line
```

### Parsed Types

```typescript
interface LyricLine {
  text: string;
  secondary?: string; // bilingual: text after "^" or "/"
  time: number; // seconds (float)
}

interface PlainLine {
  primary: string;
  secondary?: string;
}
```

### `isSynced` Detection

Checks if `content` contains at least one valid LRC timestamp pattern: `[MM:SS.ms]`.

### Bilingual Support

Lines can contain bilingual text separated by `^` or `/`:

```
[01:23.45]English text ^ 中文翻译
```

Parsed into `{ text: "English text", secondary: "中文翻译" }`.

## Frontend Display

**`LyricsDrawer.vue`** renders lines with active-line highlighting. Auto-scrolls to keep the current line centered as the track position advances.

**View toggle:** If synced lyrics are available, user can switch between synced (auto-scrolling with highlights) and plain (full text) views.

**Fullscreen player:** Lyrics panel shown as the right column or via tab in the fullscreen overlay.

**Refresh button:** In the track context menu (`context_menu.refresh_lyrics`), calls `FetchLyrics()` to force re-fetch even if cached.

**Manual Search:** In the track context menu (`context_menu.find_lyrics`), opens `FindLyricsDialog.vue`. This allows users to manually search for lyrics by title and artist. It provides a list of candidates from both LRCLIB and KuGou, scored using the same "Search and Rank" logic as the automatic fetch. Users can preview and save the selected lyrics.

**Manual Edit:** Users can manually edit lyrics in the `MetadataEditDialog`. These edits are written to the file's `LYRICS` tag and stored as `meta_content` in the database.
