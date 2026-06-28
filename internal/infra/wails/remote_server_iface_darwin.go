//go:build darwin

package wails

import (
	"bufio"
	"bytes"
	"os/exec"
	"strings"
)

// buildIfaceKindMap shells out to networksetup to learn the true hardware type
// of each interface (Wi-Fi vs Ethernet vs Thunderbolt Bridge, etc.).
// Returns a map of interface name → kind string ("wifi" | "ethernet" | "virtual").
// Returns an empty map on any error so callers fall back to name-based heuristics.
func buildIfaceKindMap() map[string]string {
	out, err := exec.Command("networksetup", "-listallhardwareports").Output()
	if err != nil {
		return map[string]string{}
	}
	return parseNetworksetupOutput(out)
}

func parseNetworksetupOutput(out []byte) map[string]string {
	result := map[string]string{}
	scanner := bufio.NewScanner(bytes.NewReader(out))

	var currentPort string
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if rest, ok := strings.CutPrefix(line, "Hardware Port:"); ok {
			currentPort = strings.TrimSpace(rest)
		} else if rest, ok := strings.CutPrefix(line, "Device:"); ok {
			device := strings.TrimSpace(rest)
			if device != "" && currentPort != "" {
				result[device] = hardwarePortToKind(currentPort)
			}
			currentPort = ""
		}
	}
	return result
}

func hardwarePortToKind(port string) string {
	lower := strings.ToLower(port)
	if strings.Contains(lower, "wi-fi") || strings.Contains(lower, "wifi") || strings.Contains(lower, "airport") {
		return "wifi"
	}
	if strings.Contains(lower, "thunderbolt bridge") || strings.Contains(lower, "bridge") {
		return "virtual"
	}
	// Thunderbolt, USB, Ethernet Adapter, Ethernet → all treated as ethernet
	return "ethernet"
}
