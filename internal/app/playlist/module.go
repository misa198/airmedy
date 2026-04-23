package playlist

import "go.uber.org/fx"

var Module = fx.Module("playlist",
	fx.Provide(NewPlaylistService),
)
