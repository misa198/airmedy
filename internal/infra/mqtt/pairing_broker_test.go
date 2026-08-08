package mqtt

import (
	"context"
	"fmt"
	"log/slog"
	"net"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

func TestPairingBrokerBindsEphemeralPort(t *testing.T) {
	broker := NewPairingBroker(slog.Default())
	port, err := broker.Start(context.Background(), "00000000-0000-0000-0000-000000000001", 0, func([]byte) {}, nil)
	require.NoError(t, err)
	require.NotZero(t, port)
	t.Cleanup(func() { require.NoError(t, broker.Stop(context.Background())) })

	conn, err := net.DialTimeout("tcp", net.JoinHostPort("127.0.0.1", fmt.Sprint(port)), time.Second)
	require.NoError(t, err)
	require.NoError(t, conn.Close())
}

func TestPairingBrokerBindsPreferredPort(t *testing.T) {
	reservation, err := net.Listen("tcp", "127.0.0.1:0")
	require.NoError(t, err)
	port := reservation.Addr().(*net.TCPAddr).Port
	require.NoError(t, reservation.Close())

	broker := NewPairingBroker(slog.Default())
	boundPort, err := broker.Start(context.Background(), "00000000-0000-0000-0000-000000000001", port, func([]byte) {}, nil)
	require.NoError(t, err)
	require.Equal(t, port, boundPort)
	t.Cleanup(func() { require.NoError(t, broker.Stop(context.Background())) })
}

func TestPairingACLOnlyAllowsPairingTopics(t *testing.T) {
	hook := &pairingACLHook{desktopID: "desktop-1"}
	require.True(t, hook.OnACLCheck(nil, pairingRequestTopic("desktop-1"), true))
	require.False(t, hook.OnACLCheck(nil, "airmedy/player/v1/play", true))
	require.True(t, hook.OnACLCheck(nil, pairingResponsePrefix("desktop-1")+"mobile-1", false))
	require.False(t, hook.OnACLCheck(nil, pairingResponsePrefix("desktop-1")+"#", false))
	require.False(t, hook.OnACLCheck(nil, pairingResponsePrefix("other")+"mobile-1", false))
}

func TestSyncClientDeviceID(t *testing.T) {
	desktopID := "00000000-0000-0000-0000-000000000001"
	deviceID := "00000000-0000-0000-0000-000000000002"
	got, ok := syncClientDeviceID("airmedy-sync-"+desktopID+"-"+deviceID, desktopID)
	require.True(t, ok)
	require.Equal(t, deviceID, got)
	_, ok = syncClientDeviceID("airmedy-sync-"+desktopID+"-not-a-uuid", desktopID)
	require.False(t, ok)
}
