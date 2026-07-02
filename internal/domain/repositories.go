package domain

import "context"

type TrackRepository interface {
	GetByID(ctx context.Context, id string) (*TrackDTO, error)
	GetByPath(ctx context.Context, path string) (*TrackDTO, error)
	GetByPathPrefix(ctx context.Context, prefix string) ([]*TrackDTO, error)
	// AlbumArtistIDsByPathPrefix returns the distinct album-artist IDs of tracks
	// whose path starts with prefix. Used to map a local artist image to the
	// folder's album artist(s) without loading full track DTOs.
	AlbumArtistIDsByPathPrefix(ctx context.Context, prefix string) ([]string, error)
	GetByAlbumID(ctx context.Context, albumID string) ([]*TrackDTO, error)
	GetByArtistID(ctx context.Context, artistID string) ([]*TrackDTO, error)
	GetByGenreID(ctx context.Context, genreID string) ([]*TrackDTO, error)
	GetByComposerID(ctx context.Context, composerID string) ([]*TrackDTO, error)
	GetAll(ctx context.Context) ([]*TrackDTO, error)
	GetPaginated(ctx context.Context, offset, limit int) ([]*TrackDTO, error)
	GetByIDs(ctx context.Context, ids []string) ([]*TrackDTO, error)
	Count(ctx context.Context) (int, error)
	GetFavorites(ctx context.Context) ([]*TrackDTO, error)
	ToggleFavorite(ctx context.Context, id string) (bool, error)
	IncrementPlayCount(ctx context.Context, id string) error
	GetMostListened(ctx context.Context, limit int) ([]*TrackDTO, error)
	GetLeastListened(ctx context.Context, limit int) ([]*TrackDTO, error)
	GetRecentlyPlayed(ctx context.Context, limit int) ([]*TrackDTO, error)
	GetRecentlyAdded(ctx context.Context, limit int) ([]*TrackDTO, error)
	Save(ctx context.Context, track *Track) error
	Delete(ctx context.Context, id string) error
	DeleteByPathPrefix(ctx context.Context, prefix string) error
	Upsert(ctx context.Context, track *Track) error
	GetAllArtworkKeys(ctx context.Context) ([]string, error)

	// Many-to-Many relationships
	SetArtists(ctx context.Context, trackID string, artistIDs []string) error
	SetAlbumArtists(ctx context.Context, trackID string, artistIDs []string) error
	SetGenres(ctx context.Context, trackID string, genreIDs []string) error
	SetComposers(ctx context.Context, trackID string, composerIDs []string) error
}

type AlbumRepository interface {
	GetByID(ctx context.Context, id string) (*AlbumDTO, error)
	GetByArtistID(ctx context.Context, artistID string) ([]*AlbumDTO, error)
	GetRecentlyAdded(ctx context.Context, limit int) ([]*AlbumDTO, error)
	GetByNormalizationKey(ctx context.Context, key string) (*Album, error)
	GetAll(ctx context.Context) ([]*AlbumDTO, error)
	Save(ctx context.Context, album *Album) error
	Upsert(ctx context.Context, album *Album) error
	// DeleteOrphaned removes albums with no tracks and returns their IDs.
	DeleteOrphaned(ctx context.Context) ([]string, error)

	// Many-to-Many relationships
	SetArtists(ctx context.Context, albumID string, artistIDs []string) error
}

type ArtistRepository interface {
	GetByID(ctx context.Context, id string) (*Artist, error)
	GetByNormalizationKey(ctx context.Context, key string) (*Artist, error)
	GetAll(ctx context.Context) ([]*Artist, error)
	Save(ctx context.Context, artist *Artist) error
	Upsert(ctx context.Context, artist *Artist) error
	// SetArtworkSource sets the cache key for a single artwork source. A nil key
	// clears that source only; other sources are left untouched.
	SetArtworkSource(ctx context.Context, id string, source string, key *string) error
	// GetAllArtworkKeys returns every non-empty artwork key across all sources,
	// used to keep live artist images out of the orphan-cleanup set.
	GetAllArtworkKeys(ctx context.Context) ([]string, error)
	// DeleteOrphaned removes artists referenced by no track/album and returns their IDs.
	DeleteOrphaned(ctx context.Context) ([]string, error)
}

type GenreRepository interface {
	GetByID(ctx context.Context, id string) (*Genre, error)
	GetByName(ctx context.Context, name string) (*Genre, error)
	GetByNormalizationKey(ctx context.Context, key string) (*Genre, error)
	GetAll(ctx context.Context) ([]*Genre, error)
	Save(ctx context.Context, genre *Genre) error
	Upsert(ctx context.Context, genre *Genre) error
	// DeleteOrphaned removes genres referenced by no track and returns their IDs.
	DeleteOrphaned(ctx context.Context) ([]string, error)
}

type ComposerRepository interface {
	GetByID(ctx context.Context, id string) (*Composer, error)
	GetByName(ctx context.Context, name string) (*Composer, error)
	GetByNormalizationKey(ctx context.Context, key string) (*Composer, error)
	GetAll(ctx context.Context) ([]*Composer, error)
	Save(ctx context.Context, composer *Composer) error
	Upsert(ctx context.Context, composer *Composer) error
	// DeleteOrphaned removes composers referenced by no track and returns their IDs.
	DeleteOrphaned(ctx context.Context) ([]string, error)
}

