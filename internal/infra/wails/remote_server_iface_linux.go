//go:build linux

package wails

import (
	"fmt"
	"net"
	"os"
)

// buildIfaceKindMap uses sysfs to detect true interface types on Linux.
// /sys/class/net/<iface>/wireless/ exists → wifi
// /sys/class/net/<iface>/type contains the ARPHRD type code → 772 = loopback, 1 = ether, etc.
func buildIfaceKindMap() map[string]string {
	result := map[string]string{}
	ifaces, err := net.Interfaces()
	if err != nil {
		return result
	}
	for _, iface := range ifaces {
		result[iface.Name] = linuxIfaceKind(iface.Name)
	}
	return result
}

func linuxIfaceKind(name string) string {
	// Wireless: wireless/ subdir exists in sysfs
	if _, err := os.Stat(fmt.Sprintf("/sys/class/net/%s/wireless", name)); err == nil {
		return "wifi"
	}
	// Virtual bridges / tun devices
	if _, err := os.Stat(fmt.Sprintf("/sys/class/net/%s/bridge", name)); err == nil {
		return "virtual"
	}
	return "ethernet"
}
