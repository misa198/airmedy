package app

import (
	"airmedy/internal/app/analysis"
	"airmedy/internal/app/appsettings"
	"airmedy/internal/app/config"
	"airmedy/internal/app/eq"
	"airmedy/internal/app/i18n"
	"airmedy/internal/app/lastfm"
	"airmedy/internal/app/library"
	"airmedy/internal/app/lyrics"
	"airmedy/internal/app/normalization"
	"airmedy/internal/app/player"
	"airmedy/internal/app/playlist"
	"airmedy/internal/app/remoteserver"
	"airmedy/internal/app/updater"
	"airmedy/internal/domain"
	"airmedy/internal/infra/artwork"
	"airmedy/internal/infra/audio"
	"airmedy/internal/infra/bleve"
	"airmedy/internal/infra/logging"
	lyricsinfra "airmedy/internal/infra/lyrics"
	"airmedy/internal/infra/metadata"
	"airmedy/internal/infra/power"
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
		func(c *config.Config) (domain.ArtworkCache, error) {
			return artwork.NewDiskArtworkCache(c.ArtworkCachePath())
		},
		func() domain.MetadataExtractor { return metadata.NewTagLibExtractor() },
		func() domain.MetadataWriter { return metadata.NewTagLibWriter() },
		func() domain.LoudnessAnalyzer { return audio.NewLoudnessAnalyzer() },
		library.NewLibraryService,
		wails.NewLibraryService,
		wails.NewPlayerService,
		wails.NewSearchService,
		wails.NewPlaylistService,
		wails.NewLyricsService,
		wails.NewEQService,
		wails.NewNormalizationService,
		wails.NewAnalysisService,
		wails.NewLastFmService,
		wails.NewWindowService,
		wails.NewSettingsService,
		wails.NewRemoteServerService,
		wails.NewUpdaterService,
		wails.NewMoodRadioService,
		func(logger *slog.Logger) *updater.Service {
			return updater.NewService(config.Version, logger)
		},
		func() *wails.GreetService { return &wails.GreetService{} },
	),
	sqlite.Module,
	logging.Module,
	lyricsinfra.Module,
	player.Module,
	power.Module,
	playlist.Module,
	lyrics.Module,
	eq.Module,
	normalization.Module,
	lastfm.Module,
	appsettings.Module,
	remoteserver.Module,
	analysis.Module,
	fx.Invoke(func(lc fx.Lifecycle, db *sqlite.DB, search domain.SearchService, lib *library.LibraryService, playerSvc *player.PlayerService, eqSvc *eq.EQService, lastfmSvc *lastfm.LastFmService, analysisSvc *analysis.AnalysisService) {
		lc.Append(fx.Hook{
			OnStart: func(ctx context.Context) error {
				// Wire library to player to sync track metadata changes (e.g. favorites)
				lib.AddTrackUpdateListener(func(track *domain.TrackDTO) {
					playerSvc.SyncTrack(track)
				})

				// Last.fm love/unlove only on a genuine favorite toggle — not on
				// every track update. AddTrackUpdateListener also fires on import
				// and metadata edits, which would spam track.unlove for every
				// freshly imported non-favorite track.
				lib.AddFavoriteChangeListener(func(track *domain.TrackDTO) {
					lastfmSvc.SetLoveStatus(track, track.IsFavorite)
				})

				// Wire import -> analysis enqueue (low priority) and playback state ->
				// analysis throttle, both centrally here so neither package imports
				// the other (avoids a library<->analysis or player<->analysis cycle;
				// mirrors the track-update wiring above).
				lib.AddAnalysisListener(func(trackID string) {
					analysisSvc.Enqueue(trackID, false)
				})
				lib.AddSyncFinishedListener(func() {
					analysisSvc.TriggerPercentileRecompute()
				})
				lib.AddTrackDeletedListener(func(trackIDs []string) {
					analysisSvc.Dequeue(trackIDs)
					analysisSvc.NotifyTracksDeleted(trackIDs)
				})
				playerSvc.AddStatusListener(func(status domain.PlayerStatus) {
					analysisSvc.SetThrottled(status.PlaybackState == domain.PlaybackStatePlaying)
				})
				playerSvc.AddTrackLoadListener(func(track *domain.TrackDTO) {
					analysisSvc.Enqueue(track.ID, true) // on-play priority boost
				})

				if err := eqSvc.SeedDefaults(ctx); err != nil {
					slog.Error("Failed to seed EQ defaults", "error", err)
				}
				if err := eqSvc.ApplyActiveProfile(ctx); err != nil {
					slog.Error("Failed to apply active EQ profile", "error", err)
				}
				if err := analysisSvc.Start(ctx); err != nil {
					slog.Error("Failed to start analysis service", "error", err)
				}
				return lib.Start(ctx)
			},
			OnStop: func(ctx context.Context) error {
				if err := analysisSvc.Stop(ctx); err != nil {
					slog.Error("Failed to stop analysis service", "error", err)
				}
				if err := lib.Stop(ctx); err != nil {
					slog.Error("Failed to stop library service", "error", err)
				}
				return nil
			},
		})
	}),
)
