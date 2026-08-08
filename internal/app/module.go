package app

import (
	"airmedy/internal/app/analysis"
	"airmedy/internal/app/analytics"
	"airmedy/internal/app/appsettings"
	"airmedy/internal/app/config"
	"airmedy/internal/app/eq"
	"airmedy/internal/app/i18n"
	"airmedy/internal/app/lastfm"
	"airmedy/internal/app/library"
	"airmedy/internal/app/lyrics"
	"airmedy/internal/app/mobilesync"
	"airmedy/internal/app/moodradio"
	"airmedy/internal/app/normalization"
	"airmedy/internal/app/pairing"
	"airmedy/internal/app/player"
	"airmedy/internal/app/playlist"
	"airmedy/internal/app/remoteserver"
	"airmedy/internal/app/updater"
	"airmedy/internal/domain"
	"airmedy/internal/infra/artwork"
	"airmedy/internal/infra/audio"
	"airmedy/internal/infra/bleve"
	keyringinfra "airmedy/internal/infra/keyring"
	"airmedy/internal/infra/logging"
	lyricsinfra "airmedy/internal/infra/lyrics"
	mdnsinfra "airmedy/internal/infra/mdns"
	"airmedy/internal/infra/metadata"
	mqttinfra "airmedy/internal/infra/mqtt"
	"airmedy/internal/infra/notification"
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
		audio.NewPlayer,
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
		wails.NewAnalyticsService,
		wails.NewLastFmService,
		wails.NewWindowService,
		wails.NewSettingsService,
		wails.NewRemoteServerService,
		wails.NewMobilePairingService,
		wails.NewMobileLibrarySyncService,
		wails.NewUpdaterService,
		wails.NewMoodRadioService,
		func(logger *slog.Logger) *updater.Service {
			return updater.NewService(config.Version, logger)
		},
		func() *wails.GreetService { return &wails.GreetService{} },
		func() domain.PairingKeyStore { return keyringinfra.NewPairingKeyStore() },
		func(logger *slog.Logger) domain.PairingBroker { return mqttinfra.NewPairingBroker(logger) },
		func(logger *slog.Logger) domain.PairingAdvertiser { return mdnsinfra.NewPairingAdvertiser(logger) },
	),
	sqlite.Module,
	logging.Module,
	lyricsinfra.Module,
	player.Module,
	power.Module,
	notification.Module,
	playlist.Module,
	lyrics.Module,
	eq.Module,
	normalization.Module,
	moodradio.Module,
	lastfm.Module,
	appsettings.Module,
	remoteserver.Module,
	pairing.Module,
	mobilesync.Module,
	analysis.Module,
	analytics.Module,
	fx.Invoke(func(lc fx.Lifecycle, db *sqlite.DB, search domain.SearchService, lib *library.LibraryService, playerSvc *player.PlayerService, eqSvc *eq.EQService, lastfmSvc *lastfm.LastFmService, analysisSvc *analysis.AnalysisService, settingsSvc *appsettings.SettingsService, playlistSvc *playlist.PlaylistService) {
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

				// Reschedule the library's periodic sync when the interval
				// setting changes (ignore saves that don't touch it, so an
				// unrelated settings edit doesn't force an immediate rescan).
				lastSyncInterval := lib.CurrentSyncInterval()
				settingsSvc.AddChangeListener(func(settings *domain.AppSettings) {
					if settings.LibrarySyncInterval != lastSyncInterval {
						lastSyncInterval = settings.LibrarySyncInterval
						lib.RescheduleSync()
					}
				})

				// Apply the queue size cap live so a lowered limit trims the
				// running queue immediately (in addition to the startup load
				// in PlayerService.restoreState).
				settingsSvc.AddChangeListener(func(settings *domain.AppSettings) {
					playerSvc.SetMaxQueueSize(domain.ResolveMaxQueueSize(settings.MaxQueueSize))
				})

				// Apply crossfade duration changes live (initial value is loaded
				// in PlayerService.restoreState).
				settingsSvc.AddChangeListener(func(settings *domain.AppSettings) {
					playerSvc.SetCrossfadeSeconds(domain.ClampCrossfadeSeconds(settings.CrossfadeSeconds))
				})

				// Apply macOS automatic-track notification preference live.
				settingsSvc.AddChangeListener(func(settings *domain.AppSettings) {
					playerSvc.SetAutoAdvanceNotificationsEnabled(settings.AutoAdvanceNotificationsEnabled)
				})

				if err := playlistSvc.EnsureFavoritesPlaylist(ctx); err != nil {
					slog.Error("Failed to ensure favorites playlist", "error", err)
				}
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
