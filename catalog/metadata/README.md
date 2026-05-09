# Metadata Extraction & Writing

## Summary

The metadata feature handles reading audio file tags (ID3, Vorbis, MP4/iTunes, etc.) via TagLib, normalizing raw strings, resolving entity relationships, and writing user edits back to files.

## Files

| File                                     | Purpose                                       |
| ---------------------------------------- | --------------------------------------------- |
| `internal/infra/metadata/taglib.go`      | TagLib integration — extract and write        |
| `internal/domain/metadata.go`            | MetadataExtractor / MetadataWriter interfaces |
| `internal/domain/metadata_processing.go` | String normalization and artist splitting     |

## MetadataExtractor Interface

```go
type MetadataExtractor interface {
    Extract(ctx context.Context, path string) (*TrackDTO, error)
    ExtractArtwork(ctx context.Context, path string) ([]byte, string, error)
    ExtractLyrics(ctx context.Context, path string) (content string, isSynced bool, err error)
}
```

**Library:** `go.senan.xyz/taglib` — Go bindings to the TagLib C++ library.

## Tag Mapping Strategy

TagLib exposes a flat tag map. The extractor tries multiple keys (ID3v2, Vorbis comments, iTunes/MP4) for each field:

| Field        | Tag keys tried                |
| ------------ | ----------------------------- |
| Title        | `TITLE`                       |
| Artist       | `ARTIST`, `TPE1`, `©ART`      |
| Album Artist | `ALBUMARTIST`, `TPE2`, `aART` |
| Album        | `ALBUM`                       |
| Genre        | `GENRE`, `TCON`               |
| Composer     | `COMPOSER`, `TCOM`            |
| Year         | `DATE`, `YEAR`, `TDRC`        |
| Track Number | `TRACKNUMBER`, `TRACK`, `TRKN`|
| Disc Number  | `DISCNUMBER`, `DISC`, `TPOS`  |
| BPM          | `BPM`, `TBPM`, `tmpo`         |
| Label        | `LABEL`, `PUBLISHER`, `TPUB`  |
| ISRC         | `ISRC`, `TSRC`                |
| Copyright    | `COPYRIGHT`, `TCOP`, `cprt`   |
| Lyrics       | `LYRICS`                      |

Values like `"3/12"` (track/total) are parsed to extract both the number and total.

## Artwork Extraction

`ExtractArtwork()` returns raw bytes and MIME type. MIME is detected from the data header if the tag doesn't specify it. The caller (`library/service.go`) passes these to `ArtworkCache.Save()`.

## Normalization Functions (`metadata_processing.go`)

### `NormalizationKey(s string) string`

Used for entity deduplication. Produces a stable, comparable key:

1. Lowercase
2. Trim leading/trailing whitespace
3. Collapse multiple spaces
4. `FoldUnicode()` — remove diacritics

Example: `"Björk"` → `"bjork"`, `"The Beatles"` → `"the beatles"`

### `NormalizeSort(s string) string`

Used for `sort_title` / `sort_name` — produces a user-friendly sort key:

1. Remove leading articles: `"The "`, `"A "`, `"An "`
2. `FoldUnicode()`
3. Remove leading punctuation
4. Pad embedded numbers to 4 digits (e.g., `"Track 2"` → `"Track 0002"`)

### `FoldUnicode(s string) string`

NFKD decomposition to separate base characters from diacritics, then strips non-spacing marks. Special case: `đ` → `d` (Vietnamese).

### `SplitArtists(raw string) []string`

Splits a raw multi-artist string into individual names using a robust regular expression.

**Hard delimiters** (always split): `,`, `;`, `|`, `/`, `\`

**Keywords** (split if appearing as full words, optionally followed by a dot, or as ampersands):

- `ft`, `feat`, `featuring`, `with`, `vs`, `&`, `and` (case-insensitive)

Whitespace around delimiters and keywords is ignored.

Example: `"Artist A, Artist B feat. Artist C"` → `["Artist A", "Artist B", "Artist C"]`

## MetadataWriter Interface

```go
type MetadataWriter interface {
    WriteMetadata(ctx context.Context, path string, fields MetadataUpdate) error
}
```

### MetadataUpdate

```go
type MetadataUpdate struct {
    Title       string
    Artist      string
    AlbumTitle  string
    Genre       string
    Composer    string
    Year        int
    TrackNumber int
    TotalTracks int
    DiscNumber  int
    TotalDiscs  int
    BPM         int
    Label       string
    ISRC        string
    Lyrics      string
    ArtworkData []byte
    ArtworkMIME string
}
```

After writing tags and optional artwork, `library/service.go` re-extracts the file and upserts the updated track to DB and search index.

## Raw Metadata Storage

All extracted tags are serialized as JSON and stored in `tracks.other_metadata` (TEXT column). This allows future migrations to re-parse additional fields without re-scanning files.

## Supported Formats

TagLib handles: MP3 (ID3v1/v2), FLAC (Vorbis), M4A/AAC (iTunes atoms), WAV, OGG, Opus, AIFF. For formats TagLib cannot decode (APE, WavPack, DSD), FFmpeg is invoked as a fallback decoder on macOS. On Windows and Linux, FFmpeg is the primary decoder for all formats to ensure consistent and high-performance playback.
