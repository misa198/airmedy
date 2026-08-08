package pairing

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log/slog"
	"os"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

	"airmedy/internal/domain"

	"github.com/google/uuid"
)

const (
	pendingTTL = 2 * time.Minute
	seenTTL    = 10 * time.Minute
)

// Status is safe to expose through the Wails adapter; it contains no private material.
type Status struct {
	Running     bool   `json:"running"`
	Port        int    `json:"port"`
	DeviceID    string `json:"device_id"`
	DesktopName string `json:"desktop_name"`
	PublicKey   string `json:"public_key"`
	Error       string `json:"error"`
}

// PendingRequest is emitted only after a complete, verified request from an untrusted key.
type PendingRequest struct {
	RequestID   string `json:"request_id"`
	MobileID    string `json:"mobile_id"`
	DisplayName string `json:"display_name"`
	Platform    string `json:"platform"`
	Fingerprint string `json:"fingerprint"`
}

type pendingRequest struct {
	request handshakeRequest
	expires time.Time
}

type Service struct {
	identityRepo domain.PairingIdentityRepository
	devices      domain.TrustedMobileDeviceRepository
	keys         domain.PairingKeyStore
	broker       domain.PairingBroker
	settings     pairingSettings
	logger       *slog.Logger
	now          func() time.Time

	mu                        sync.Mutex
	identity                  *domain.PairingIdentity
	private                   ed25519.PrivateKey
	port                      int
	lastError                 string
	pending                   map[string]pendingRequest
	seen                      map[string]time.Time
	listeners                 []func(PendingRequest)
	deviceConnectionListeners []func()
	online                    map[string]bool
	cancel                    context.CancelFunc
}

type pairingSettings interface {
	GetSettings(context.Context) (*domain.AppSettings, error)
	SaveSettings(context.Context, *domain.AppSettings) error
}

func NewService(identityRepo domain.PairingIdentityRepository, devices domain.TrustedMobileDeviceRepository, keys domain.PairingKeyStore, broker domain.PairingBroker, settings pairingSettings, logger *slog.Logger) *Service {
	return &Service{identityRepo: identityRepo, devices: devices, keys: keys, broker: broker, settings: settings, logger: logger, now: time.Now, pending: make(map[string]pendingRequest), seen: make(map[string]time.Time), online: make(map[string]bool)}
}

func (s *Service) OnStart(ctx context.Context) error {
	if err := s.start(ctx); err != nil {
		s.mu.Lock()
		s.lastError = err.Error()
		s.mu.Unlock()
		s.logger.Error("mobile pairing is unavailable", "error", err)
	}
	return nil // Pairing must never prevent the desktop player from starting.
}

func (s *Service) OnStop(ctx context.Context) error {
	s.mu.Lock()
	if s.cancel != nil {
		s.cancel()
		s.cancel = nil
	}
	s.mu.Unlock()
	return s.broker.Stop(ctx)
}

func (s *Service) Retry(ctx context.Context) error {
	if s.broker.Running() {
		return nil
	}
	if err := s.start(ctx); err != nil {
		return err
	}
	return nil
}

func (s *Service) start(ctx context.Context) error {
	s.mu.Lock()
	if s.broker.Running() {
		s.mu.Unlock()
		return nil
	}
	s.mu.Unlock()
	identity, private, err := s.loadIdentity(ctx)
	if err != nil {
		return err
	}
	settings, err := s.settings.GetSettings(ctx)
	if err != nil {
		return fmt.Errorf("load pairing MQTT settings: %w", err)
	}
	port, err := s.broker.Start(ctx, identity.DeviceID, settings.PairingMQTTPort, s.handlePayload, s.setDeviceConnection)
	if err != nil && settings.PairingMQTTPort != 0 {
		s.logger.Warn("cached pairing MQTT port is unavailable; selecting a new port", "port", settings.PairingMQTTPort, "error", err)
		port, err = s.broker.Start(ctx, identity.DeviceID, 0, s.handlePayload, s.setDeviceConnection)
	}
	if err != nil {
		return err
	}
	if port != settings.PairingMQTTPort {
		settings.PairingMQTTPort = port
		if err := s.settings.SaveSettings(ctx, settings); err != nil {
			s.logger.Warn("failed to cache pairing MQTT port", "port", port, "error", err)
		}
	}
	loopCtx, cancel := context.WithCancel(context.Background())
	s.mu.Lock()
	s.identity, s.private, s.port, s.lastError, s.cancel = identity, private, port, "", cancel
	s.mu.Unlock()
	go s.expiryLoop(loopCtx)
	return nil
}

