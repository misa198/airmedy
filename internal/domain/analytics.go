package domain

import (
	"context"
	"time"
)

type ListeningRange string

const (
	ListeningRange7D  ListeningRange = "7d"
	ListeningRange30D ListeningRange = "30d"
	ListeningRangeAll ListeningRange = "all"
)

type ListeningSession struct {
	TrackID         string
	StartedAt       time.Time
	EndedAt         time.Time
	ListenedSeconds int
	QualifiedPlay   bool
}

type AnalyticsPoint struct {
	Date            string `json:"date" db:"date"`
	ListenedSeconds int    `json:"listened_seconds" db:"listened_seconds"`
}

type AnalyticsQualityBucket struct {
	Kind  string `json:"kind" db:"kind"`
	Count int    `json:"count" db:"count"`
}

type AnalyticsGenre struct {
	Name            string `json:"name" db:"name"`
	ListenedSeconds int    `json:"listened_seconds" db:"listened_seconds"`
	IsOther         bool   `json:"is_other"`
}

type AnalyticsArtist struct {
	ID              string `json:"id" db:"id"`
	Name            string `json:"name" db:"name"`
	ArtworkKey      string `json:"artwork_key" db:"artwork_key"`
	ListenedSeconds int    `json:"listened_seconds" db:"listened_seconds"`
}

type AnalyticsTrack struct {
	ID              string `json:"id" db:"id"`
	Title           string `json:"title" db:"title"`
	Artist          string `json:"artist" db:"artist"`
	PlayCount       int    `json:"play_count" db:"play_count"`
	ListenedSeconds int    `json:"listened_seconds" db:"listened_seconds"`
}

type AnalyticsInsights struct {
	ListenedSeconds  int                      `json:"listened_seconds"`
	Plays            int                      `json:"plays"`
	ChangePercent    *float64                 `json:"change_percent,omitempty"`
	LibraryTracks    int                      `json:"library_tracks"`
	LibraryAlbums    int                      `json:"library_albums"`
	LibraryArtists   int                      `json:"library_artists"`
	LibraryPlaylists int                      `json:"library_playlists"`
	LibraryBytes     int64                    `json:"library_bytes"`
	Activity         []AnalyticsPoint         `json:"activity"`
	Quality          []AnalyticsQualityBucket `json:"quality"`
	Genres           []AnalyticsGenre         `json:"genres"`
	TopArtists       []AnalyticsArtist        `json:"top_artists"`
	TopTracks        []AnalyticsTrack         `json:"top_tracks"`
}

type ListeningRepository interface {
	RecordSession(ctx context.Context, session ListeningSession) error
	CleanupSessions(ctx context.Context, before time.Time) error
	GetInsights(ctx context.Context, period ListeningRange, now time.Time) (*AnalyticsInsights, error)
}
