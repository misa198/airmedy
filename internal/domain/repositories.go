package domain

import "context"

type TrackRepository interface {
	GetByID(ctx context.Context, id string) (*TrackDTO, error)
	GetByPath(ctx context.Context, path string) (*TrackDTO, error)
	GetByPathPrefix(ctx context.Context, prefix string) ([]*TrackDTO, error)
	GetAll(ctx context.Context) ([]*TrackDTO, error)
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
	Delete(ctx context.Context, id string) error
	AddTrack(ctx context.Context, playlistID, trackID string, position int) error
	RemoveTrack(ctx context.Context, playlistID, trackID string) error
	GetTracks(ctx context.Context, playlistID string) ([]*TrackDTO, error)
}

type LyricRepository interface {
	GetByTrackID(ctx context.Context, trackID string) (*Lyric, error)
	Save(ctx context.Context, lyric *Lyric) error
	Upsert(ctx context.Context, lyric *Lyric) error
	Delete(ctx context.Context, trackID string) error
}

type WatchedFolderRepository interface {
	GetByID(ctx context.Context, id string) (*WatchedFolder, error)
	GetAll(ctx context.Context) ([]*WatchedFolder, error)
	Save(ctx context.Context, folder *WatchedFolder) error
	Delete(ctx context.Context, id string) error
}
