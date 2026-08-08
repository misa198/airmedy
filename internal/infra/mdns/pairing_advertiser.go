// Package mdns provides LAN discovery adapters.
package mdns

import (
	"context"
	"fmt"
	"log/slog"
	"net"

	"github.com/grandcat/zeroconf"
)

const pairingServiceType = "_airmedy-pair._tcp"

// PairingAdvertiser publishes the local MQTT pairing endpoint through DNS-SD.
// It intentionally advertises only endpoint information requested by the
// desktop discovery contract; authentication remains the pairing protocol's job.
type PairingAdvertiser struct{ logger *slog.Logger }

func NewPairingAdvertiser(logger *slog.Logger) *PairingAdvertiser {
	return &PairingAdvertiser{logger: logger}
}

func (a *PairingAdvertiser) Advertise(_ context.Context, deviceID string, port int) (func(), error) {
	if deviceID == "" || port < 1 || port > 65535 {
		return nil, fmt.Errorf("invalid pairing advertisement")
	}
	ip, ifaces := primaryIPv4AndInterfaces()
	if ip == "" || len(ifaces) == 0 {
		return nil, fmt.Errorf("no reachable IPv4 network interface")
	}
	server, err := zeroconf.Register("Airmedy-"+deviceID, pairingServiceType, "local.", port, pairingTXT(ip, port, deviceID), ifaces)
	if err != nil {
		return nil, err
	}
	a.logger.Info("pairing mDNS broadcast started", "device_id", deviceID, "port", port, "ip", ip)
	return func() {
		server.Shutdown()
		a.logger.Info("pairing mDNS broadcast stopped", "device_id", deviceID)
	}, nil
}

func pairingTXT(ip string, port int, deviceID string) []string {
	return []string{"ip=" + ip, fmt.Sprintf("port=%d", port), "device_id=" + deviceID}
}

func primaryIPv4AndInterfaces() (string, []net.Interface) {
	interfaces, err := net.Interfaces()
	if err != nil {
		return "", nil
	}
	var primary string
	usable := make([]net.Interface, 0, len(interfaces))
	for _, iface := range interfaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addresses, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, address := range addresses {
			ipNet, ok := address.(*net.IPNet)
			if !ok || ipNet.IP.IsLoopback() || ipNet.IP.To4() == nil {
				continue
			}
			if primary == "" {
				primary = ipNet.IP.String()
			}
			usable = append(usable, iface)
			break
		}
	}
	return primary, usable
}
