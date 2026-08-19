# Mobile Library Sync Protocol v1

## Summary

Library Sync is a desktop-to-mobile, pull-based transfer protocol. It runs only
after [mobile pairing](../pairing/README.md) has established a trusted desktop
and mobile Ed25519 key pair. Desktop owns scope selection and creates an
immutable plan; mobile downloads a manifest and its assets over LAN HTTP, then
acknowledges verified writes through MQTT.

Android implements the protocol with a Room-backed mirror. Each track retains
the full manifest `TrackDTO` JSON (without desktop `path`) as its canonical
metadata snapshot, while indexed projection fields support the mobile UI.
New sync plans stage their metadata and assets, atomically activate after all
assets verify, then remove the old mirror; initiating Sync again therefore
replaces changed metadata while reusing unchanged content-addressed assets.

## Preconditions and lifecycle

- The mobile has a paired desktop identity and connects with client ID
  `airmedy-sync-<desktop-id>-<mobile-id>`.
- A desktop user selects either the entire library or selected IDs from exactly
  one source kind: `artists`, `albums`, `genres`, or `playlists`.
- Pressing Sync with an unfinished identical scope re-announces the exact same
  immutable plan. Pressing it after completion creates a new plan/revision.
- If the desktop replaces an unfinished scope, the old plan becomes unavailable.
  Mobile must discard its in-progress state for that plan when it receives a
  newer request.
- Cancel on desktop, including automatically when the mobile MQTT session goes
  Offline, stops an in-progress plan preparation or marks an active plan
  superseded. Its HTTP endpoints then return `404`; mobile passively leaves its
  existing mirror intact and waits for a later desktop request.
- Desktop startup also supersedes every persisted active plan: its ephemeral
  HTTP server and MQTT request cannot survive a desktop restart, so the Sync UI
  must not present an interrupted plan as still syncing.
- Mobile must perform mirror deletion only after it has atomically applied a
  plan and is ready to publish the final completion receipt. An interrupted
  transfer must leave existing synced content intact.

## MQTT transport

All sync messages are UTF-8 JSON, QoS 1, non-retained, and under the pairing
broker. A mobile session is allowed to subscribe/publish only its own topics.

| Direction | Topic |
| --- | --- |
| Desktop → mobile | `airmedy/library-sync/v1/<desktop-id>/<mobile-id>/request` |
| Mobile → desktop | `airmedy/library-sync/v1/<desktop-id>/<mobile-id>/receipt` |

### Desktop request

```json
{
  "version": 1,
  "type": "library.sync.request",
  "plan_id": "uuid",
  "desktop_id": "uuid",
  "mobile_id": "uuid",
  "manifest_url": "http://192.168.1.10:49152/mobile-sync/v1/plans/<plan-id>/manifest",
  "manifest_hash": "lowercase-sha256-hex",
  "issued_at": 0,
  "signature": "base64url-no-padding"
}
```

`issued_at` is Unix epoch milliseconds. Mobile validates all identity fields,
validates the desktop signature using the public key saved from QR pairing, then
downloads the manifest. The signature input is compact UTF-8 JSON for the same
object with `signature` set to `""`, keys in the field order shown above, and no
whitespace. This matches Go `encoding/json` serialization exactly.

### Mobile receipt

Publish one receipt only after the identified asset was downloaded, SHA-256
verified, and durably written with any metadata transaction it needs. Duplicate
receipts are safe.

```json
{
  "version": 1,
  "type": "library.sync.receipt",
  "plan_id": "uuid",
  "mobile_id": "uuid",
  "asset_id": "audio:<track-id>",
  "complete": false,
  "issued_at": 0,
  "signature": "base64url-no-padding"
}
```

The signature input is compact UTF-8 JSON with the same field order and an empty
`signature`, signed with the paired mobile Ed25519 private key. Desktop rejects
unknown devices, invalid signatures, wrong plan IDs, and timestamps more than
five minutes in either direction from desktop time.

After all desired assets and database updates are committed, mobile mirrors
deletions for its desktop-sync collection and publishes exactly one final
receipt:

```json
{
  "version": 1,
  "type": "library.sync.receipt",
  "plan_id": "uuid",
  "mobile_id": "uuid",
  "asset_id": "",
  "complete": true,
  "issued_at": 0,
  "signature": "base64url-no-padding"
}
```

Only this receipt marks the desktop plan complete.

## Authenticated HTTP pull

Desktop starts an ephemeral LAN HTTP server before publishing the request. It
serves only active plans; no desktop filesystem path is exposed. The manifest
response body is the exact compact JSON byte sequence used to compute
`manifest_hash` (it has no encoder-added trailing newline), because mobile
hashes the downloaded response bytes before it begins asset transfer.

