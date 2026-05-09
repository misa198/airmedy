# Artwork

## Summary

The artwork feature manages album art: storing extracted images to disk, generating resized variants for performance, extracting dominant color palettes for dynamic theming, and cleaning up orphaned files.

## Files

| File                                | Purpose                                   |
| ----------------------------------- | ----------------------------------------- |
| `internal/infra/artwork/cache.go`   | Disk cache management                     |
| `internal/infra/artwork/resize.go`  | Image downsampling                        |
| `internal/infra/artwork/palette.go` | K-means color extraction                  |
| `internal/domain/artwork.go`        | ArtworkCache interface                    |
| `internal/infra/wails/assets.go`    | Asset handler — serve artwork to frontend |

## ArtworkCache Interface

```go
type ArtworkCache interface {
    Save(ctx context.Context, data []byte, mimeType string) (string, error)
    GetPath(key string) string
    GetVariantPath(key, variant string) string
    Exists(key string) bool
    CleanupOrphaned(ctx context.Context, activeKeys map[string]bool) error
}
```

## Disk Cache

**Location:** `$XDG_DATA_HOME/airmedy/artwork/`

**Key generation:** SHA256 hash of the raw image bytes. Same artwork across different albums reuses a single file (content-addressed).

**Storage format:** Original saved as `{hash}.jpg` or `{hash}.png` depending on MIME type.

**Variants** generated asynchronously after save:

| Variant name    | Dimensions | Format | Quality |
| --------------- | ---------- | ------ | ------- |
| `{hash}_sm.jpg` | 64×64px    | JPEG   | 85      |
| `{hash}_md.jpg` | 500×500px  | JPEG   | 85      |

Variants are used by the frontend: `sm` for mini player and track rows, `md` for album cards and player footer, original for full-screen player.

## Image Resize (`resize.go`)

- Aspect ratio preserved, image is cropped to square if needed before resizing.
- Nearest-neighbor interpolation (fast, acceptable for downscaling to small sizes).

## Palette Extraction (`palette.go`)

Called via `LibraryService.GetAlbumColors(albumID)` — fetches colors from cached artwork.

### Algorithm

1. **Decode** the cached JPEG/PNG.
2. **Downsample** to 64×64px thumbnail for speed.
3. **Collect pixels:** Non-transparent pixels only (alpha ≥ `0x8000`).
4. **K-means clustering:** k=3, 10 iterations. Each cluster centroid is an RGB color.
5. **Classify clusters:**
   - **Vibrant** — cluster with highest `saturation × value` (HSV) score.
   - **Dominant** — cluster with the largest pixel count.
   - **Muted** — the remaining cluster.
6. Return as `ThemeColors{Vibrant, Dominant, Muted}` as hex strings (`#RRGGBB`).

### ThemeColors

```go
type ThemeColors struct {
    Vibrant  string  // highest saturation × value
    Muted    string  // lowest saturation
    Dominant string  // most pixels
}
```

### Frontend Usage

The player store receives `ThemeColors` via the `player:theme` event on each track load. `App.vue` applies them to CSS custom properties:

```javascript
document.documentElement.style.setProperty("--dynamic-primary", vibrant);
document.documentElement.style.setProperty(
  "--dynamic-surface",
  hexToRgba(dominant, 0.15),
);
document.documentElement.style.setProperty(
  "--dynamic-glow",
  hexToRgba(vibrant, 0.4),
);
```

Transitions use `1.5s ease-in-out` for a smooth color wash effect.

## Asset Handler (`assets.go`)

Custom Wails v3 asset handler registered at app startup. Maps incoming requests for artwork keys to file paths:

- `{key}` → `ArtworkCache.GetPath(key)` (original)
- `{key}?v=sm` → `ArtworkCache.GetVariantPath(key, "sm")`
- `{key}?v=md` → `ArtworkCache.GetVariantPath(key, "md")`

Returns 404 if the key doesn't exist in cache.

## Orphan Cleanup

After every sync, `CleanupOrphaned(ctx, activeKeys)` compares all files in the artwork directory against `activeKeys` (built from `TrackRepository.GetAllArtworkKeys()`). Files not in the active set (original and variants) are deleted.

## Frontend Artwork URL Construction

```typescript
// stores/player.ts computed
artworkUrl = `wails://artwork/${artworkKey}`;
artworkUrlSm = `wails://artwork/${artworkKey}?v=sm`;
artworkUrlMd = `wails://artwork/${artworkKey}?v=md`;
```

Fallback: if `artworkKey` is empty, a placeholder image is shown.

## Artist Artwork (Online)

Artist artwork is fetched dynamically from the Deezer API when enabled in settings (`UseOnlineArtistArtwork`).

### Fetching Flow

1. The frontend calls `LibraryService.GetArtistArtwork(artistID, eventID)`.
2. If the artist already has an `artwork_key` in the database, the cached URL is returned immediately.
3. If not cached, the request is placed on an asynchronous queue (`artistArtworkQueue`).
4. A background worker (`StartArtistArtworkWorker`) picks up the job:
   - Verifies the `UseOnlineArtistArtwork` setting is enabled.
   - Searches the Deezer API (`https://api.deezer.com/search/artist?q={name}`).
   - Downloads the `picture_medium` image.
   - Saves it to the standard `ArtworkCache` (which generates the standard variants).
   - Updates the `artists` table with the new `artwork_key`.
   - Emits a Wails event using the provided `eventID` with the new artwork URL.
5. The frontend component (`ArtistCard.vue`) listens for this event to dynamically display the fetched image.
