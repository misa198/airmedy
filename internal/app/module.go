package app

import (
	"airmedy/internal/app/appsettings"
	"airmedy/internal/app/config"
	"airmedy/internal/app/eq"
	"airmedy/internal/app/i18n"
	"airmedy/internal/app/lastfm"
	"airmedy/internal/app/library"
	"airmedy/internal/app/lyrics"
	"airmedy/internal/app/player"
	"airmedy/internal/app/playlist"
	"airmedy/internal/app/updater"
	"airmedy/internal/domain"
	"airmedy/internal/infra/artwork"
	"airmedy/internal/infra/bleve"
	lyricsinfra "airmedy/internal/infra/lyrics"
	"airmedy/internal/infra/logging"
	"airmedy/internal/infra/metadata"
	"airmedy/internal/infra/sqlite"
	"airmedy/internal/infra/wails"
	"context"
	"log/slog"

	"go.uber.org/fx"
)

var Module = fx.Module("app",
	fx.Provide(
		config.NewConfig,
		i18n.NewService,
		func(lc fx.Lifecycle, c *config.Config, logger *slog.Logger) (*sqlite.DB, error) {
			db, err := sqlite.NewDB(c.DBPath(), logger)
			if err != nil {
				return nil, err
			}
			lc.Append(fx.Hook{
				OnStop: func(ctx context.Context) error {
					return db.Close()
				},
			})
			return db, nil
		},
		func(lc fx.Lifecycle, c *config.Config) (domain.SearchService, error) {
			search, err := bleve.NewBleveSearchService(c.IndexPath())
			if err != nil {
				return nil, err
			}
			lc.Append(fx.Hook{
				OnStop: func(ctx context.Context) error {
					return search.Close()
				},
			})
			return search, nil
		},
		func(c *config.Config) (domain.ArtworkCache, error) { return artwork.NewDiskArtworkCache(c.ArtworkCachePath()) },
		func() domain.MetadataExtractor { return metadata.NewTagLibExtractor() },
		func() domain.MetadataWriter { return metadata.NewTagLibWriter() },
		library.NewLibraryService,
		wails.NewLibraryService,
		wails.NewPlayerService,
		wails.NewSearchService,
		wails.NewPlaylistService,
		wails.NewLyricsService,
		wails.NewEQService,
		wails.NewLastFmService,
		wails.NewWindowService,
		wails.NewSettingsService,
		wails.NewUpdaterService,
		func(logger *slog.Logger) *updater.Service {
			return updater.NewService(config.Version, logger)
		},
		func() *wails.GreetService { return &wails.GreetService{} },
	),
	sqlite.Module,
	logging.Module,
	lyricsinfra.Module,
	player.Module,
	playlist.Module,
	lyrics.Module,
	eq.Module,
	lastfm.Module,
	appsettings.Module,
	fx.Invoke(func(lc fx.Lifecycle, db *sqlite.DB, search domain.SearchService, lib *library.LibraryService, playerSvc *player.PlayerService, eqSvc *eq.EQService, lastfmSvc *lastfm.LastFmService) {
		lc.Append(fx.Hook{
			OnStart: func(ctx context.Context) error {
				// Wire library to player to sync track metadata changes (e.g. favorites)
				lib.AddTrackUpdateListener(func(track *domain.TrackDTO) {
					playerSvc.SyncTrack(track)
					lastfmSvc.SetLoveStatus(track, track.IsFavorite)
				})


				if err := eqSvc.SeedDefaults(ctx); err != nil {
					slog.Error("Failed to seed EQ defaults", "error", err)
				}
				if err := eqSvc.ApplyActiveProfile(ctx); err != nil {
					slog.Error("Failed to apply active EQ profile", "error", err)
				}
				return lib.Start(ctx)
			},
			OnStop: func(ctx context.Context) error {
				if err := lib.Stop(ctx); err != nil {
					slog.Error("Failed to stop library service", "error", err)
				}
				return nil
			},
		})
	}),
)
