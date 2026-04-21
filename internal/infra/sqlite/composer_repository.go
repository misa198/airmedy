package sqlite

import (
	"context"
	"database/sql"
	"fmt"

	"changeme/internal/domain"
)

type composerRepository struct {
	db *DB
}

func NewComposerRepository(db *DB) domain.ComposerRepository {
	return &composerRepository{db: db}
}

func (r *composerRepository) GetByID(ctx context.Context, id string) (*domain.Composer, error) {
	var c domain.Composer
	err := r.db.GetContext(ctx, &c, "SELECT * FROM composers WHERE id = ?", id)
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
	err := r.db.GetContext(ctx, &c, "SELECT * FROM composers WHERE name = ?", name)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get composer by name: %w", err)
	}
	return &c, nil
}

func (r *composerRepository) GetAll(ctx context.Context) ([]*domain.Composer, error) {
	var composers []*domain.Composer
	err := r.db.SelectContext(ctx, &composers, "SELECT * FROM composers ORDER BY name")
	if err != nil {
		return nil, fmt.Errorf("failed to get all composers: %w", err)
	}
	return composers, nil
}

func (r *composerRepository) Save(ctx context.Context, c *domain.Composer) error {
	_, err := r.db.NamedExecContext(ctx, "INSERT INTO composers (id, name) VALUES (:id, :name)", c)
	if err != nil {
		return fmt.Errorf("failed to save composer: %w", err)
	}
	return nil
}

func (r *composerRepository) Upsert(ctx context.Context, c *domain.Composer) error {
	_, err := r.db.NamedExecContext(ctx, "INSERT INTO composers (id, name) VALUES (:id, :name) ON CONFLICT(name) DO UPDATE SET id = id", c)
	if err != nil {
		return fmt.Errorf("failed to upsert composer: %w", err)
	}
	return nil
}
