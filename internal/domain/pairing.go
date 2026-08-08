package domain

import (
	"context"
	"crypto/ed25519"
	"time"
)

// PairingIdentity is the stable public identity advertised by this desktop.
// Its private half is deliberately held outside SQLite by PairingKeyStore.
type PairingIdentity struct {
	DeviceID  string
	PublicKey []byte
}

// TrustedMobileDevice is a mobile device explicitly accepted by the desktop user.
type TrustedMobileDevice struct {
	DeviceID    string    `json:"device_id" db:"device_id"`
	PublicKey   []byte    `json:"-" db:"public_key"`
	Fingerprint string    `json:"fingerprint" db:"-"`
	DisplayName string    `json:"display_name" db:"display_name"`
	Platform    string    `json:"platform" db:"platform"`
	PairedAt    time.Time `json:"paired_at" db:"paired_at"`
	LastSeenAt  time.Time `json:"last_seen_at" db:"last_seen_at"`
	Online      bool      `json:"online" db:"-"`
}

type PairingIdentityRepository interface {
	Load(ctx context.Context) (*PairingIdentity, error)
	Save(ctx context.Context, identity *PairingIdentity) error
}

type TrustedMobileDeviceRepository interface {
	List(ctx context.Context) ([]*TrustedMobileDevice, error)
	GetByDeviceID(ctx context.Context, deviceID string) (*TrustedMobileDevice, error)
	Save(ctx context.Context, device *TrustedMobileDevice) error
	Touch(ctx context.Context, deviceID string, at time.Time) error
	Delete(ctx context.Context, deviceID string) error
}

// PairingKeyStore holds only the desktop Ed25519 private key in an OS secure vault.
type PairingKeyStore interface {
	Load(ctx context.Context) (ed25519.PrivateKey, bool, error)
	Save(ctx context.Context, key ed25519.PrivateKey) error
}

// PairingBroker is the small transport boundary used by the pairing use case.
// The adapter is responsible for MQTT ACLs and only forwards request-topic payloads.
type PairingBroker interface {
	// Start binds preferredPort when it is non-zero; zero selects an ephemeral port.
	Start(ctx context.Context, desktopID string, preferredPort int, onRequest func([]byte), onDeviceConnection func(deviceID string, connected bool)) (port int, err error)
	Stop(ctx context.Context) error
	// Disconnect removes an active trusted-device sync session, if present.
	Disconnect(ctx context.Context, deviceID string) error
	Publish(ctx context.Context, topic string, payload []byte) error
	// Subscribe registers an internal desktop-side handler for a narrow topic.
	// It is used by separately-versioned authenticated protocols after pairing.
	Subscribe(ctx context.Context, topic string, handler func([]byte)) error
	Running() bool
}

// PairingAdvertiser publishes a short-lived LAN discovery record for the
// desktop pairing broker. The returned stop function must be safe to call once.
type PairingAdvertiser interface {
	Advertise(ctx context.Context, deviceID string, port int) (stop func(), err error)
}
