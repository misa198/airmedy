package sqlite

import (
	"context"
	"database/sql"
	"fmt"

	"airmedy/internal/domain"
)

type playlistMutationLedgerRepository struct{ db *DB }

func NewPlaylistMutationLedger(db *DB) domain.PlaylistMutationLedger {
	return &playlistMutationLedgerRepository{db: db}
}

func (r *playlistMutationLedgerRepository) Get(ctx context.Context, deviceID, mutationID string) (*domain.PlaylistMutationLedgerEntry, error) {
	var entry domain.PlaylistMutationLedgerEntry
	err := r.db.Ext(ctx).GetContext(ctx, &entry, `SELECT device_id, mutation_id, result, created_at FROM mobile_playlist_mutation_ledger WHERE device_id = ? AND mutation_id = ?`, deviceID, mutationID)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get playlist mutation ledger: %w", err)
	}
	return &entry, nil
}

func (r *playlistMutationLedgerRepository) Save(ctx context.Context, entry domain.PlaylistMutationLedgerEntry) error {
	_, err := r.db.Ext(ctx).ExecContext(ctx, `INSERT OR IGNORE INTO mobile_playlist_mutation_ledger (device_id, mutation_id, result, created_at) VALUES (?, ?, ?, ?)`, entry.DeviceID, entry.MutationID, entry.Result, entry.CreatedAt)
	if err != nil {
		return fmt.Errorf("save playlist mutation ledger: %w", err)
	}
	return nil
}
