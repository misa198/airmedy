package sqlite

import (
	"context"
	"database/sql"
	"fmt"

	"airmedy/internal/domain"
)

type genreRepository struct {
	db *DB
}

func NewGenreRepository(db *DB) domain.GenreRepository {
	return &genreRepository{db: db}
}

func (r *genreRepository) GetByID(ctx context.Context, id string) (*domain.Genre, error) {
	var g domain.Genre
	err := r.db.GetContext(ctx, &g, "SELECT * FROM genres WHERE id = ?", id)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get genre by id: %w", err)
	}
	return &g, nil
}

func (r *genreRepository) GetByName(ctx context.Context, name string) (*domain.Genre, error) {
	var g domain.Genre
	err := r.db.GetContext(ctx, &g, "SELECT * FROM genres WHERE name = ?", name)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get genre by name: %w", err)
	}
	return &g, nil
}

func (r *genreRepository) GetByNormalizationKey(ctx context.Context, key string) (*domain.Genre, error) {
	var g domain.Genre
	err := r.db.GetContext(ctx, &g, "SELECT * FROM genres WHERE normalization_key = ?", key)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get genre by normalization key: %w", err)
	}
	return &g, nil
}

func (r *genreRepository) GetAll(ctx context.Context) ([]*domain.Genre, error) {
	var genres []*domain.Genre
	err := r.db.SelectContext(ctx, &genres, "SELECT * FROM genres ORDER BY name")
	if err != nil {
		return nil, fmt.Errorf("failed to get all genres: %w", err)
	}
	return genres, nil
}

func (r *genreRepository) Save(ctx context.Context, g *domain.Genre) error {
	_, err := r.db.NamedExecContext(ctx, "INSERT INTO genres (id, name, normalization_key) VALUES (:id, :name, :normalization_key)", g)
	if err != nil {
		return fmt.Errorf("failed to save genre: %w", err)
	}
	return nil
}

func (r *genreRepository) Upsert(ctx context.Context, g *domain.Genre) error {
	query := `
		INSERT INTO genres (id, name, normalization_key) 
		VALUES (:id, :name, :normalization_key) 
		ON CONFLICT(id) DO UPDATE SET 
			normalization_key = excluded.normalization_key
	`
	_, err := r.db.NamedExecContext(ctx, query, g)
	if err != nil {
		return fmt.Errorf("failed to upsert genre: %w", err)
	}
	return nil
}

func (r *genreRepository) DeleteOrphaned(ctx context.Context) error {
	// Clean up orphaned junction rows that might exist from before foreign keys were enabled
	_, _ = r.db.ExecContext(ctx, "DELETE FROM track_genres WHERE track_id NOT IN (SELECT id FROM tracks)")

	query := `DELETE FROM genres WHERE id NOT IN (SELECT genre_id FROM track_genres)`
	_, err := r.db.ExecContext(ctx, query)
	if err != nil {
		return fmt.Errorf("failed to delete orphaned genres: %w", err)
	}
	return nil
}
