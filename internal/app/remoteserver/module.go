package remoteserver

import (
	"context"
	"io/fs"

	"go.uber.org/fx"
)

// RemoteFS is a named wrapper so FX can distinguish it from other fs.FS values.
type RemoteFS struct {
	fs.FS
}

var Module = fx.Module("remoteserver",
	fx.Provide(NewService),
	fx.Invoke(func(lc fx.Lifecycle, svc *Service) {
		lc.Append(fx.Hook{
			OnStart: func(ctx context.Context) error {
				return svc.OnStart(ctx)
			},
			OnStop: func(ctx context.Context) error {
				return svc.OnStop(ctx)
			},
		})
	}),
)
