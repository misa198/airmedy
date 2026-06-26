# Lyrics

## Summary

Fetches and displays synchronized (LRC) or plain-text lyrics for the current track. Lyrics are sourced from **sibling local files** (`.lrc` / `.txt` next to the audio file, read live at play time), an optional **dedicated lyrics folder** (a single user-chosen folder searched by track basename, gated by `lyrics_folder_enabled`), embedded **file metadata** (`LYRICS` tag, read at scan time), and the external providers **lrclib.net** and **KuGou Music** (cached in SQLite). The frontend parses LRC timestamps and auto-scrolls to the current line.

## Files

| File                                        | Purpose                              |
| ------------------------------------------- | ------------------------------------ |
| `internal/domain/repositories.go`           | `LyricsProvider` + `LocalLyricsReader` port interfaces |
| `internal/app/lyrics/lyrics_service.go`     | Use-case orchestration, CRUD, racing, resolution |
| `internal/infra/lyrics/local.go`            | Sibling `.lrc`/`.txt` file reader    |
| `internal/infra/lyrics/lrclib.go`           | lrclib.net HTTP adapter              |
| `internal/infra/lyrics/kugou.go`            | KuGou Music HTTP adapter             |
| `internal/infra/lyrics/module.go`           | FX wiring for provider group + local reader |
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

`LyricsService.ResolveLyrics(ctx, trackID, audioPath, preferLocal bool, extraLyricsDir string)` picks the best lyric.

The **local tier** is built first: a local lyric file (read live by `LocalLyricsReader`, `.lrc`
preferred over `.txt`) — checked in the **sibling dir** of `audioPath` first, then in `extraLyricsDir`
(the dedicated lyrics folder, when set) — if present, otherwise the embedded metadata tag
(`MetaContent`, cached in DB at scan time). So within the local tier: **sibling file beats dedicated-folder
file beats tag**.

`extraLyricsDir` is supplied by `PlayerService.fetchAndEmitLyrics`: it equals `lyrics_folder_path`
when `lyrics_folder_enabled` is true, otherwise `""` (disabled → sibling-only behavior).
`HasLocalLyrics(ctx, trackID, audioPath, extraLyricsDir)` takes the same extra dir.

`preferLocal` then decides local tier vs external provider content:

| Setting | Effect |
| --- | --- |
| `prefer_local_lyrics=true` | (Default) Use the local tier if available, otherwise fall back to `Content` (external provider). |
| `prefer_local_lyrics=false` | Use `Content` (external provider) if available, otherwise fall back to the local tier. |
| `lyrics_folder_enabled=true` | Also search `lyrics_folder_path` (flat, by basename) for `.lrc`/`.txt`, after the sibling dir. Default off. |
| `enable_lrclib=false` | lrclib.net provider disabled; not queried on fetch. |
| `enable_kugou=false` | KuGou provider disabled; not queried on fetch. |

**Full priority (default `prefer_local_lyrics=true`):**
`Sibling .lrc` → `Sibling .txt` → `Folder .lrc` → `Folder .txt` (when `lyrics_folder_enabled`) →
`Metadata tag` → `LRClib / KuGou`.

The setting was renamed from `prefer_metadata_lyrics` (migration `000022_prefer_local_lyrics`,
`RENAME COLUMN` preserves the existing value).

If both `enable_lrclib` and `enable_kugou` are false, no external fetch is attempted. If resolution returns no cached result and at least one provider is enabled, `PlayerService` triggers an external fetch.

### Local Lyric Files

- Filename must match the audio file's basename exactly, differing only in extension
  (`/music/Song.mp3` → `/music/Song.lrc` or `/music/Song.txt`).
- Searched in the audio file's **sibling dir** first, then the **dedicated lyrics folder**
  (`extraLyricsDir`) if enabled. Both use the same basename match; the dedicated folder is a flat
  lookup (no recursion). Sibling dir always wins. An empty extra dir, or one equal to the sibling dir,
  is skipped (`internal/infra/lyrics/local.go`).
- Read **live at play time** in `PlayerService.fetchAndEmitLyrics` — not cached and not watched, so
  newly added/edited files are picked up on the next play. No rescan needed.
- Paths joined with `filepath.Join` → cross-platform (Unix `/`, Windows `\`).
- Sources: `local-lrc`, `local-txt`. Frontend keys synced/plain off the content (timestamps), so
  `.lrc` renders synced and `.txt` renders plain automatically.

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

// LocalLyricsReader reads a local lyric file by the audio file's basename
// (<base>.lrc preferred, then <base>.txt). The sibling dir is checked first,
// then each extraDir in order (skipping empty / sibling-equal dirs).
// found=false if none exist.
type LocalLyricsReader interface {
    Read(audioPath string, extraDirs ...string) (content, source string, found bool)
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