| Method | Endpoint | Response |
| --- | --- | --- |
| `GET` | `/mobile-sync/v1/plans/<plan-id>/manifest` | JSON manifest, `ETag: <manifest_hash>` |
| `GET` | `/mobile-sync/v1/plans/<plan-id>/assets/<asset-id>` | Original asset bytes, `ETag` and `X-Airmedy-SHA256` |

Every request must include:

```text
X-Airmedy-Mobile-ID: <paired mobile UUID>
X-Airmedy-Timestamp: <UTC RFC3339Nano timestamp>
X-Airmedy-Nonce: <new opaque random value>
X-Airmedy-Signature: <base64url-no-padding Ed25519 signature>
```

The signature input is UTF-8 text, joined with `\n` and without a trailing
newline:

```text
GET
<escaped request path>
<X-Airmedy-Timestamp>
<X-Airmedy-Nonce>
```

For example, use the literal escaped path
`/mobile-sync/v1/plans/<plan-id>/assets/audio:track-uuid`. Sign with the mobile
private key. Desktop verifies against the stored paired public key, permits a
five-minute clock skew, and rejects reuse of the same device+nonce for five
minutes. Use a new nonce for every manifest and asset request, including retry.

## Manifest schema and apply order

The manifest is a self-contained snapshot:

```json
{
  "version": 1,
  "plan_id": "uuid",
  "revision": "content-revision-sha256",
  "scope": { "kind": "artists", "selected_ids": ["uuid"] },
  "tracks": ["TrackDTO without path"],
  "playlists": [{ "playlist": "Playlist", "track_ids": ["uuid"] }],
  "lyrics": { "track-id": "Lyric" },
  "analysis": { "track-id": "TrackFeatures" },
  "assets": [{ "id": "audio:<track-id>", "kind": "audio", "sha256": "hex", "size": 0 }]
}
```

`tracks`, `playlists`, and `assets` are collections. Desktop's Go encoder may
emit `null` for an empty collection (notably `playlists` for a scope that does
not include playlists); mobile must treat that form exactly as an empty array.

An included playlist is represented even when it has no tracks: its
`track_ids` is an empty array. This preserves empty desktop playlists in the
mobile mirror for `all` and explicit `playlists` scopes.

