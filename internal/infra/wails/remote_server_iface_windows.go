//go:build windows

package wails

import (
	"unsafe"

	"golang.org/x/sys/windows"
)

// Windows IANA interface type constants (RFC 3635 / IANAifType).
const (
	ifTypeEthernetCSMACD = 6   // wired Ethernet
	ifTypeIEEE80211      = 71  // Wi-Fi (802.11)
	ifTypePPP            = 23  // PPP (dial-up / some VPNs)
	ifTypeTunnel         = 131 // generic tunnel (VPN)
	ifTypeSoftwareLoop   = 24  // loopback
)

// buildIfaceKindMap queries GetAdaptersAddresses to get the true interface type
// for each adapter. The map key is the friendly name (matches net.Interface.Name on Windows).
func buildIfaceKindMap() map[string]string {
	result := map[string]string{}

	// First call: get required buffer size.
	var size uint32
	_ = windows.GetAdaptersAddresses(windows.AF_UNSPEC, 0, 0, nil, &size)
	if size == 0 {
		return result
	}

	buf := make([]byte, size)
	addr := (*windows.IpAdapterAddresses)(unsafe.Pointer(&buf[0]))
	if err := windows.GetAdaptersAddresses(windows.AF_UNSPEC, 0, 0, addr, &size); err != nil {
		return result
	}

	for cur := addr; cur != nil; cur = cur.Next {
		name := windows.UTF16PtrToString(cur.FriendlyName)
		result[name] = windowsIfTypeToKind(cur.IfType)
	}
	return result
}

func windowsIfTypeToKind(ifType uint32) string {
	switch ifType {
	case ifTypeIEEE80211:
		return "wifi"
	case ifTypeEthernetCSMACD:
		return "ethernet"
	case ifTypePPP, ifTypeTunnel:
		return "vpn"
	default:
		return "virtual"
	}
}
