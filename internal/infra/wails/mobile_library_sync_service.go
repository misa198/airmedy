package wails

import (
	"context"
	"fmt"

	"airmedy/internal/app/mobilesync"
	"airmedy/internal/domain"
	"github.com/wailsapp/wails/v3/pkg/application"
)

// MobileLibrarySyncService is the desktop IPC boundary for sending a selected
// library snapshot to an already paired mobile device.
type MobileLibrarySyncService struct{ svc *mobilesync.Service }

func NewMobileLibrarySyncService(svc *mobilesync.Service) *MobileLibrarySyncService {
	adapter := &MobileLibrarySyncService{svc: svc}
	svc.AddListener(func(plan *domain.MobileLibrarySyncPlan) {
		if app := application.Get(); app != nil {
			app.Event.Emit("mobile-library-sync:updated", plan)
		}
	})
	return adapter
}

func (s *MobileLibrarySyncService) GetStatus(ctx context.Context, deviceID string) (*domain.MobileLibrarySyncPlan, error) {
	return s.svc.GetStatus(ctx, deviceID)
}

func (s *MobileLibrarySyncService) Cancel(ctx context.Context, deviceID string) (*domain.MobileLibrarySyncPlan, error) {
	plan, err := s.svc.Cancel(ctx, deviceID)
	if err != nil {
		return nil, fmt.Errorf("cancel mobile library sync: %w", err)
	}
	return plan, nil
}

// Sync creates a new plan after a completed sync, resumes an unchanged active
// plan, or replaces an active plan only when replace was confirmed by the UI.
func (s *MobileLibrarySyncService) Sync(ctx context.Context, deviceID string, scope domain.MobileLibrarySyncScope, host string, replace bool) (*domain.MobileLibrarySyncPlan, error) {
	plan, err := s.svc.Start(ctx, deviceID, scope, host, replace)
	if err != nil {
		return nil, fmt.Errorf("start mobile library sync: %w", err)
	}
	return plan, nil
}
