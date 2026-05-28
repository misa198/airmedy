package wails

import (
	"context"
	"net"

	"airmedy/internal/app/remoteserver"
)

// RemoteServerService exposes remote server controls to the desktop frontend.
type RemoteServerService struct {
	svc *remoteserver.Service
}

func NewRemoteServerService(svc *remoteserver.Service) *RemoteServerService {
	return &RemoteServerService{svc: svc}
}

// RemoteServerStatus is returned to the frontend.
type RemoteServerStatus struct {
	Enabled  bool     `json:"enabled"`
	Running  bool     `json:"running"`
	Port     int      `json:"port"`
	Password string   `json:"password"`
	LocalIPs []string `json:"local_ips"`
}

func (s *RemoteServerService) GetStatus(ctx context.Context) RemoteServerStatus {
	settings, _ := s.svc.GetSettings(ctx)
	return RemoteServerStatus{
		Enabled:  settings != nil && settings.RemoteServerEnabled,
		Running:  s.svc.IsRunning(),
		Port:     s.svc.GetPort(),
		Password: func() string {
			if settings != nil {
				return settings.RemoteServerPassword
			}
			return ""
		}(),
		LocalIPs: getLocalIPs(),
	}
}

func (s *RemoteServerService) SetEnabled(ctx context.Context, enabled bool) error {
	return s.svc.SetEnabled(ctx, enabled)
}

func (s *RemoteServerService) RegeneratePassword(ctx context.Context) (string, error) {
	return s.svc.RegeneratePassword(ctx)
}

func (s *RemoteServerService) SetPassword(ctx context.Context, password string) error {
	return s.svc.SetPassword(ctx, password)
}

// getLocalIPs returns all non-loopback IPv4 addresses.
func getLocalIPs() []string {
	var ips []string
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return ips
	}
	for _, addr := range addrs {
		ipNet, ok := addr.(*net.IPNet)
		if !ok {
			continue
		}
		ip := ipNet.IP
		if ip.IsLoopback() || ip.To4() == nil {
			continue
		}
		ips = append(ips, ip.String())
	}
	return ips
}