type PlaylistRepository interface {
	GetByID(ctx context.Context, id string) (*Playlist, error)
	GetAll(ctx context.Context) ([]*Playlist, error)
	Save(ctx context.Context, playlist *Playlist) error
	Update(ctx context.Context, playlist *Playlist) error
	Delete(ctx context.Context, id string) error
	AddTrack(ctx context.Context, playlistID, trackID string, position string) error
	AddTracks(ctx context.Context, playlistID string, trackIDs []string) error
	RemoveTrack(ctx context.Context, playlistID, trackID string) error
	UpdateTrackPosition(ctx context.Context, playlistID, trackID, position string) error
	UpdateTracksPositions(ctx context.Context, playlistID string, updates map[string]string) error
	GetTracks(ctx context.Context, playlistID string) ([]*TrackDTO, error)
	GetPlaylistsForTrack(ctx context.Context, trackID string) ([]string, error)
	GetTrackPosition(ctx context.Context, playlistID, trackID string) (string, error)
	GetMaxPosition(ctx context.Context, playlistID string) (string, error)
	CountTracks(ctx context.Context, playlistID string) (int, error)
}

type LyricRepository interface {
	GetByTrackID(ctx context.Context, trackID string) (*Lyric, error)
	Save(ctx context.Context, lyric *Lyric) error
	Upsert(ctx context.Context, lyric *Lyric) error
	Delete(ctx context.Context, trackID string) error
}

type LyricsProvider interface {
	Fetch(ctx context.Context, track *TrackDTO) (*Lyric, error)
	Search(ctx context.Context, title, artist string, duration int) ([]*LyricsSearchResult, error)
	Name() string
}

// LocalLyricsReader reads a sibling lyric file located next to the audio file.
// The lyric file must share the audio file's basename, differing only in extension.
// It tries "<base>.lrc" first, then "<base>.txt". found is false if neither exists.
// extraDirs are additional flat directories searched (in order) after the
// sibling dir, matched by the audio file's basename. The sibling dir keeps
// priority over extraDirs.
type LocalLyricsReader interface {
	Read(audioPath string, extraDirs ...string) (content, source string, found bool)
}

type EQRepository interface {
	GetActive(ctx context.Context) (*EQProfile, error)
	GetAll(ctx context.Context) ([]*EQProfile, error)
	GetByID(ctx context.Context, id string) (*EQProfile, error)
	Save(ctx context.Context, profile *EQProfile) error
	Delete(ctx context.Context, id string) error
	SetActive(ctx context.Context, id string) error
}

type WatchedFolderRepository interface {
	GetByID(ctx context.Context, id string) (*WatchedFolder, error)
	GetAll(ctx context.Context) ([]*WatchedFolder, error)
	Save(ctx context.Context, folder *WatchedFolder) error
	Delete(ctx context.Context, id string) error
}

type PlayerStateRepository interface {
	Save(ctx context.Context, state *PlayerState) error
	Load(ctx context.Context) (*PlayerState, error)
}

type SettingsRepository interface {
	Save(ctx context.Context, settings *AppSettings) error
	Load(ctx context.Context) (*AppSettings, error)
}

// AnalysisRepository persists the one-time audio analysis features and tracks
// which library entries still need analysis (tracks.analyzed_version).
type AnalysisRepository interface {
	// UpsertFeatures writes the features for a track and, in the same
	// transaction, bumps tracks.analyzed_version to f.AnalyzerVersion so the
	// track no longer counts as pending. Idempotent.
	UpsertFeatures(ctx context.Context, f *TrackFeatures) error
	// GetFeatures returns the stored features for a track, or (nil, nil) if none.
	GetFeatures(ctx context.Context, trackID string) (*TrackFeatures, error)
	// CountPending returns how many tracks have analyzed_version < currentVersion.
	CountPending(ctx context.Context, currentVersion int) (int, error)
	// ListPending returns up to limit track IDs with analyzed_version <
	// currentVersion, oldest-added first (stable backfill order).
	ListPending(ctx context.Context, currentVersion, limit int) ([]string, error)
	// MarkFailed bumps tracks.analyzed_version to currentVersion without
	// writing a track_features row, for a track whose analysis pass errored
	// (corrupt file, unsupported codec, etc). Without this, a single
	// permanently-failing track would count as pending forever — CountPending
	// never reaches 0, and analysis:progress never reports "done". No
	// features row means GetFeatures still returns nil for it, so
	// Normalization correctly treats it as unanalyzed (gain 0) rather than
	// inventing fake loudness data.
	MarkFailed(ctx context.Context, trackID string, currentVersion int) error
}

type MiniPlayerStateRepository interface {
	Save(ctx context.Context, state *MiniPlayerState) error
	Load(ctx context.Context) (*MiniPlayerState, error)
}

// LibrarySyncStateRepository persists a signature of the delimiter config that
// the library's split data currently reflects, so a sync can tell whether the
// delimiters changed since they were last applied.
type LibrarySyncStateRepository interface {
	GetDelimitersSignature(ctx context.Context) (string, error)
	SetDelimitersSignature(ctx context.Context, sig string) error
}
