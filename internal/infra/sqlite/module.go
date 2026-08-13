package sqlite

import (
	"airmedy/internal/domain"

	"go.uber.org/fx"
)

var Module = fx.Module("sqlite",
	fx.Provide(
		func(db *DB) domain.TrackRepository { return NewTrackRepository(db) },
		func(db *DB, tracks domain.TrackRepository) domain.TrackQueryRepository {
			return NewTrackQueryRepository(db, tracks)
		},
		func(db *DB) domain.AlbumRepository { return NewAlbumRepository(db) },
		func(db *DB) domain.ArtistRepository { return NewArtistRepository(db) },
		func(db *DB) domain.GenreRepository { return NewGenreRepository(db) },
		func(db *DB) domain.ComposerRepository { return NewComposerRepository(db) },
		func(db *DB) domain.PlaylistRepository { return NewPlaylistRepository(db) },
		func(db *DB) domain.LyricRepository { return NewLyricRepository(db) },
		func(db *DB) domain.MobileSyncLyricCacheRepository { return NewMobileSyncLyricCacheRepository(db) },
		func(db *DB) domain.WatchedFolderRepository { return NewWatchedFolderRepository(db) },
		func(db *DB) domain.EQRepository { return NewEQRepository(db) },
		func(db *DB) domain.PlayerStateRepository { return NewPlayerStateRepository(db) },
		func(db *DB) domain.SettingsRepository { return NewSettingsRepository(db) },
		func(db *DB) domain.AnalysisRepository { return NewAnalysisRepository(db) },
		func(db *DB) domain.MiniPlayerStateRepository { return NewMiniPlayerStateRepository(db) },
		func(db *DB) domain.LibrarySyncStateRepository { return NewLibrarySyncStateRepository(db) },
		func(db *DB) domain.ListeningRepository { return NewListeningRepository(db) },
		func(db *DB) domain.PairingIdentityRepository { return NewPairingIdentityRepository(db) },
		func(db *DB) domain.TrustedMobileDeviceRepository { return NewTrustedMobileDeviceRepository(db) },
		func(db *DB) domain.MobileLibrarySyncPlanRepository { return NewMobileLibrarySyncPlanRepository(db) },
		func(db *DB) domain.PlaylistMutationLedger { return NewPlaylistMutationLedger(db) },
		func(db *DB) domain.PlaylistMutationLWW { return NewPlaylistMutationLWW(db) },
		func(db *DB) domain.PlaylistArtworkStagingRepository { return NewPlaylistArtworkStagingRepository(db) },
		func(db *DB) domain.TxManager { return NewTxManager(db) },
	),
)
