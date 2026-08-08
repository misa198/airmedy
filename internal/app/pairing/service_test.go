package pairing

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"strings"
	"sync"
	"testing"
	"time"

	"airmedy/internal/domain"

	"github.com/google/uuid"
	"github.com/stretchr/testify/require"
)

type memoryIdentityRepo struct{ identity *domain.PairingIdentity }

func (r *memoryIdentityRepo) Load(context.Context) (*domain.PairingIdentity, error) {
	return r.identity, nil
}
func (r *memoryIdentityRepo) Save(_ context.Context, identity *domain.PairingIdentity) error {
	r.identity = identity
	return nil
}

type memoryDeviceRepo struct {
	devices map[string]*domain.TrustedMobileDevice
}

func (r *memoryDeviceRepo) List(context.Context) ([]*domain.TrustedMobileDevice, error) {
	devices := make([]*domain.TrustedMobileDevice, 0, len(r.devices))
	for _, device := range r.devices {
		devices = append(devices, device)
	}
	return devices, nil
}
func (r *memoryDeviceRepo) GetByDeviceID(_ context.Context, id string) (*domain.TrustedMobileDevice, error) {
	return r.devices[id], nil
}
func (r *memoryDeviceRepo) Save(_ context.Context, d *domain.TrustedMobileDevice) error {
	r.devices[d.DeviceID] = d
	return nil
}
func (r *memoryDeviceRepo) Touch(_ context.Context, id string, at time.Time) error {
	r.devices[id].LastSeenAt = at
	return nil
}
func (r *memoryDeviceRepo) Delete(_ context.Context, id string) error {
	delete(r.devices, id)
	return nil
}

type memoryKeyStore struct{ key ed25519.PrivateKey }

func (s *memoryKeyStore) Load(context.Context) (ed25519.PrivateKey, bool, error) {
	return s.key, s.key != nil, nil
}
func (s *memoryKeyStore) Save(_ context.Context, key ed25519.PrivateKey) error {
	s.key = key
	return nil
}

type memoryPairingSettings struct {
	settings *domain.AppSettings
	saves    int
}

func (s *memoryPairingSettings) GetSettings(context.Context) (*domain.AppSettings, error) {
	return s.settings, nil
}

func (s *memoryPairingSettings) SaveSettings(_ context.Context, settings *domain.AppSettings) error {
	s.settings = settings
	s.saves++
	return nil
}

type testBroker struct {
	mu           sync.Mutex
	handler      func([]byte)
	published    [][]byte
	ports        []int
	failures     map[int]error
	disconnected []string
}

func (b *testBroker) Start(_ context.Context, _ string, preferredPort int, handler func([]byte), _ func(string, bool)) (int, error) {
	b.ports = append(b.ports, preferredPort)
	if err := b.failures[preferredPort]; err != nil {
		return 0, err
	}
	b.handler = handler
	return 54321, nil
}
func (b *testBroker) Stop(context.Context) error { return nil }
func (b *testBroker) Disconnect(_ context.Context, deviceID string) error {
	b.disconnected = append(b.disconnected, deviceID)
	return nil
}
func (b *testBroker) Publish(_ context.Context, _ string, payload []byte) error {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.published = append(b.published, payload)
	return nil
}
func (b *testBroker) Running() bool { return b.handler != nil }

type testAdvertiser struct {
	calls []struct {
		deviceID string
		port     int
	}
	stops int
	err   error
}

func (a *testAdvertiser) Advertise(_ context.Context, deviceID string, port int) (func(), error) {
	if a.err != nil {
		return nil, a.err
	}
	a.calls = append(a.calls, struct {
		deviceID string
		port     int
	}{deviceID, port})
	return func() { a.stops++ }, nil
}

func requireLogger(_ *testing.T) *slog.Logger { return slog.New(slog.NewTextHandler(io.Discard, nil)) }

func signedRequest(t *testing.T, identity *domain.PairingIdentity, private ed25519.PrivateKey) handshakeRequest {
	t.Helper()
	public := private.Public().(ed25519.PublicKey)
	nonce := make([]byte, 32)
	_, err := rand.Read(nonce)
	require.NoError(t, err)
	req := handshakeRequest{Version: protocolVersion, Type: requestType, RequestID: uuid.NewString(), DesktopID: identity.DeviceID, DesktopPublicKey: base64.RawURLEncoding.EncodeToString(identity.PublicKey), MobileID: uuid.NewString(), MobileName: "Test phone", MobilePlatform: "android", MobilePublicKey: base64.RawURLEncoding.EncodeToString(public), Nonce: base64.RawURLEncoding.EncodeToString(nonce), IssuedAt: time.Now().UnixMilli()}
	input, err := requestSigningInput(req)
	require.NoError(t, err)
	req.Signature = base64.RawURLEncoding.EncodeToString(ed25519.Sign(private, input))
	return req
}

