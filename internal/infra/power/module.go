package power

import "go.uber.org/fx"

var Module = fx.Module("power", fx.Provide(NewInhibitor))