`tracks` contains the full Airmedy normalized track metadata: album, artists
(including each artist's manual/local/online artwork source keys), album artists,
genres, composers, raw metadata, format/technical fields and track artwork keys.
The nested album projection preserves all album fields, including canonical
`sort_title`, timestamps, and normalization metadata, so mobile album-name and
date-added sorting use the same data as the desktop library. Batch manifests
also preserve ordered album artists, genres, and composers as their canonical
objects. Mobile derives its entity lists and genre/composer membership exclusively
from those objects; raw-name fields remain display metadata and are never re-split
or inferred on mobile.
`lyrics` is keyed by track ID and contains only the
desktop-effective `content`/`source` snapshot for each track (not raw metadata
and provider alternatives). `analysis` is keyed by track
ID and is absent for tracks without analysis. Artist, album, playlist and track
artwork are represented by deduplicated `artwork:<artwork-key>` assets. Audio is
represented by exactly one `audio:<track-id>` asset per track.

Playlist membership contains only tracks included in the selected scope, in the
desktop playlist order. A mobile database must treat this as the membership for
the mirrored sync collection; it must not attempt to download non-manifest
playlist tracks.

Recommended mobile apply sequence:

1. Download manifest and verify its SHA-256 equals MQTT `manifest_hash`.
2. Compare asset SHA-256 plus size against the durable local asset cache; reuse
   an already-verified file across plan revisions and pull only missing or
   changed assets, validating each `X-Airmedy-SHA256` header and downloaded bytes.
3. Upsert metadata, relationships, lyrics, analysis and playlist membership in
   a local transaction; keep downloads staged until their rows reference them.
4. Publish durable per-asset receipts as assets are committed.
5. Delete tracks/entities/assets outside this plan's mirrored collection only
   after all upserts succeed, then publish the final `complete` receipt.

## Resume and error handling

- Keep the latest plan ID and downloaded asset hashes locally. A repeated MQTT
  request for the same plan resumes from this state.
- A `404` means the plan was superseded, completed, or desktop restarted without
  an active plan; wait for the next request and do not delete local content.
- A `401` means headers, timestamp, nonce, or signature are invalid. Generate a
  new timestamp/nonce/signature and retry once; do not retry indefinitely.
- A hash mismatch means source data changed while the plan was active. Do not
  acknowledge that asset; wait for the user to press Sync again and receive a
  fresh plan.
- MQTT is not encrypted in v1, so sync metadata and bytes are observable on the
  LAN. Signatures provide authenticity and replay protection, not confidentiality.

## Android client

Android implements protocol validation and transfer ordering in `sharedLogic`.
The Android app supplies the native HTTP asset puller and Room-backed mirror
store. It saves complete metadata JSON plus indexed track fields, playlist
membership, lyrics, analysis, plan checkpoints, and asset paths in app-private
storage. A prepared plan is activated atomically; old mirrored content is
removed only after the replacement is complete and before the final receipt.
Assets are content-addressed by SHA-256 and shared across plan revisions, so a
fresh plan reuses unchanged verified files rather than downloading them again.
Distinct content hashes download concurrently in bounded batches of two to four
workers (`availableProcessors / 2`, capped at four); assets with the same hash
and size share one staged file and are committed together. Receipt publication,
progress updates, activation, and finalization remain serialized. A newer plan
cancels and waits for the prior foreground transfer to finish cleanup before it
can begin, preventing concurrent writers to the shared asset cache.

When an already-online paired MQTT session receives a valid request, Android
starts a `dataSync` foreground service. The service temporarily uses that
app-owned MQTT session while it pulls assets and publishes QoS 1 receipts, so
an active transfer survives the Activity going to the background. Its ongoing
notification shows progress and exposes cancellation. When the service stops,
the app-owned session remains connected while the app process is alive, so the
desktop can announce a later plan. A fallback session created by the service is
disconnected when it stops; persisted staging permits a re-announced plan to
resume after failure, cancellation, revocation, or timeout.

Android does not keep a foreground service alive solely to wait for future
plans. Its app-owned MQTT session stays available only while the app process is
alive; if the process is no longer alive, the user must reopen the app before
desktop re-announces the plan.

When a foreground sync is active, reopening the app must not create its normal
UI MQTT session: MQTT client IDs are device-scoped, so a second connection would
disconnect the foreground service's session and interrupt the transfer. The UI
instead observes `AndroidSyncRuntime` progress until the service finishes.

## Listening reconciliation

Before desktop creates a library plan, the existing signed reconciliation
request also supplies `listening_url`. Android POSTs a version-1 snapshot with
raw sessions/attempts from the last 180 days plus all-time daily aggregates.
It uses the existing method/path/body-hash/timestamp/nonce Ed25519 authorization
and has a 32 MiB body ceiling and a 200,000-record ceiling.

Desktop accepts only rows whose `source_device_id` equals the authenticated
mobile ID, imports them transactionally, then returns the union of every known
source. The desktop signs that exact response snapshot with its paired Ed25519
key; Android verifies it with the QR-pinned desktop public key before merging
it into Room and publishing the terminal
MQTT reconciliation result. Raw IDs and per-source aggregate keys make retries
and propagation through multiple mobiles idempotent. Listening data is
device-wide and is exchanged regardless of selected library scope; Android may
retain records for tracks outside its current mirror.

## Playlist mutation foundation

Android now retains a durable, idempotent `PlaylistMutation` queue alongside
the mirrored playlist snapshot. Mutations are deltas rather than full playlist
documents so a future two-way reconciliation can safely edit a playlist when
the mobile scope contains only some of its tracks. The queue is intentionally
not connected to a Compose playlist UI yet. `AndroidLibrarySyncStore` exposes
the pending/acknowledge boundary for the protocol layer. Room migration 6 adds
the mutation table; migration 7 adds durable playlist-artwork staging metadata.

Favorites use this same signed reconciliation session and durable queue. The
Fullscreen Player adds a `SET_FAVORITE` delta (`playlist_id: "favorites"`, track
ID, desired state, timestamp, and UUID), which immediately overlays the local
mirror. Before the next plan is built desktop applies the delta through a
device+mutation ledger and per-track LWW watermark, so the following scoped
snapshot contains accepted mobile and desktop favorite changes. Favorites keeps
its virtual membership, but accepts `SET_ARTWORK` and `REMOVE_ARTWORK` mutations
so a mobile-selected cover remains authoritative in the following snapshot.

### Playlist scope boundary

Playlist data is not inferred from selected tracks. A manifest includes playlists only for the `all` scope or for explicit `playlists` selections. Mutations are accepted only for `all`, or an explicitly selected playlist; artist, album, and genre scopes return `scope-conflict` and do not touch a playlist outside the selected resource set.

### Reconciliation security and artwork staging

Playlist reconciliation HTTP signatures bind the method, escaped path,
lowercase SHA-256 of the exact request body, RFC3339 timestamp, and nonce.
Desktop restores the body after verification and accepts a nonce once per
device for five minutes.

Desktop stores a global per-playlist LWW watermark `(updated_at, mutation_id)`
and DELETE tombstone. The lexical mutation ID breaks timestamp ties. Ledger,
watermark, and playlist mutation share one SQLite transaction. Artwork staging
is owned by reconciliation ID, device ID, and SHA-256 for the 30-second
reconciliation lifetime; terminal or expired staging is removed before orphan
artwork cleanup.

Android Room stores staged artwork SHA-256, MIME, byte size, and relative path.
It verifies and uploads the artwork before its `SET_ARTWORK` batch mutation.
Acknowledged staging stays visible until the replacement plan activates, avoiding
a temporary desktop-cover flash; it is then removed when no pending or local
playlist still references the hash.
