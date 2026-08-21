package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"airmedy/internal/domain"
)

type playlistMutationLWWRepository struct{ db *DB }

func NewPlaylistMutationLWW(db *DB) domain.PlaylistMutationLWW {
	return &playlistMutationLWWRepository{db: db}
}

func (r *playlistMutationLWWRepository) Claim(ctx context.Context, playlistID string, updatedAt int64, mutationID string, deleted bool) (bool, error) {
	result, err := r.db.Ext(ctx).ExecContext(ctx, `
		INSERT INTO mobile_playlist_mutation_lww (playlist_id, updated_at, mutation_id, deleted)
		VALUES (?, ?, ?, ?)
		ON CONFLICT(playlist_id) DO UPDATE SET updated_at = excluded.updated_at, mutation_id = excluded.mutation_id, deleted = excluded.deleted
		WHERE excluded.updated_at > mobile_playlist_mutation_lww.updated_at
		   OR (excluded.updated_at = mobile_playlist_mutation_lww.updated_at AND excluded.mutation_id > mobile_playlist_mutation_lww.mutation_id)`,
		playlistID, updatedAt, mutationID, deleted)
	if err != nil {
		return false, fmt.Errorf("claim playlist mutation LWW: %w", err)
	}
	changed, err := result.RowsAffected()
	return changed == 1, err
}

type playlistArtworkStagingRepository struct{ db *DB }

func NewPlaylistArtworkStagingRepository(db *DB) domain.PlaylistArtworkStagingRepository {
	return &playlistArtworkStagingRepository{db: db}
}
func (r *playlistArtworkStagingRepository) Save(ctx context.Context, e domain.PlaylistArtworkStaging) error {
	_, err := r.db.Ext(ctx).ExecContext(ctx, `INSERT OR REPLACE INTO mobile_playlist_artwork_staging (reconciliation_id, device_id, sha256, artwork_key, expires_at) VALUES (?, ?, ?, ?, ?)`, e.ReconciliationID, e.DeviceID, e.SHA256, e.ArtworkKey, e.ExpiresAt)
	if err != nil {
		return fmt.Errorf("save playlist artwork staging: %w", err)
	}
	return nil
}
func (r *playlistArtworkStagingRepository) Get(ctx context.Context, rid, did, hash string) (*domain.PlaylistArtworkStaging, error) {
	var e domain.PlaylistArtworkStaging
	err := r.db.Ext(ctx).GetContext(ctx, &e, `SELECT reconciliation_id, device_id, sha256, artwork_key, expires_at FROM mobile_playlist_artwork_staging WHERE reconciliation_id=? AND device_id=? AND sha256=?`, rid, did, hash)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get playlist artwork staging: %w", err)
	}
	return &e, nil
}
func (r *playlistArtworkStagingRepository) DeleteExpired(ctx context.Context, now time.Time) ([]string, error) {
	return r.delete(ctx, `DELETE FROM mobile_playlist_artwork_staging WHERE expires_at <= ?`, now)
}
func (r *playlistArtworkStagingRepository) DeleteReconciliation(ctx context.Context, rid, did string) ([]string, error) {
	return r.delete(ctx, `DELETE FROM mobile_playlist_artwork_staging WHERE reconciliation_id=? AND device_id=?`, rid, did)
}
func (r *playlistArtworkStagingRepository) delete(ctx context.Context, query string, args ...any) ([]string, error) {
	var keys []string
	if err := r.db.Ext(ctx).SelectContext(ctx, &keys, "SELECT artwork_key FROM mobile_playlist_artwork_staging WHERE "+query[len("DELETE FROM mobile_playlist_artwork_staging WHERE "):], args...); err != nil {
		return nil, err
	}
	if _, err := r.db.Ext(ctx).ExecContext(ctx, query, args...); err != nil {
		return nil, err
	}
	return keys, nil
}
func (r *playlistArtworkStagingRepository) ActiveArtworkKeys(ctx context.Context, now time.Time) ([]string, error) {
	var keys []string
	err := r.db.Ext(ctx).SelectContext(ctx, &keys, `SELECT artwork_key FROM mobile_playlist_artwork_staging WHERE expires_at > ?`, now)
	return keys, err
}