func TestAcceptRequestPersistsTrustedDeviceAndSignsResponse(t *testing.T) {
	identityRepo := &memoryIdentityRepo{}
	devices := &memoryDeviceRepo{devices: map[string]*domain.TrustedMobileDevice{}}
	keys := &memoryKeyStore{}
	broker := &testBroker{}
	svc := NewService(identityRepo, devices, keys, broker, nil, &memoryPairingSettings{settings: &domain.AppSettings{}}, nil)
	svc.logger = requireLogger(t)
	require.NoError(t, svc.start(context.Background()))
	_, mobilePrivate, err := ed25519.GenerateKey(rand.Reader)
	require.NoError(t, err)
	request := signedRequest(t, identityRepo.identity, mobilePrivate)
	payload, err := json.Marshal(request)
	require.NoError(t, err)
	broker.handler(payload)
	require.NoError(t, svc.Respond(context.Background(), request.RequestID, true))
	require.Contains(t, devices.devices, request.MobileID)
	require.Len(t, broker.published, 1)
	var response handshakeResponse
	require.NoError(t, json.Unmarshal(broker.published[0], &response))
	require.Equal(t, "approved", response.Decision)
	input, err := responseSigningInput(response, request, identityRepo.identity.PublicKey)
	require.NoError(t, err)
	signature, err := decodeRaw(response.Signature, ed25519.SignatureSize, "signature")
	require.NoError(t, err)
	require.True(t, ed25519.Verify(identityRepo.identity.PublicKey, input, signature))
}

func TestTrustedDeviceIsAutomaticallyApprovedOnce(t *testing.T) {
	identityRepo := &memoryIdentityRepo{}
	devices := &memoryDeviceRepo{devices: map[string]*domain.TrustedMobileDevice{}}
	keys := &memoryKeyStore{}
	broker := &testBroker{}
	svc := NewService(identityRepo, devices, keys, broker, nil, &memoryPairingSettings{settings: &domain.AppSettings{}}, requireLogger(t))
	require.NoError(t, svc.start(context.Background()))
	_, mobilePrivate, err := ed25519.GenerateKey(rand.Reader)
	require.NoError(t, err)
	request := signedRequest(t, identityRepo.identity, mobilePrivate)
	public, _ := decodeRaw(request.MobilePublicKey, ed25519.PublicKeySize, "key")
	devices.devices[request.MobileID] = &domain.TrustedMobileDevice{DeviceID: request.MobileID, PublicKey: public}
	payload, err := json.Marshal(request)
	require.NoError(t, err)
	broker.handler(payload)
	broker.handler(payload)
	require.Len(t, broker.published, 1)
}

func TestNormalizeDesktopNameUsesSafeFallback(t *testing.T) {
	require.Equal(t, "Studio Mac", normalizeDesktopName(" Studio Mac "))
	require.Equal(t, "Airmedy Desktop", normalizeDesktopName("bad\nname"))
	require.Equal(t, "Airmedy Desktop", normalizeDesktopName(strings.Repeat("a", 65)))
}

func TestStartCachesAndReusesPairingMQTTPort(t *testing.T) {
	identityRepo := &memoryIdentityRepo{}
	devices := &memoryDeviceRepo{devices: map[string]*domain.TrustedMobileDevice{}}
	keys := &memoryKeyStore{}
	settings := &memoryPairingSettings{settings: &domain.AppSettings{}}

	firstBroker := &testBroker{}
	first := NewService(identityRepo, devices, keys, firstBroker, nil, settings, requireLogger(t))
	require.NoError(t, first.start(context.Background()))
	require.Equal(t, []int{0}, firstBroker.ports)
	require.Equal(t, 54321, settings.settings.PairingMQTTPort)
	require.Equal(t, 1, settings.saves)

	secondBroker := &testBroker{}
	second := NewService(identityRepo, devices, keys, secondBroker, nil, settings, requireLogger(t))
	require.NoError(t, second.start(context.Background()))
	require.Equal(t, []int{54321}, secondBroker.ports)
	require.Equal(t, 1, settings.saves)
}

