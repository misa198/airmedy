package sqlite

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"airmedy/internal/domain"
	"github.com/stretchr/testify/require"
	"log/slog"
)

func TestPlaylistMutationLWWIsDurableAndDeterministic(t *testing.T) {
	path := filepath.Join(t.TempDir(), "library.db")
	db, err := NewDB(path, slog.Default())
	require.NoError(t, err)
	repo := NewPlaylistMutationLWW(db)
	won, err := repo.Claim(context.Background(), "playlist", 100, "a", false)
	require.NoError(t, err)
	require.True(t, won)
	won, err = repo.Claim(context.Background(), "playlist", 100, "a", false)
	require.NoError(t, err)
	require.False(t, won)
	won, err = repo.Claim(context.Background(), "playlist", 100, "b", true)
	require.NoError(t, err)
	require.True(t, won)
	_ = db.Close()

	db, err = NewDB(path, slog.Default())
	require.NoError(t, err)
	won, err = NewPlaylistMutationLWW(db).Claim(context.Background(), "playlist", 99, "z", false)
	require.NoError(t, err)
	require.False(t, won, "DELETE watermark must survive restart")
}

func TestPlaylistArtworkStagingOwnershipExpiryAndPersistence(t *testing.T) {
	path := filepath.Join(t.TempDir(), "library.db")
	db, err := NewDB(path, slog.Default())
	require.NoError(t, err)
	deviceID := "11111111-1111-4111-8111-111111111111"
	_, err = db.Exec(`INSERT INTO paired_mobile_devices (device_id, public_key, display_name, platform, paired_at, last_seen_at) VALUES (?, ?, 'Phone', 'Android', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)`, deviceID, make([]byte, 32))
	require.NoError(t, err)
	repo := NewPlaylistArtworkStagingRepository(db)
	now := time.Now().UTC()
	require.NoError(t, repo.Save(context.Background(), domain.PlaylistArtworkStaging{ReconciliationID: "r1", DeviceID: deviceID, SHA256: "hash1", ArtworkKey: "one.png", ExpiresAt: now.Add(time.Minute)}))
	require.NoError(t, repo.Save(context.Background(), domain.PlaylistArtworkStaging{ReconciliationID: "r2", DeviceID: deviceID, SHA256: "hash2", ArtworkKey: "two.png", ExpiresAt: now.Add(-time.Minute)}))
	require.Nil(t, mustGetArtworkStage(t, repo, "r1", "other", "hash1"))
	require.NotNil(t, mustGetArtworkStage(t, repo, "r1", deviceID, "hash1"))
	deleted, err := repo.DeleteExpired(context.Background(), now)
	require.NoError(t, err)
	require.Equal(t, []string{"two.png"}, deleted)
	keys, err := repo.ActiveArtworkKeys(context.Background(), now)
	require.NoError(t, err)
	require.Equal(t, []string{"one.png"}, keys)
	require.NoError(t, db.Close())
	db, err = NewDB(path, slog.Default())
	require.NoError(t, err)
	t.Cleanup(func() { require.NoError(t, db.Close()) })
	require.NotNil(t, mustGetArtworkStage(t, NewPlaylistArtworkStagingRepository(db), "r1", deviceID, "hash1"))
}

func mustGetArtworkStage(t *testing.T, repo domain.PlaylistArtworkStagingRepository, rid, did, hash string) *domain.PlaylistArtworkStaging {
	t.Helper()
	entry, err := repo.Get(context.Background(), rid, did, hash)
	require.NoError(t, err)
	return entry
}
