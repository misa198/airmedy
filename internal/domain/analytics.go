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
	ID              string
	SourceDeviceID  string
	TrackID         string
	StartedAt       time.Time
	EndedAt         time.Time
	ListenedSeconds int
	QualifiedPlay   bool
}

type PlaybackEndReason string

const (
	PlaybackEndCompleted PlaybackEndReason = "completed"
	PlaybackEndSkipped   PlaybackEndReason = "skipped"
	PlaybackEndStopped   PlaybackEndReason = "stopped"
)

// PlaybackAttempt is one continuous selection of a track. Unlike listening
// sessions, it survives pause/resume and is used for completion/skip rates.
type PlaybackAttempt struct {
	ID                   string
	SourceDeviceID       string
	TrackID              string
	StartedAt            time.Time
	EndedAt              time.Time
	StartPositionSeconds float64
	ListenedSeconds      int
	EndReason            PlaybackEndReason
}

type AnalyticsPoint struct {
	Date            string `json:"date" db:"date"`
	ListenedSeconds int    `json:"listened_seconds" db:"listened_seconds"`
}

type AnalyticsLibraryGrowthPoint struct {
	Date       string `json:"date" db:"date"`
	TrackCount int    `json:"track_count" db:"track_count"`
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

type LibraryInsights struct {
	LibraryTracks    int                           `json:"library_tracks"`
	LibraryAlbums    int                           `json:"library_albums"`
	LibraryArtists   int                           `json:"library_artists"`
	LibraryPlaylists int                           `json:"library_playlists"`
	LibraryBytes     int64                         `json:"library_bytes"`
	LibraryGrowth    []AnalyticsLibraryGrowthPoint `json:"library_growth"`
	Quality          []AnalyticsQualityBucket      `json:"quality"`
}

type ListeningInsights struct {
	ListenedSeconds       int               `json:"listened_seconds"`
	Plays                 int               `json:"plays"`
	Attempts              int               `json:"attempts"`
	Completed             int               `json:"completed"`
	Skipped               int               `json:"skipped"`
	Stopped               int               `json:"stopped"`
	CompletionRate        *float64          `json:"completion_rate,omitempty"`
	SkipRate              *float64          `json:"skip_rate,omitempty"`
	AverageSessionSeconds int               `json:"average_session_seconds"`
	StreakDays            int               `json:"streak_days"`
	ChangePercent         *float64          `json:"change_percent,omitempty"`
	Activity              []AnalyticsPoint  `json:"activity"`
	Genres                []AnalyticsGenre  `json:"genres"`
	TopArtists            []AnalyticsArtist `json:"top_artists"`
	TopTracks             []AnalyticsTrack  `json:"top_tracks"`
}

type ListeningSyncSession struct {
	ID              string `json:"id"`
	SourceDeviceID  string `json:"source_device_id"`
	TrackID         string `json:"track_id"`
	StartedAt       int64  `json:"started_at"`
	EndedAt         int64  `json:"ended_at"`
	ListenedSeconds int    `json:"listened_seconds"`
	QualifiedPlay   bool   `json:"qualified_play"`
}

type ListeningSyncAttempt struct {
	ID              string `json:"id"`
	SourceDeviceID  string `json:"source_device_id"`
	TrackID         string `json:"track_id"`
	StartedAt       int64  `json:"started_at"`
	EndedAt         int64  `json:"ended_at"`
	StartPositionMS int64  `json:"start_position_ms"`
	ListenedSeconds int    `json:"listened_seconds"`
	EndReason       string `json:"end_reason"`
}

type DailyTrackListeningStat struct {
	SourceDeviceID  string `json:"source_device_id" db:"source_device_id"`
	LocalDate       string `json:"local_date" db:"local_date"`
	TrackID         string `json:"track_id" db:"track_id"`
	ListenedSeconds int    `json:"listened_seconds" db:"listened_seconds"`
	PlayCount       int    `json:"play_count" db:"play_count"`
}

type DailyPlaybackAttemptStat struct {
	SourceDeviceID  string `json:"source_device_id" db:"source_device_id"`
	LocalDate       string `json:"local_date" db:"local_date"`
	Attempts        int    `json:"attempts" db:"attempts"`
	Completed       int    `json:"completed" db:"completed"`
	Skipped         int    `json:"skipped" db:"skipped"`
	Stopped         int    `json:"stopped" db:"stopped"`
	ListenedSeconds int    `json:"listened_seconds" db:"listened_seconds"`
}

type ListeningSyncSnapshot struct {
	Version          int                        `json:"version"`
	ReconciliationID string                     `json:"reconciliation_id"`
	Sessions         []ListeningSyncSession     `json:"sessions"`
	Attempts         []ListeningSyncAttempt     `json:"attempts"`
	DailyTracks      []DailyTrackListeningStat  `json:"daily_tracks"`
	DailyAttempts    []DailyPlaybackAttemptStat `json:"daily_attempts"`
	Signature        string                     `json:"signature"`
}

type ListeningRepository interface {
	RecordSession(ctx context.Context, session ListeningSession) error
	CleanupSessions(ctx context.Context, before time.Time) error
	RecordAttemptStart(ctx context.Context, attempt PlaybackAttempt) error
	FinalizeAttempt(ctx context.Context, attempt PlaybackAttempt) error
	RecoverOpenAttempts(ctx context.Context) error
	GetLibraryInsights(ctx context.Context, period ListeningRange, now time.Time) (*LibraryInsights, error)
	GetListeningInsights(ctx context.Context, period ListeningRange, sourceDeviceID string, now time.Time) (*ListeningInsights, error)
	ExportSnapshot(ctx context.Context, reconciliationID string, since time.Time) (*ListeningSyncSnapshot, error)
	ImportSnapshot(ctx context.Context, snapshot *ListeningSyncSnapshot) error
}
