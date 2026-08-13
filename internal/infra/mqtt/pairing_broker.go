package mqtt

import (
	"context"
	"fmt"
	"log/slog"
	"net"
	"strconv"
	"strings"
	"sync"

	"airmedy/internal/domain"

	"github.com/google/uuid"
	mqtt "github.com/mochi-mqtt/server/v2"
	"github.com/mochi-mqtt/server/v2/listeners"
	"github.com/mochi-mqtt/server/v2/packets"
)

const maxPairingPayload = 16 * 1024

// PairingBroker embeds a deliberately narrow MQTT broker. It exposes only the
// v1 pairing request and response topics; player control is not transportable here.
type PairingBroker struct {
	logger    *slog.Logger
	mu        sync.RWMutex
	server    *mqtt.Server
	port      int
	desktopID string
}

func NewPairingBroker(logger *slog.Logger) *PairingBroker { return &PairingBroker{logger: logger} }

func (b *PairingBroker) Start(_ context.Context, desktopID string, preferredPort int, onRequest func([]byte), onDeviceConnection func(deviceID string, connected bool)) (int, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.server != nil {
		return b.port, nil
	}

	server := mqtt.New(&mqtt.Options{InlineClient: true, Logger: b.logger})
	if err := server.AddHook(&pairingACLHook{desktopID: desktopID, onDeviceConnection: onDeviceConnection}, nil); err != nil {
		return 0, fmt.Errorf("add pairing MQTT ACL: %w", err)
	}
	address := "0.0.0.0:0"
	if preferredPort != 0 {
		address = net.JoinHostPort("0.0.0.0", strconv.Itoa(preferredPort))
	}
	tcp := listeners.NewTCP(listeners.Config{ID: "mobile-pairing", Address: address})
	if err := server.AddListener(tcp); err != nil {
		return 0, fmt.Errorf("bind pairing MQTT listener: %w", err)
	}
	requestTopic := pairingRequestTopic(desktopID)
	if err := server.Subscribe(requestTopic, 1, func(_ *mqtt.Client, _ packets.Subscription, pk packets.Packet) {
		if len(pk.Payload) > maxPairingPayload {
			return
		}
		payload := append([]byte(nil), pk.Payload...)
		go onRequest(payload)
	}); err != nil {
		_ = server.Close()
		return 0, fmt.Errorf("subscribe pairing request topic: %w", err)
	}

	go func() {
		if err := server.Serve(); err != nil {
			b.logger.Error("mobile pairing MQTT broker stopped unexpectedly", "error", err)
		}
	}()

	boundAddress := tcp.Address()
	_, portText, err := net.SplitHostPort(boundAddress)
	port, parseErr := strconv.Atoi(portText)
	if err != nil || parseErr != nil || port == 0 {
		_ = server.Close()
		return 0, fmt.Errorf("resolve pairing MQTT port from %q", boundAddress)
	}
	b.server = server
	b.port = port
	b.desktopID = desktopID
	b.logger.Info("mobile pairing MQTT broker started", "port", port)
	return port, nil
}

func (b *PairingBroker) Stop(_ context.Context) error {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.server == nil {
		return nil
	}
	err := b.server.Close()
	b.server = nil
	b.port = 0
	b.desktopID = ""
	return err
}

// Disconnect forcibly closes the live sync session for a revoked mobile device.
func (b *PairingBroker) Disconnect(_ context.Context, deviceID string) error {
	b.mu.RLock()
	server, desktopID := b.server, b.desktopID
	b.mu.RUnlock()
	if server == nil || desktopID == "" {
		return nil
	}
	client, ok := server.Clients.Get("airmedy-sync-" + desktopID + "-" + deviceID)
	if !ok {
		return nil
	}
	if err := server.DisconnectClient(client, packets.ErrNotAuthorized); err != nil {
		return fmt.Errorf("disconnect mobile sync session: %w", err)
	}
	return nil
}

