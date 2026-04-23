package lyrics

import "go.uber.org/fx"

var Module = fx.Module("lyrics",
	fx.Provide(NewLyricsService),
)
