package analytics

import "go.uber.org/fx"

var Module = fx.Module("analytics", fx.Provide(NewService))
