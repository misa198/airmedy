package domain

import "context"

type TrackRepository interface {
	GetByID(ctx context.Context, id string) (*TrackDTO, error)
	GetByPath(ctx context.Context, path string) (*TrackDTO, error)
	GetByPathPrefix(ctx context.Context, prefix string) ([]*TrackDTO, error)
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
	DeleteOrphaned(ctx context.Context) error

	// Many-to-Many relationships
	SetArtists(ctx context.Context, albumID string, artistIDs []string) error
}

type ArtistRepository interface {
	GetByID(ctx context.Context, id string) (*Artist, error)
	GetByNormalizationKey(ctx context.Context, key string) (*Artist, error)
	GetAll(ctx context.Context) ([]*Artist, error)
	Save(ctx context.Context, artist *Artist) error
	Upsert(ctx context.Context, artist *Artist) error
	DeleteOrphaned(ctx context.Context) error
}

type GenreRepository interface {
	GetByID(ctx context.Context, id string) (*Genre, error)
	GetByName(ctx context.Context, name string) (*Genre, error)
	GetByNormalizationKey(ctx context.Context, key string) (*Genre, error)
	GetAll(ctx context.Context) ([]*Genre, error)
	Save(ctx context.Context, genre *Genre) error
	Upsert(ctx context.Context, genre *Genre) error
	DeleteOrphaned(ctx context.Context) error
}

type ComposerRepository interface {
	GetByID(ctx context.Context, id string) (*Composer, error)
	GetByName(ctx context.Context, name string) (*Composer, error)
	GetByNormalizationKey(ctx context.Context, key string) (*Composer, error)
	GetAll(ctx context.Context) ([]*Composer, error)
	Save(ctx context.Context, composer *Composer) error
	Upsert(ctx context.Context, composer *Composer) error
	DeleteOrphaned(ctx context.Context) error
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
