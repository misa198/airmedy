package sqlite

import (
	"context"
	"database/sql"
	"fmt"

	"airmedy/internal/domain"
)

type librarySyncStateRepository struct {
	db *DB
}

func NewLibrarySyncStateRepository(db *DB) domain.LibrarySyncStateRepository {
	return &librarySyncStateRepository{db: db}
}

func (r *librarySyncStateRepository) GetDelimitersSignature(ctx context.Context) (string, error) {
	var sig string
	err := r.db.GetContext(ctx, &sig,
		`SELECT delimiters_signature FROM library_sync_state WHERE id = 1`,
	)
	if err == sql.ErrNoRows {
		return "", nil
	}
	if err != nil {
		return "", fmt.Errorf("failed to load delimiters signature: %w", err)
	}
	return sig, nil
}

func (r *librarySyncStateRepository) SetDelimitersSignature(ctx context.Context, sig string) error {
	_, err := r.db.ExecContext(ctx,
		`INSERT INTO library_sync_state (id, delimiters_signature, updated_at)
		 VALUES (1, ?, CURRENT_TIMESTAMP)
		 ON CONFLICT(id) DO UPDATE SET
		   delimiters_signature = excluded.delimiters_signature,
		   updated_at = excluded.updated_at`,
		sig,
	)
	if err != nil {
		return fmt.Errorf("failed to save delimiters signature: %w", err)
	}
	return nil
}
