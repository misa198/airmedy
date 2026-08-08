package mdns

import (
	"testing"

	"github.com/stretchr/testify/require"
)

func TestPairingTXTContainsOnlyDiscoveryEndpointFields(t *testing.T) {
	require.Equal(t, []string{
		"ip=192.168.1.10",
		"port=45678",
		"device_id=8f4d850a-c795-4e0e-8a2a-a33a2096bf28",
	}, pairingTXT("192.168.1.10", 45678, "8f4d850a-c795-4e0e-8a2a-a33a2096bf28"))
}