func (s *Service) loadIdentity(ctx context.Context) (*domain.PairingIdentity, ed25519.PrivateKey, error) {
	private, found, err := s.keys.Load(ctx)
	if err != nil {
		return nil, nil, err
	}
	if !found {
		_, private, err = ed25519.GenerateKey(rand.Reader)
		if err != nil {
			return nil, nil, fmt.Errorf("generate desktop pairing key: %w", err)
		}
		if err := s.keys.Save(ctx, private); err != nil {
			return nil, nil, err
		}
	}
	public := private.Public().(ed25519.PublicKey)
	identity, err := s.identityRepo.Load(ctx)
	if err != nil {
		return nil, nil, err
	}
	if identity == nil {
		identity = &domain.PairingIdentity{DeviceID: uuid.NewString(), PublicKey: append([]byte(nil), public...)}
		if err := s.identityRepo.Save(ctx, identity); err != nil {
			return nil, nil, err
		}
	} else if !ed25519.PublicKey(identity.PublicKey).Equal(public) {
		return nil, nil, fmt.Errorf("pairing identity public key does not match OS keyring")
	}
	return identity, private, nil
}

func (s *Service) AddRequestListener(listener func(PendingRequest)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.listeners = append(s.listeners, listener)
}

func (s *Service) AddDeviceConnectionListener(listener func()) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.deviceConnectionListeners = append(s.deviceConnectionListeners, listener)
}

func (s *Service) setDeviceConnection(deviceID string, connected bool) {
	s.mu.Lock()
	if s.online[deviceID] == connected {
		s.mu.Unlock()
		return
	}
	s.online[deviceID] = connected
	listeners := append([]func(){}, s.deviceConnectionListeners...)
	s.mu.Unlock()
	for _, listener := range listeners {
		listener()
	}
}

func (s *Service) GetStatus() Status {
	s.mu.Lock()
	defer s.mu.Unlock()
	status := Status{Running: s.broker.Running(), Port: s.port, DesktopName: desktopDisplayName(), Error: s.lastError}
	if s.identity != nil {
		status.DeviceID = s.identity.DeviceID
		status.PublicKey = base64.RawURLEncoding.EncodeToString(s.identity.PublicKey)
	}
	return status
}

func desktopDisplayName() string {
	name, err := os.Hostname()
	if err != nil {
		return "Airmedy Desktop"
	}
	return normalizeDesktopName(name)
}

func normalizeDesktopName(name string) string {
	if !validDisplayValue(name, 1, 64) {
		return "Airmedy Desktop"
	}
	return strings.TrimSpace(name)
}

