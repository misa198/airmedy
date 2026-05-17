package lyrics

import "go.uber.org/fx"

var Module = fx.Module("lyrics",
	fx.Provide(
		fx.Annotate(
			NewLyricsService,
			fx.ParamTags(``, ``, `group:"lyrics_providers"`),
		),
	),
)
