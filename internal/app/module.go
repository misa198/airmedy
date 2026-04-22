package app

import (
	"changeme/internal/app/config"
	"changeme/internal/app/library"
	"changeme/internal/domain"
	"changeme/internal/infra/artwork"
	"changeme/internal/infra/bleve"
	"changeme/internal/infra/logging"
	"changeme/internal/infra/metadata"
	"changeme/internal/infra/sqlite"
	"changeme/internal/infra/wails"
	"context"
	"log/slog"

	"go.uber.org/fx"
)

var Module = fx.Module("app",
	fx.Provide(
		config.NewConfig,
		func(c *config.Config, logger *slog.Logger) (*sqlite.DB, error) { return sqlite.NewDB(c.DBPath(), logger) },
		func(c *config.Config) (domain.SearchService, error) { return bleve.NewBleveSearchService(c.IndexPath()) },
		func(c *config.Config) (domain.ArtworkCache, error) { return artwork.NewDiskArtworkCache(c.ArtworkCachePath()) },
		func() domain.MetadataExtractor { return metadata.NewTagLibExtractor() },
		library.NewLibraryService,
		wails.NewLibraryService,
		func() *wails.GreetService { return &wails.GreetService{} },
	),
	sqlite.Module,
	logging.Module,
	fx.Invoke(func(lc fx.Lifecycle, db *sqlite.DB, search domain.SearchService, lib *library.LibraryService) {
		lc.Append(fx.Hook{
			OnStart: func(ctx context.Context) error {
				return lib.Start(ctx)
			},
			OnStop: func(ctx context.Context) error {
				if err := lib.Stop(ctx); err != nil {
					slog.Error("Failed to stop library service", "error", err)
				}
				if err := search.Close(); err != nil {
					return err
				}
				return db.Close()
			},
		})
	}),
)
