package notification

import (
	"airmedy/internal/domain"

	"go.uber.org/fx"
)

// Module provides the platform-specific automatic track-transition notifier.
var Module = fx.Module("notification", fx.Provide(
	NewTrackTransitionNotifier,
	NewTrackTransitionNotificationActivator,
))

func NewTrackTransitionNotificationActivator(notifier domain.TrackTransitionNotifier) domain.TrackTransitionNotificationActivator {
	return notifier.(domain.TrackTransitionNotificationActivator)
}
