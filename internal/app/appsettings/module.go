package appsettings

import "go.uber.org/fx"

var Module = fx.Module("appsettings",
	fx.Provide(
		NewSettingsService,
	),
)
