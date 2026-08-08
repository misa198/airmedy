package sqlite

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"fmt"
	"strings"
	"time"

	"airmedy/internal/domain"
)

type pairingIdentityRepository struct{ db *DB }
type trustedMobileDeviceRepository struct{ db *DB }

func pairingFingerprint(publicKey []byte) string {
	sum := sha256.Sum256(publicKey)
	hex := fmt.Sprintf("%X", sum[:])
	return strings.Join([]string{hex[0:4], hex[4:8], hex[8:12], hex[12:16], hex[16:20], hex[20:24]}, "-")
}

func NewPairingIdentityRepository(db *DB) *pairingIdentityRepository {
	return &pairingIdentityRepository{db: db}
}
func NewTrustedMobileDeviceRepository(db *DB) *trustedMobileDeviceRepository {
	return &trustedMobileDeviceRepository{db: db}
}

func (r *pairingIdentityRepository) Load(ctx context.Context) (*domain.PairingIdentity, error) {
	var identity domain.PairingIdentity
	err := r.db.QueryRowxContext(ctx, `SELECT device_id, public_key FROM pairing_identity WHERE id = 1`).Scan(&identity.DeviceID, &identity.PublicKey)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("load pairing identity: %w", err)
	}
	return &identity, nil
}

func (r *pairingIdentityRepository) Save(ctx context.Context, identity *domain.PairingIdentity) error {
	_, err := r.db.ExecContext(ctx, `INSERT INTO pairing_identity (id, device_id, public_key) VALUES (1, ?, ?) ON CONFLICT(id) DO UPDATE SET device_id = excluded.device_id, public_key = excluded.public_key`, identity.DeviceID, identity.PublicKey)
	if err != nil {
		return fmt.Errorf("save pairing identity: %w", err)
	}
	return nil
}

func (r *trustedMobileDeviceRepository) List(ctx context.Context) ([]*domain.TrustedMobileDevice, error) {
	var devices []*domain.TrustedMobileDevice
	if err := r.db.SelectContext(ctx, &devices, `SELECT device_id, public_key, display_name, platform, paired_at, last_seen_at FROM paired_mobile_devices ORDER BY last_seen_at DESC, display_name COLLATE NOCASE`); err != nil {
		return nil, fmt.Errorf("list paired mobile devices: %w", err)
	}
	for _, device := range devices {
		device.Fingerprint = pairingFingerprint(device.PublicKey)
	}
	return devices, nil
}

func (r *trustedMobileDeviceRepository) GetByDeviceID(ctx context.Context, deviceID string) (*domain.TrustedMobileDevice, error) {
	var device domain.TrustedMobileDevice
	err := r.db.QueryRowxContext(ctx, `SELECT device_id, public_key, display_name, platform, paired_at, last_seen_at FROM paired_mobile_devices WHERE device_id = ?`, deviceID).Scan(&device.DeviceID, &device.PublicKey, &device.DisplayName, &device.Platform, &device.PairedAt, &device.LastSeenAt)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get paired mobile device: %w", err)
	}
	device.Fingerprint = pairingFingerprint(device.PublicKey)
	return &device, nil
}

func (r *trustedMobileDeviceRepository) Save(ctx context.Context, device *domain.TrustedMobileDevice) error {
	_, err := r.db.ExecContext(ctx, `INSERT INTO paired_mobile_devices (device_id, public_key, display_name, platform, paired_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(device_id) DO UPDATE SET public_key = excluded.public_key, display_name = excluded.display_name, platform = excluded.platform, last_seen_at = excluded.last_seen_at`, device.DeviceID, device.PublicKey, device.DisplayName, device.Platform, device.PairedAt, device.LastSeenAt)
	if err != nil {
		return fmt.Errorf("save paired mobile device: %w", err)
	}
	return nil
}

func (r *trustedMobileDeviceRepository) Touch(ctx context.Context, deviceID string, at time.Time) error {
	_, err := r.db.ExecContext(ctx, `UPDATE paired_mobile_devices SET last_seen_at = ? WHERE device_id = ?`, at, deviceID)
	if err != nil {
		return fmt.Errorf("touch paired mobile device: %w", err)
	}
	return nil
}

func (r *trustedMobileDeviceRepository) Delete(ctx context.Context, deviceID string) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM paired_mobile_devices WHERE device_id = ?`, deviceID)
	if err != nil {
		return fmt.Errorf("delete paired mobile device: %w", err)
	}
	return nil
}
