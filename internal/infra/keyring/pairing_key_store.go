package keyring

import (
	"context"
	"crypto/ed25519"
	"encoding/base64"
	"fmt"

	"github.com/zalando/go-keyring"
)

const (
	pairingServiceName = "airmedy"
	pairingKeyName     = "mobile_pairing_ed25519_private_key_v1"
)

// PairingKeyStore keeps the desktop pairing private key out of the application database.
type PairingKeyStore struct{}

func NewPairingKeyStore() *PairingKeyStore { return &PairingKeyStore{} }

func (s *PairingKeyStore) Load(_ context.Context) (ed25519.PrivateKey, bool, error) {
	encoded, err := keyring.Get(pairingServiceName, pairingKeyName)
	if err == keyring.ErrNotFound {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, fmt.Errorf("load pairing key from keyring: %w", err)
	}
	key, err := base64.RawURLEncoding.DecodeString(encoded)
	if err != nil || len(key) != ed25519.PrivateKeySize {
		return nil, false, fmt.Errorf("decode pairing key from keyring: invalid key material")
	}
	return ed25519.PrivateKey(key), true, nil
}

func (s *PairingKeyStore) Save(_ context.Context, key ed25519.PrivateKey) error {
	if len(key) != ed25519.PrivateKeySize {
		return fmt.Errorf("save pairing key: invalid Ed25519 private key")
	}
	if err := keyring.Set(pairingServiceName, pairingKeyName, base64.RawURLEncoding.EncodeToString(key)); err != nil {
		return fmt.Errorf("save pairing key to keyring: %w", err)
	}
	return nil
}
