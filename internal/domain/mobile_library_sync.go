package domain

import (
	"context"
	"time"
)

const (
	MobileLibrarySyncScopeAll       = "all"
	MobileLibrarySyncScopeArtists   = "artists"
	MobileLibrarySyncScopeAlbums    = "albums"
	MobileLibrarySyncScopeGenres    = "genres"
	MobileLibrarySyncScopePlaylists = "playlists"
)

// MobileLibrarySyncScope is the desktop user's selected source set for one
// mobile device. SelectedIDs is empty only for the all-library scope.
type MobileLibrarySyncScope struct {
	Kind        string   `json:"kind"`
	SelectedIDs []string `json:"selected_ids"`
}

type MobileLibrarySyncAsset struct {
	ID     string `json:"id"`
	Kind   string `json:"kind"`
	SHA256 string `json:"sha256"`
	Size   int64  `json:"size"`
}

// MobileLibrarySyncManifest is an immutable snapshot. Track.Path is always
// blank in this wire object: desktop source paths must never leave the host.
type MobileLibrarySyncManifest struct {
	Version   int                       `json:"version"`
	PlanID    string                    `json:"plan_id"`
	Revision  string                    `json:"revision"`
	Scope     MobileLibrarySyncScope    `json:"scope"`
	Tracks    []*TrackDTO               `json:"tracks"`
	Playlists []*MobileSyncPlaylist     `json:"playlists"`
	Lyrics    map[string]*Lyric         `json:"lyrics"`
	Analysis  map[string]*TrackFeatures `json:"analysis"`
	Assets    []MobileLibrarySyncAsset  `json:"assets"`
}

// MobileSyncPlaylist keeps the source playlist data plus only the track IDs
// present in the manifest, in desktop playlist order.
type MobileSyncPlaylist struct {
	Playlist *Playlist `json:"playlist"`
	TrackIDs []string  `json:"track_ids"`
}

type MobileLibrarySyncPlan struct {
	ID           string                    `json:"id" db:"id"`
	DeviceID     string                    `json:"device_id" db:"device_id"`
	Scope        MobileLibrarySyncScope    `json:"scope" db:"-"`
	Manifest     MobileLibrarySyncManifest `json:"manifest" db:"-"`
	ManifestHash string                    `json:"manifest_hash" db:"manifest_hash"`
	Status       string                    `json:"status" db:"status"`
	Completed    int                       `json:"completed" db:"completed"`
	Total        int                       `json:"total" db:"total"`
	CreatedAt    time.Time                 `json:"created_at" db:"created_at"`
	UpdatedAt    time.Time                 `json:"updated_at" db:"updated_at"`
}

type MobileLibrarySyncPlanRepository interface {
	GetLatest(ctx context.Context, deviceID string) (*MobileLibrarySyncPlan, error)
	Save(ctx context.Context, plan *MobileLibrarySyncPlan) error
	MarkSuperseded(ctx context.Context, deviceID string) error
	MarkReceipt(ctx context.Context, planID, assetID string, at time.Time) (completed int, err error)
	MarkComplete(ctx context.Context, planID string, at time.Time) error
}

// PlaylistMutationLedger persists terminal reconciliation results. Its unique
// (device_id, mutation_id) key makes an HTTP retry safe after a desktop restart.
type PlaylistMutationLedgerEntry struct {
	DeviceID   string    `db:"device_id"`
	MutationID string    `db:"mutation_id"`
	Result     string    `db:"result"`
	CreatedAt  time.Time `db:"created_at"`
}

type PlaylistMutationLedger interface {
	Get(ctx context.Context, deviceID, mutationID string) (*PlaylistMutationLedgerEntry, error)
	Save(ctx context.Context, entry PlaylistMutationLedgerEntry) error
}

// PlaylistMutationLWW stores the durable, per-playlist ordering watermark.
// Deleted playlists retain a watermark so an older CREATE cannot resurrect one.
type PlaylistMutationLWW interface {
	// Claim atomically advances the watermark when (updatedAt, mutationID) wins.
	// A false result means the mutation is stale.
	Claim(ctx context.Context, playlistID string, updatedAt int64, mutationID string, deleted bool) (bool, error)
}

// PlaylistArtworkStaging records artwork uploaded during the short-lived
// reconciliation session. It is intentionally device- and session-owned.
type PlaylistArtworkStaging struct {
	ReconciliationID string    `db:"reconciliation_id"`
	DeviceID         string    `db:"device_id"`
	SHA256           string    `db:"sha256"`
	ArtworkKey       string    `db:"artwork_key"`
	ExpiresAt        time.Time `db:"expires_at"`
}

type PlaylistArtworkStagingRepository interface {
	Save(ctx context.Context, entry PlaylistArtworkStaging) error
	Get(ctx context.Context, reconciliationID, deviceID, sha256 string) (*PlaylistArtworkStaging, error)
	DeleteExpired(ctx context.Context, now time.Time) ([]string, error)
	DeleteReconciliation(ctx context.Context, reconciliationID, deviceID string) ([]string, error)
	ActiveArtworkKeys(ctx context.Context, now time.Time) ([]string, error)
}
