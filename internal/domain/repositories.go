package domain

import "context"

type TrackRepository interface {
	GetByID(ctx context.Context, id string) (*Track, error)
	GetByPath(ctx context.Context, path string) (*Track, error)
	GetAll(ctx context.Context) ([]*Track, error)
	Save(ctx context.Context, track *Track) error
	Delete(ctx context.Context, id string) error
	Upsert(ctx context.Context, track *Track) error
}

type AlbumRepository interface {
	GetByID(ctx context.Context, id string) (*Album, error)
	GetAll(ctx context.Context) ([]*Album, error)
	Save(ctx context.Context, album *Album) error
	Upsert(ctx context.Context, album *Album) error
}

type ArtistRepository interface {
	GetByID(ctx context.Context, id string) (*Artist, error)
	GetAll(ctx context.Context) ([]*Artist, error)
	Save(ctx context.Context, artist *Artist) error
	Upsert(ctx context.Context, artist *Artist) error
}

type GenreRepository interface {
	GetByID(ctx context.Context, id string) (*Genre, error)
	GetByName(ctx context.Context, name string) (*Genre, error)
	GetAll(ctx context.Context) ([]*Genre, error)
	Save(ctx context.Context, genre *Genre) error
	Upsert(ctx context.Context, genre *Genre) error
}

type ComposerRepository interface {
	GetByID(ctx context.Context, id string) (*Composer, error)
	GetByName(ctx context.Context, name string) (*Composer, error)
	GetAll(ctx context.Context) ([]*Composer, error)
	Save(ctx context.Context, composer *Composer) error
	Upsert(ctx context.Context, composer *Composer) error
}

type PlaylistRepository interface {
	GetByID(ctx context.Context, id string) (*Playlist, error)
	GetAll(ctx context.Context) ([]*Playlist, error)
	Save(ctx context.Context, playlist *Playlist) error
	Delete(ctx context.Context, id string) error
	AddTrack(ctx context.Context, playlistID, trackID string, position int) error
	RemoveTrack(ctx context.Context, playlistID, trackID string) error
	GetTracks(ctx context.Context, playlistID string) ([]*Track, error)
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
