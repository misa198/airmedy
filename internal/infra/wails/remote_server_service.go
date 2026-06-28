package wails

import (
	"context"
	"net"
	"sort"
	"strings"

	"airmedy/internal/app/remoteserver"
)

// RemoteServerService exposes remote server controls to the desktop frontend.
type RemoteServerService struct {
	svc *remoteserver.Service
}

func NewRemoteServerService(svc *remoteserver.Service) *RemoteServerService {
	return &RemoteServerService{svc: svc}
}

// LocalAddress is a single reachable address with its network interface info.
type LocalAddress struct {
	IP    string `json:"ip"`
	Iface string `json:"iface"`
	// Kind classifies the interface: "wifi", "ethernet", "vpn", "virtual" or "link_local".
	Kind string `json:"kind"`
}

// RemoteServerStatus is returned to the frontend.
type RemoteServerStatus struct {
	Enabled   bool           `json:"enabled"`
	Running   bool           `json:"running"`
	Port      int            `json:"port"`
	Password  string         `json:"password"`
	Addresses []LocalAddress `json:"addresses"`
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
		Addresses: getLocalAddresses(),
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

// kindRank orders address kinds so the most likely-reachable ones come first.
// The frontend uses the first entry as the primary (QR) URL.
var kindRank = map[string]int{
	"ethernet":   0,
	"wifi":       1,
	"vpn":        2,
	"virtual":    3,
	"link_local": 4,
}

// getLocalAddresses returns all non-loopback IPv4 addresses, each annotated with
// the network interface it belongs to and a classified kind, sorted so that
// real LAN interfaces (ethernet/wifi) come first and noisy ones (virtual,
// link-local) last.
func getLocalAddresses() []LocalAddress {
	var out []LocalAddress
	ifaces, err := net.Interfaces()
	if err != nil {
		return out
	}
	hints := buildIfaceKindMap()
	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
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
			out = append(out, LocalAddress{
				IP:    ip.String(),
				Iface: iface.Name,
				Kind:  classifyInterface(iface, ip, hints),
			})
		}
	}
	sort.SliceStable(out, func(i, j int) bool {
		return kindRank[out[i].Kind] < kindRank[out[j].Kind]
	})
	return out
}

// classifyInterface maps a network interface + IPv4 address to a coarse kind
// usable for picking an icon/label in the UI. hints (from buildIfaceKindMap)
// takes priority; name- and flag-based heuristics are the fallback.
func classifyInterface(iface net.Interface, ip net.IP, hints map[string]string) string {
	if ip4 := ip.To4(); ip4 != nil && ip4[0] == 169 && ip4[1] == 254 {
		return "link_local"
	}

	name := strings.ToLower(iface.Name)

	// VPN / tunnels: point-to-point flag is the strongest signal.
	if iface.Flags&net.FlagPointToPoint != 0 ||
		hasAnyPrefix(name, "utun", "tun", "tap", "ppp", "wg", "ipsec", "tailscale", "zt") {
		return "vpn"
	}

	// Virtual bridges from Docker / VMs / containers.
	if hasAnyPrefix(name, "docker", "br-", "bridge", "veth", "virbr",
		"vmnet", "vmenet", "vboxnet", "vnic", "hyper-v", "vethernet") {
		return "virtual"
	}

	// Platform-supplied hint (e.g. from networksetup on macOS) wins over name guessing.
	if kind, ok := hints[iface.Name]; ok {
		return kind
	}

	// Wi-Fi. Reliable on Linux (wl*) and Windows ("Wi-Fi").
	if hasAnyPrefix(name, "wl", "wlan", "wlp", "wifi", "wi-fi") {
		return "wifi"
	}

	// Everything else private/up is treated as wired LAN.
	return "ethernet"
}

func hasAnyPrefix(s string, prefixes ...string) bool {
	for _, p := range prefixes {
		if strings.HasPrefix(s, p) {
			return true
		}
	}
	return false
}
