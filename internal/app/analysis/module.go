package analysis

import "go.uber.org/fx"

var Module = fx.Module("analysis",
	fx.Provide(NewAnalysisService),
)
