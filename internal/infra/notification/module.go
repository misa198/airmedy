package notification

import "go.uber.org/fx"

// Module provides the platform-specific automatic track-transition notifier.
var Module = fx.Module("notification", fx.Provide(NewTrackTransitionNotifier))
