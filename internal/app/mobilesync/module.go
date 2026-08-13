package mobilesync

import "go.uber.org/fx"

var Module = fx.Module("mobile-library-sync",
	fx.Provide(NewService),
	fx.Invoke(func(lc fx.Lifecycle, svc *Service) { lc.Append(fx.Hook{OnStart: svc.OnStart, OnStop: svc.OnStop}) }),
)