func (s *Service) ListTrustedDevices(ctx context.Context) ([]*domain.TrustedMobileDevice, error) {
	devices, err := s.devices.List(ctx)
	if err != nil {
		return nil, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	for _, device := range devices {
		device.Online = s.online[device.DeviceID]
	}
	return devices, nil
}

func (s *Service) RevokeDevice(ctx context.Context, deviceID string) error {
	if _, err := uuid.Parse(deviceID); err != nil {
		return fmt.Errorf("invalid paired device ID")
	}
	return s.devices.Delete(ctx, deviceID)
}

func (s *Service) Respond(ctx context.Context, requestID string, accepted bool) error {
	s.mu.Lock()
	s.cleanExpiredLocked(ctx)
	pending, ok := s.pending[requestID]
	if ok {
		delete(s.pending, requestID)
		s.seen[requestID] = s.now().Add(seenTTL)
	}
	s.mu.Unlock()
	if !ok {
		return fmt.Errorf("pairing request is no longer pending")
	}
	if accepted {
		publicKey, _ := decodeRaw(pending.request.MobilePublicKey, ed25519.PublicKeySize, "mobile_public_key")
		now := s.now().UTC()
		device := &domain.TrustedMobileDevice{DeviceID: pending.request.MobileID, PublicKey: publicKey, DisplayName: pending.request.MobileName, Platform: pending.request.MobilePlatform, PairedAt: now, LastSeenAt: now}
		if err := s.devices.Save(ctx, device); err != nil {
			return err
		}
	}
	decision := "rejected"
	if accepted {
		decision = "approved"
	}
	return s.publishResponse(ctx, pending.request, decision)
}

func (s *Service) handlePayload(payload []byte) {
	var request handshakeRequest
	if err := json.Unmarshal(payload, &request); err != nil {
		s.logger.Warn("ignored malformed mobile pairing request", "error", err)
		return
	}
	if err := s.validateRequest(request); err != nil {
		s.logger.Warn("ignored invalid mobile pairing request", "error", err)
		return
	}

	ctx := context.Background()
	s.mu.Lock()
	s.cleanExpiredLocked(ctx)
	_, seen := s.seen[request.RequestID]
	_, pendingExists := s.pending[request.RequestID]
	s.mu.Unlock()
	if seen || pendingExists {
		return
	}
	trusted, err := s.devices.GetByDeviceID(ctx, request.MobileID)
	if err != nil {
		s.logger.Error("load paired device", "error", err)
		return
	}
	if trusted != nil && ed25519.PublicKey(trusted.PublicKey).Equal(ed25519.PublicKey(mustKey(request.MobilePublicKey))) {
		if err := s.devices.Touch(ctx, request.MobileID, s.now().UTC()); err != nil {
			s.logger.Warn("touch paired device", "error", err)
		}
		s.markSeen(request.RequestID)
		if err := s.publishResponse(ctx, request, "approved"); err != nil {
			s.logger.Warn("publish automatic pairing response", "error", err)
		}
		return
	}

	s.mu.Lock()
	s.cleanExpiredLocked(ctx)
	if _, duplicate := s.seen[request.RequestID]; duplicate {
		s.mu.Unlock()
		return
	}
	if _, duplicate := s.pending[request.RequestID]; duplicate {
		s.mu.Unlock()
		return
	}
	s.pending[request.RequestID] = pendingRequest{request: request, expires: s.now().Add(pendingTTL)}
	listeners := append([]func(PendingRequest){}, s.listeners...)
	s.mu.Unlock()
	pending := PendingRequest{RequestID: request.RequestID, MobileID: request.MobileID, DisplayName: request.MobileName, Platform: request.MobilePlatform, Fingerprint: fingerprint(mustKey(request.MobilePublicKey))}
	for _, listener := range listeners {
		listener(pending)
	}
}

func (s *Service) validateRequest(request handshakeRequest) error {
	if request.Version != protocolVersion || request.Type != requestType {
		return fmt.Errorf("unsupported pairing protocol")
	}
	if _, err := uuid.Parse(request.RequestID); err != nil {
		return fmt.Errorf("invalid request ID")
	}
	if _, err := uuid.Parse(request.MobileID); err != nil {
		return fmt.Errorf("invalid mobile ID")
	}
	if !validDisplayValue(request.MobileName, 1, 64) || !validDisplayValue(request.MobilePlatform, 1, 32) {
		return fmt.Errorf("invalid mobile metadata")
	}
	s.mu.Lock()
	identity := s.identity
	s.mu.Unlock()
	if identity == nil || request.DesktopID != identity.DeviceID || request.DesktopPublicKey != base64.RawURLEncoding.EncodeToString(identity.PublicKey) {
		return fmt.Errorf("request is not for this desktop")
	}
	if delta := s.now().UnixMilli() - request.IssuedAt; delta > int64((5*time.Minute).Milliseconds()) || delta < -int64((5*time.Minute).Milliseconds()) {
		return fmt.Errorf("request timestamp outside allowed window")
	}
	input, err := requestSigningInput(request)
	if err != nil {
		return err
	}
	publicKey, err := decodeRaw(request.MobilePublicKey, ed25519.PublicKeySize, "mobile_public_key")
	if err != nil {
		return err
	}
	signature, err := decodeRaw(request.Signature, ed25519.SignatureSize, "signature")
	if err != nil || !ed25519.Verify(publicKey, input, signature) {
		return fmt.Errorf("invalid request signature")
	}
	return nil
}

func (s *Service) publishResponse(ctx context.Context, request handshakeRequest, decision string) error {
	s.mu.Lock()
	identity, private := s.identity, append(ed25519.PrivateKey(nil), s.private...)
	s.mu.Unlock()
	if identity == nil || len(private) != ed25519.PrivateKeySize {
		return fmt.Errorf("desktop pairing identity is unavailable")
	}
	nonce := make([]byte, 32)
	if _, err := rand.Read(nonce); err != nil {
		return fmt.Errorf("generate pairing response nonce: %w", err)
	}
	response := handshakeResponse{Version: protocolVersion, Type: responseType, RequestID: request.RequestID, Decision: decision, DesktopID: identity.DeviceID, DesktopNonce: base64.RawURLEncoding.EncodeToString(nonce), IssuedAt: s.now().UnixMilli()}
	input, err := responseSigningInput(response, request, identity.PublicKey)
	if err != nil {
		return err
	}
	response.Signature = base64.RawURLEncoding.EncodeToString(ed25519.Sign(private, input))
	payload, err := json.Marshal(response)
	if err != nil {
		return fmt.Errorf("marshal pairing response: %w", err)
	}
	topic := "airmedy/pairing/v1/" + identity.DeviceID + "/response/" + request.MobileID
	return s.broker.Publish(ctx, topic, payload)
}

func (s *Service) expiryLoop(ctx context.Context) {
	ticker := time.NewTicker(15 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			s.mu.Lock()
			s.cleanExpiredLocked(context.Background())
			s.mu.Unlock()
		}
	}
}

