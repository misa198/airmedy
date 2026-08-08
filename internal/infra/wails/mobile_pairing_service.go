package wails

import (
	"context"
	"time"

	"airmedy/internal/app/pairing"
	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
)

// MobilePairingService is a thin desktop IPC adapter for mobile pairing state and actions.
type MobilePairingService struct{ svc *pairing.Service }

func NewMobilePairingService(svc *pairing.Service) *MobilePairingService {
	adapter := &MobilePairingService{svc: svc}
	svc.AddRequestListener(func(request pairing.PendingRequest) {
		if app := application.Get(); app != nil {
			app.Event.Emit("pairing:request", request)
		}
	})
	svc.AddDeviceConnectionListener(emitTrustedDevicesChanged)
	svc.AddBroadcastListener(emitPairingBroadcastChanged)
	return adapter
}

type MobilePairingStatus struct {
	Running        bool           `json:"running"`
	Port           int            `json:"port"`
	DeviceID       string         `json:"device_id"`
	DesktopName    string         `json:"desktop_name"`
	PublicKey      string         `json:"public_key"`
	Error          string         `json:"error"`
	Addresses      []LocalAddress `json:"addresses"`
	Broadcasting   bool           `json:"broadcasting"`
	BroadcastUntil string         `json:"broadcasting_until"`
}

func (s *MobilePairingService) GetStatus() MobilePairingStatus {
	status := s.svc.GetStatus()
	return MobilePairingStatus{Running: status.Running, Port: status.Port, DeviceID: status.DeviceID, DesktopName: status.DesktopName, PublicKey: status.PublicKey, Error: status.Error, Addresses: getLocalAddresses(), Broadcasting: status.Broadcasting, BroadcastUntil: status.BroadcastUntil.UTC().Format(time.RFC3339Nano)}
}

func (s *MobilePairingService) Retry(ctx context.Context) error { return s.svc.Retry(ctx) }

func (s *MobilePairingService) StartBroadcast(ctx context.Context) error {
	return s.svc.StartBroadcast(ctx)
}

func (s *MobilePairingService) StopBroadcast() { s.svc.StopBroadcast() }

func (s *MobilePairingService) GetTrustedDevices(ctx context.Context) ([]*domain.TrustedMobileDevice, error) {
	return s.svc.ListTrustedDevices(ctx)
}

func (s *MobilePairingService) Respond(ctx context.Context, requestID string, accepted bool) error {
	if err := s.svc.Respond(ctx, requestID, accepted); err != nil {
		return err
	}
	if accepted {
		emitTrustedDevicesChanged()
	}
	return nil
}

func (s *MobilePairingService) RevokeDevice(ctx context.Context, deviceID string) error {
	if err := s.svc.RevokeDevice(ctx, deviceID); err != nil {
		return err
	}
	emitTrustedDevicesChanged()
	return nil
}

func emitTrustedDevicesChanged() {
	if app := application.Get(); app != nil {
		app.Event.Emit("pairing:trusted-devices-changed", nil)
	}
}

func emitPairingBroadcastChanged() {
	if app := application.Get(); app != nil {
		app.Event.Emit("pairing:broadcast-changed", nil)
	}
}
