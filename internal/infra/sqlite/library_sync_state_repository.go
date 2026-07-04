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

func (r *librarySyncStateRepository) GetMetadataSchemaVersion(ctx context.Context) (int, error) {
	var version int
	err := r.db.GetContext(ctx, &version,
		`SELECT metadata_schema_version FROM library_sync_state WHERE id = 1`,
	)
	if err == sql.ErrNoRows {
		return 0, nil
	}
	if err != nil {
		return 0, fmt.Errorf("failed to load metadata schema version: %w", err)
	}
	return version, nil
}

func (r *librarySyncStateRepository) SetMetadataSchemaVersion(ctx context.Context, version int) error {
	_, err := r.db.ExecContext(ctx,
		`INSERT INTO library_sync_state (id, metadata_schema_version, updated_at)
		 VALUES (1, ?, CURRENT_TIMESTAMP)
		 ON CONFLICT(id) DO UPDATE SET
		   metadata_schema_version = excluded.metadata_schema_version,
		   updated_at = excluded.updated_at`,
		version,
	)
	if err != nil {
		return fmt.Errorf("failed to save metadata schema version: %w", err)
	}
	return nil
}
