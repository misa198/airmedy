package lastfm

import (
	"context"
	"log/slog"

	"go.uber.org/fx"
)

var Module = fx.Module("lastfm",
	fx.Provide(
		NewLastFmService,
	),
	fx.Invoke(func(lc fx.Lifecycle, s *LastFmService, logger *slog.Logger) {
		lc.Append(fx.Hook{
			OnStart: func(ctx context.Context) error {
				logger.Info("Last.fm service started")
				return nil
			},
		})
	}),
)
