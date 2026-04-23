package eq

import "go.uber.org/fx"

var Module = fx.Module("eq",
	fx.Provide(NewEQService),
)