func (s *Service) cleanExpiredLocked(ctx context.Context) {
	now := s.now()
	for requestID, pending := range s.pending {
		if now.Before(pending.expires) {
			continue
		}
		delete(s.pending, requestID)
		s.seen[requestID] = now.Add(seenTTL)
		go func(request handshakeRequest) { _ = s.publishResponse(ctx, request, "expired") }(pending.request)
	}
	for requestID, expires := range s.seen {
		if !now.Before(expires) {
			delete(s.seen, requestID)
		}
	}
}

func (s *Service) markSeen(requestID string) {
	s.mu.Lock()
	s.seen[requestID] = s.now().Add(seenTTL)
	s.mu.Unlock()
}

func mustKey(encoded string) []byte {
	key, _ := decodeRaw(encoded, ed25519.PublicKeySize, "mobile_public_key")
	return key
}

func fingerprint(publicKey []byte) string {
	sum := sha256.Sum256(publicKey)
	hex := fmt.Sprintf("%X", sum[:])
	return strings.Join([]string{hex[0:4], hex[4:8], hex[8:12], hex[12:16], hex[16:20], hex[20:24]}, "-")
}

func validDisplayValue(value string, min, max int) bool {
	value = strings.TrimSpace(value)
	if !utf8.ValidString(value) || len(value) < min || len(value) > max {
		return false
	}
	return !strings.ContainsAny(value, "\x00\r\n")
}