func TestStartReplacesUnavailableCachedPairingMQTTPort(t *testing.T) {
	identityRepo := &memoryIdentityRepo{}
	devices := &memoryDeviceRepo{devices: map[string]*domain.TrustedMobileDevice{}}
	keys := &memoryKeyStore{}
	settings := &memoryPairingSettings{settings: &domain.AppSettings{PairingMQTTPort: 42000}}
	broker := &testBroker{failures: map[int]error{42000: errors.New("address already in use")}}
	svc := NewService(identityRepo, devices, keys, broker, nil, settings, requireLogger(t))

	require.NoError(t, svc.start(context.Background()))
	require.Equal(t, []int{42000, 0}, broker.ports)
	require.Equal(t, 54321, settings.settings.PairingMQTTPort)
	require.Equal(t, 1, settings.saves)
}

func TestPairingBroadcastAdvertisesEndpointAndCanBeStopped(t *testing.T) {
	identityRepo := &memoryIdentityRepo{}
	broker := &testBroker{}
	advertiser := &testAdvertiser{}
	svc := NewService(identityRepo, &memoryDeviceRepo{devices: map[string]*domain.TrustedMobileDevice{}}, &memoryKeyStore{}, broker, advertiser, &memoryPairingSettings{settings: &domain.AppSettings{}}, requireLogger(t))
	received := 0
	svc.AddBroadcastListener(func() { received++ })
	require.NoError(t, svc.StartBroadcast(context.Background()))

	status := svc.GetStatus()
	require.True(t, status.Broadcasting)
	require.WithinDuration(t, time.Now().Add(broadcastTTL), status.BroadcastUntil, time.Second)
	require.Len(t, advertiser.calls, 1)
	require.Equal(t, identityRepo.identity.DeviceID, advertiser.calls[0].deviceID)
	require.Equal(t, 54321, advertiser.calls[0].port)

	svc.StopBroadcast()
	require.False(t, svc.GetStatus().Broadcasting)
	require.Equal(t, 1, advertiser.stops)
	require.Equal(t, 2, received)
}

func TestPairingBroadcastRestartsWithFreshEndpointRecord(t *testing.T) {
	identityRepo := &memoryIdentityRepo{}
	advertiser := &testAdvertiser{}
	svc := NewService(identityRepo, &memoryDeviceRepo{devices: map[string]*domain.TrustedMobileDevice{}}, &memoryKeyStore{}, &testBroker{}, advertiser, &memoryPairingSettings{settings: &domain.AppSettings{}}, requireLogger(t))
	require.NoError(t, svc.StartBroadcast(context.Background()))
	require.NoError(t, svc.StartBroadcast(context.Background()))
	require.Len(t, advertiser.calls, 2)
	require.Equal(t, 1, advertiser.stops)
	svc.StopBroadcast()
}

func TestTrustedDeviceOnlineStateFollowsMQTTSession(t *testing.T) {
	deviceID := uuid.NewString()
	devices := &memoryDeviceRepo{devices: map[string]*domain.TrustedMobileDevice{
		deviceID: {DeviceID: deviceID},
	}}
	svc := NewService(&memoryIdentityRepo{}, devices, &memoryKeyStore{}, &testBroker{}, nil, &memoryPairingSettings{settings: &domain.AppSettings{}}, requireLogger(t))

	svc.setDeviceConnection(deviceID, true)
	trusted, err := svc.ListTrustedDevices(context.Background())
	require.NoError(t, err)
	require.Len(t, trusted, 1)
	require.True(t, trusted[0].Online)

	svc.setDeviceConnection(deviceID, false)
	trusted, err = svc.ListTrustedDevices(context.Background())
	require.NoError(t, err)
	require.False(t, trusted[0].Online)
}

func TestRevokeDeviceDisconnectsItsActiveSyncSession(t *testing.T) {
	deviceID := uuid.NewString()
	devices := &memoryDeviceRepo{devices: map[string]*domain.TrustedMobileDevice{
		deviceID: {DeviceID: deviceID},
	}}
	broker := &testBroker{}
	svc := NewService(&memoryIdentityRepo{}, devices, &memoryKeyStore{}, broker, nil, &memoryPairingSettings{settings: &domain.AppSettings{}}, requireLogger(t))
	svc.setDeviceConnection(deviceID, true)

	require.NoError(t, svc.RevokeDevice(context.Background(), deviceID))
	require.NotContains(t, devices.devices, deviceID)
	require.Equal(t, []string{deviceID}, broker.disconnected)
	require.False(t, svc.online[deviceID])
}
