package pairing

import (
	"airmedy/internal/app/appsettings"

	"go.uber.org/fx"
)

var Module = fx.Module("pairing",
	fx.Provide(func(settings *appsettings.SettingsService) pairingSettings { return settings }),
	fx.Provide(NewService),
	fx.Invoke(func(lc fx.Lifecycle, svc *Service) {
		lc.Append(fx.Hook{OnStart: svc.OnStart, OnStop: svc.OnStop})
	}),
)
