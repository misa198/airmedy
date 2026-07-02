package sqlite

import (
	"context"
	"database/sql"
	"fmt"

	"airmedy/internal/domain"
)

type composerRepository struct {
	db *DB
}

func NewComposerRepository(db *DB) domain.ComposerRepository {
	return &composerRepository{db: db}
}

func (r *composerRepository) GetByID(ctx context.Context, id string) (*domain.Composer, error) {
	var c domain.Composer
	err := r.db.Ext(ctx).GetContext(ctx, &c, "SELECT * FROM composers WHERE id = ?", id)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get composer by id: %w", err)
	}
	return &c, nil
}

func (r *composerRepository) GetByName(ctx context.Context, name string) (*domain.Composer, error) {
	var c domain.Composer
	err := r.db.Ext(ctx).GetContext(ctx, &c, "SELECT * FROM composers WHERE name = ?", name)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get composer by name: %w", err)
	}
	return &c, nil
}

func (r *composerRepository) GetByNormalizationKey(ctx context.Context, key string) (*domain.Composer, error) {
	var c domain.Composer
	err := r.db.Ext(ctx).GetContext(ctx, &c, "SELECT * FROM composers WHERE normalization_key = ?", key)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get composer by normalization key: %w", err)
	}
	return &c, nil
}

func (r *composerRepository) GetAll(ctx context.Context) ([]*domain.Composer, error) {
	var composers []*domain.Composer
	err := r.db.Ext(ctx).SelectContext(ctx, &composers, "SELECT * FROM composers ORDER BY name")
	if err != nil {
		return nil, fmt.Errorf("failed to get all composers: %w", err)
	}
	return composers, nil
}

func (r *composerRepository) Save(ctx context.Context, c *domain.Composer) error {
	_, err := r.db.Ext(ctx).NamedExecContext(ctx, "INSERT INTO composers (id, name, normalization_key) VALUES (:id, :name, :normalization_key)", c)
	if err != nil {
		return fmt.Errorf("failed to save composer: %w", err)
	}
	return nil
}

func (r *composerRepository) Upsert(ctx context.Context, c *domain.Composer) error {
	query := `
		INSERT INTO composers (id, name, normalization_key) 
		VALUES (:id, :name, :normalization_key) 
		ON CONFLICT(id) DO UPDATE SET 
			normalization_key = excluded.normalization_key
	`
	_, err := r.db.Ext(ctx).NamedExecContext(ctx, query, c)
	if err != nil {
		return fmt.Errorf("failed to upsert composer: %w", err)
	}
	return nil
}

func (r *composerRepository) DeleteOrphaned(ctx context.Context) ([]string, error) {
	// Clean up orphaned junction rows that might exist from before foreign keys were enabled
	_, _ = r.db.Ext(ctx).ExecContext(ctx, "DELETE FROM track_composers WHERE track_id NOT IN (SELECT id FROM tracks)")

	const cond = `id NOT IN (SELECT composer_id FROM track_composers)`
	var ids []string
	if err := r.db.Ext(ctx).SelectContext(ctx, &ids, `SELECT id FROM composers WHERE `+cond); err != nil {
		return nil, fmt.Errorf("failed to select orphaned composers: %w", err)
	}
	if _, err := r.db.Ext(ctx).ExecContext(ctx, `DELETE FROM composers WHERE `+cond); err != nil {
		return nil, fmt.Errorf("failed to delete orphaned composers: %w", err)
	}
	return ids, nil
}