func (b *PairingBroker) Publish(_ context.Context, topic string, payload []byte) error {
	b.mu.RLock()
	server := b.server
	b.mu.RUnlock()
	if server == nil {
		return fmt.Errorf("pairing MQTT broker is not running")
	}
	if err := server.Publish(topic, payload, false, 1); err != nil {
		return fmt.Errorf("publish pairing MQTT response: %w", err)
	}
	return nil
}

func (b *PairingBroker) Subscribe(_ context.Context, topic string, handler func([]byte)) error {
	b.mu.RLock()
	server := b.server
	b.mu.RUnlock()
	if server == nil {
		return fmt.Errorf("pairing MQTT broker is not running")
	}
	if err := server.Subscribe(topic, 1, func(_ *mqtt.Client, _ packets.Subscription, pk packets.Packet) {
		if len(pk.Payload) > maxPairingPayload || handler == nil {
			return
		}
		payload := append([]byte(nil), pk.Payload...)
		go handler(payload)
	}); err != nil {
		return fmt.Errorf("subscribe pairing MQTT topic: %w", err)
	}
	return nil
}

func (b *PairingBroker) Running() bool {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return b.server != nil
}

type pairingACLHook struct {
	mqtt.HookBase
	desktopID          string
	onDeviceConnection func(deviceID string, connected bool)
}

func (h *pairingACLHook) ID() string { return "airmedy-mobile-pairing-acl" }

func (h *pairingACLHook) Provides(b byte) bool {
	return b == mqtt.OnConnectAuthenticate || b == mqtt.OnACLCheck || b == mqtt.OnSessionEstablished || b == mqtt.OnDisconnect
}

func (h *pairingACLHook) OnConnectAuthenticate(_ *mqtt.Client, _ packets.Packet) bool { return true }

func (h *pairingACLHook) OnSessionEstablished(client *mqtt.Client, _ packets.Packet) {
	if deviceID, ok := syncClientDeviceID(client.ID, h.desktopID); ok && h.onDeviceConnection != nil {
		h.onDeviceConnection(deviceID, true)
	}
}

func (h *pairingACLHook) OnDisconnect(client *mqtt.Client, _ error, _ bool) {
	if deviceID, ok := syncClientDeviceID(client.ID, h.desktopID); ok && h.onDeviceConnection != nil {
		h.onDeviceConnection(deviceID, false)
	}
}

func (h *pairingACLHook) OnACLCheck(client *mqtt.Client, topic string, write bool) bool {
	if client != nil {
		if deviceID, ok := syncClientDeviceID(client.ID, h.desktopID); ok {
			base := "airmedy/library-sync/v1/" + h.desktopID + "/" + deviceID + "/"
			playlistBase := "airmedy/playlist-sync/v1/" + h.desktopID + "/" + deviceID + "/"
			if write {
				return topic == base+"receipt" || topic == playlistBase+"result"
			}
			return topic == base+"request" || topic == playlistBase+"request"
		}
	}
	if write {
		return topic == pairingRequestTopic(h.desktopID)
	}
	prefix := pairingResponsePrefix(h.desktopID)
	if !strings.HasPrefix(topic, prefix) {
		return false
	}
	id := strings.TrimPrefix(topic, prefix)
	return id != "" && !strings.ContainsAny(id, "/+#")
}

func pairingRequestTopic(desktopID string) string {
	return "airmedy/pairing/v1/" + desktopID + "/request"
}

func pairingResponsePrefix(desktopID string) string {
	return "airmedy/pairing/v1/" + desktopID + "/response/"
}

func syncClientDeviceID(clientID, desktopID string) (string, bool) {
	prefix := "airmedy-sync-" + desktopID + "-"
	if !strings.HasPrefix(clientID, prefix) {
		return "", false
	}
	deviceID := strings.TrimPrefix(clientID, prefix)
	if _, err := uuid.Parse(deviceID); err != nil {
		return "", false
	}
	return deviceID, true
}

var _ domain.PairingBroker = (*PairingBroker)(nil)
