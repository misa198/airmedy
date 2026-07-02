package normalization

import "go.uber.org/fx"

var Module = fx.Module("normalization",
	fx.Provide(NewNormalizationService),
)
