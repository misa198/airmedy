package sqlite

import (
	"context"
	"database/sql"
	"fmt"

	"airmedy/internal/domain"
)

type favoriteMutationLedgerRepository struct{ db *DB }

func NewFavoriteMutationLedger(db *DB) domain.FavoriteMutationLedger {
	return &favoriteMutationLedgerRepository{db: db}
}

func (r *favoriteMutationLedgerRepository) Get(ctx context.Context, deviceID, mutationID string) (*domain.PlaylistMutationLedgerEntry, error) {
	var entry domain.PlaylistMutationLedgerEntry
	err := r.db.Ext(ctx).GetContext(ctx, &entry, `SELECT device_id, mutation_id, result, created_at FROM mobile_favorite_mutation_ledger WHERE device_id=? AND mutation_id=?`, deviceID, mutationID)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get favorite mutation ledger: %w", err)
	}
	return &entry, nil
}

func (r *favoriteMutationLedgerRepository) Save(ctx context.Context, entry domain.PlaylistMutationLedgerEntry) error {
	_, err := r.db.Ext(ctx).ExecContext(ctx, `INSERT OR IGNORE INTO mobile_favorite_mutation_ledger (device_id, mutation_id, result, created_at) VALUES (?, ?, ?, ?)`, entry.DeviceID, entry.MutationID, entry.Result, entry.CreatedAt)
	if err != nil {
		return fmt.Errorf("save favorite mutation ledger: %w", err)
	}
	return nil
}

type favoriteMutationLWWRepository struct{ db *DB }

func NewFavoriteMutationLWW(db *DB) domain.FavoriteMutationLWW {
	return &favoriteMutationLWWRepository{db: db}
}

func (r *favoriteMutationLWWRepository) Claim(ctx context.Context, trackID string, updatedAt int64, mutationID string, favorite bool) (bool, error) {
	result, err := r.db.Ext(ctx).ExecContext(ctx, `
		INSERT INTO mobile_favorite_mutation_lww (track_id, updated_at, mutation_id, is_favorite)
		VALUES (?, ?, ?, ?)
		ON CONFLICT(track_id) DO UPDATE SET updated_at=excluded.updated_at, mutation_id=excluded.mutation_id, is_favorite=excluded.is_favorite
		WHERE excluded.updated_at > mobile_favorite_mutation_lww.updated_at
		   OR (excluded.updated_at = mobile_favorite_mutation_lww.updated_at AND excluded.mutation_id > mobile_favorite_mutation_lww.mutation_id)`, trackID, updatedAt, mutationID, favorite)
	if err != nil {
		return false, fmt.Errorf("claim favorite mutation LWW: %w", err)
	}
	changed, err := result.RowsAffected()
	return changed == 1, err
}
