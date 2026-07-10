package moodradio

import "go.uber.org/fx"

var Module = fx.Module("moodradio", fx.Provide(NewService))
