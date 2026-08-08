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
