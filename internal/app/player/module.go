package player

import "go.uber.org/fx"

// Module provides the player-related services to the application.
var Module = fx.Module("player",
	fx.Provide(
		NewQueueService,
		NewPlayerService,
	),
)
