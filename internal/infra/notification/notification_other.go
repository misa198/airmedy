//go:build !darwin

package notification

import "airmedy/internal/domain"

type noopTrackTransitionNotifier struct{}

func NewTrackTransitionNotifier() domain.TrackTransitionNotifier {
	return noopTrackTransitionNotifier{}
}

func (noopTrackTransitionNotifier) NotifyTrackAdvanced(_, _, _ string) {}
