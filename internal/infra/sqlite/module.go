package sqlite

import (
	"airmedy/internal/domain"

	"go.uber.org/fx"
)

var Module = fx.Module("sqlite",
	fx.Provide(
		func(db *DB) domain.TrackRepository { return NewTrackRepository(db) },
		func(db *DB) domain.AlbumRepository { return NewAlbumRepository(db) },
		func(db *DB) domain.ArtistRepository { return NewArtistRepository(db) },
		func(db *DB) domain.GenreRepository { return NewGenreRepository(db) },
		func(db *DB) domain.ComposerRepository { return NewComposerRepository(db) },
		func(db *DB) domain.PlaylistRepository { return NewPlaylistRepository(db) },
		func(db *DB) domain.LyricRepository { return NewLyricRepository(db) },
		func(db *DB) domain.WatchedFolderRepository { return NewWatchedFolderRepository(db) },
		func(db *DB) domain.EQRepository { return NewEQRepository(db) },
		func(db *DB) domain.PlayerStateRepository { return NewPlayerStateRepository(db) },
		func(db *DB) domain.SettingsRepository { return NewSettingsRepository(db) },
	),
)
