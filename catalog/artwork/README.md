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

Called via `LibraryService.GetAlbumColors(albumID)` or `LibraryService.GetArtistColors(artistID)` — fetches colors from cached artwork. The artist variant resolves the artist's displayed artwork key (`Artist.ResolveArtworkKey(useOnline, preferLocal)`) before extraction.

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

## Artist Artwork

Artist artwork has three independent sources, **each stored separately** on the
artist row. Every source's cache key is kept; which one is shown is decided at
read time, so toggling the preference switches the displayed image instantly with
no re-fetch or re-scan.

| Source     | Column               | Set by                                  |
| ---------- | -------------------- | --------------------------------------- |
| Manual     | `artwork_key_manual` | User picks an image in ArtistDetailView |
| Local file | `artwork_key_local`  | Scanned `artist.jpg/jpeg/png` from disk |
| Online     | `artwork_key_online` | Background Deezer fetch                 |

**Read-time resolution** (`domain.Artist.ResolveArtworkKey(useOnline, preferLocal)`)
is governed by two settings — **`use_online_artist_artwork`** (gates whether the
Deezer image may ever be shown) and **`prefer_local_artist_artwork`** (a nested
sub-toggle, only relevant/shown when online is on). `manual` always wins; then:

| `use_online` | `prefer_local` | Order (after manual)             |
| ------------ | -------------- | -------------------------------- |
| off          | —              | local only (online **never**)    |
| on           | on             | local → online                   |
| on           | off            | online → local                   |

Turning online **off** never shows the Deezer image, even one already
downloaded (nothing is deleted — it is just not resolved, so re-enabling shows it
again instantly). With online on + prefer-local on, an existing manual/local image
suppresses online entirely (it is neither fetched nor shown). Resolution is
client-side, so toggling either switch swaps the displayed image instantly with no
re-fetch. The Wails layer reads both flags via
`LibraryService.artistArtworkPrefs(ctx) (useOnline, preferLocal)` (defaults
`false, true` when settings are unreadable).

Whether an online fetch is even worthwhile is centralized in
`domain.Artist.ShouldFetchOnline(useOnline, preferLocal)` — true only when online
is on, no manual image exists, no local image suppresses it (under prefer-local),
and no online key is cached yet. All enqueue/worker sites use it so a Deezer image
that would never be displayed is never fetched.

Files (in `internal/app/library/`):

| File                   | Purpose                                                       |
| ---------------------- | ------------------------------------------------------------- |
| `artist_local_image.go`| Local image scan, per-source writes, version-gated rescan     |
| `artist_artwork.go`    | Deezer fetch + background worker                              |

### Write path

Writes go through `LibraryService.writeArtistArtworkSource(artistID, source, load)`
— per-artist locked (keyed `sync.Map` of mutexes), lazily loads + caches the
bytes, skips if that source's key is unchanged, then calls
`ArtistRepository.SetArtworkSource(id, source, key)` (writes one source column;
nil clears just that source). No cross-source precedence at write time.

On every change it emits a global `artist-artwork-updated` Wails event
`{ artist_id, source, key }` (empty `key` = cleared). `ArtistCard.vue` keeps a
reactive copy of the three keys, updates the changed slot, and recomputes the
displayed image — so live changes and preference toggles both reflect instantly.
`ArtistDetailView.vue` also listens for this event and re-runs
`GetArtistColors` so the hero tint follows async artwork changes (e.g. a Deezer
fetch completing).

### Local file scan

- `findArtistImageFile(dir)` looks for `artist.jpg`, then `artist.jpeg`, then
  `artist.png` (priority order).
- An `artist.*` image maps to a directory's **album artist(s)** only — guest /
  track artists are ignored, since the folder represents the album artist's
  discography. `artistIDsForImageDir` resolves them in priority order:
  - **by contained tracks** (authoritative) — distinct album artists of tracks in
    the dir or its subfolders, via `TrackRepository.AlbumArtistIDsByPathPrefix`
    (a single `SELECT DISTINCT … JOIN track_album_artists` — *not* the track DTO,
    whose `AlbumArtists` is not populated by `GetByPathPrefix`);
  - **by folder name** (fallback, only when the dir has no album artists, e.g. an
    artist folder holding only `artist.jpg`, or tracks with no `ALBUMARTIST` tag)
    — `GetByNormalizationKey(NormalizationKey(base(dir)))`.
- **Ambiguity guard:** if one `artist.*` resolves to **>1 album artist** (e.g. a
  various-artists / compilation folder), it is **not mapped to anyone** — a single
  image can't represent multiple artists. Existing DB mappings are left untouched
  (the guard only blocks new writes).
- A full sync resolves images in one batch (`applyLocalArtistImagesForDirs`): the
  walk collects every directory holding an `artist.*`, each image is cached once,
  no per-track stat/read storm. Single-file imports outside a bulk sync fall back
  to the per-track `resolveTrackArtistImages` (gated by the `syncing` flag), which
  applies to the track's **album artists** and skips when there is more than one.
- `artist.*` changes are picked up by the periodic sync scan (no real-time file
  watcher — see [Library catalog](../library/README.md#periodic-sync-scheduler)):
  create/write sets the local source (skipped when ambiguous); a file missing on a
  later scan clears the `local_file` source only (manual/online remain).
- `ScanArtistImages` is a heavier per-artist sweep used only by the version-gated
  rescan (below).

### Version-gated rescan

`maybeRescanArtistImages` runs once on startup when `last_scan_version` is empty
or older than `config.Version` (semver compare), so existing libraries pick up
artist images already on disk after upgrading. Stores the current version after.

### Online (Deezer) fetch

1. `LibraryService.GetArtistArtwork(artistID, eventID)` resolves the display key
   (via both toggles); if non-empty, returns its URL.
2. If `Artist.ShouldFetchOnline(useOnline, preferLocal)` (online on, no manual,
   not suppressed by a local image under prefer-local, no online key yet) the
   request is queued (`artistArtworkQueue`).
3. The background worker (`StartArtistArtworkWorker`) re-checks `ShouldFetchOnline`
   against current settings + artist state and bails if no longer needed; otherwise
   searches Deezer (`https://api.deezer.com/search/artist?q={name}`), downloads
   `picture_medium`, and stores it via `writeArtistArtworkSource` with source
   `online`. The folder-removal path (`service.go`) likewise clears the local source
   then enqueues only when `ShouldFetchOnline` holds.

### Custom image & cache cleanup

- `SelectAndSetArtistArtwork` / `RemoveArtistArtwork` set / clear the `manual`
  source (ArtistDetailView avatar menu).
- `ArtistRepository.GetAllArtworkKeys` returns keys across all three columns;
  `CleanupOrphanedArtworks` unions them with track keys so artist images survive
  while the artist exists and **all** of an artist's cached images are deleted once
  it is orphaned. It runs after a full sync and on folder removal — not per watcher
  delete event (that would rescan the whole cache dir and thrash on bulk deletes).

### Custom image (in-app)

- `LibraryService.SelectAndSetArtistArtwork(artistID)` opens a file picker and
  sets the chosen image with source `manual` (locked).
- `LibraryService.RemoveArtistArtwork(artistID)` clears it, allowing automatic
  sources to repopulate.
- Surfaced in `ArtistDetailView.vue` via the avatar hover/right-click menu.
