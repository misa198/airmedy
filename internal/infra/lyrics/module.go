package lyrics

import (
	"airmedy/internal/domain"

	"go.uber.org/fx"
)

var Module = fx.Module("lyrics-providers",
	fx.Provide(
		fx.Annotate(
			func() domain.LyricsProvider { return NewLrclibProvider() },
			fx.ResultTags(`group:"lyrics_providers"`),
		),
		fx.Annotate(
			func() domain.LyricsProvider { return NewKugouProvider() },
			fx.ResultTags(`group:"lyrics_providers"`),
		),
	),
)
