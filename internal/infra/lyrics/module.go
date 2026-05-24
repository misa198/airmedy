package lyrics

import (
	"log/slog"

	"airmedy/internal/domain"

	"go.uber.org/fx"
)

var Module = fx.Module("lyrics-providers",
	fx.Provide(
		fx.Annotate(
			func(logger *slog.Logger) domain.LyricsProvider { return NewLrclibProvider(logger) },
			fx.ResultTags(`group:"lyrics_providers"`),
		),
		fx.Annotate(
			func(logger *slog.Logger) domain.LyricsProvider { return NewKugouProvider(logger) },
			fx.ResultTags(`group:"lyrics_providers"`),
		),
	),
)
