package sqlite

import (
	"context"
	"database/sql"
	"fmt"

	"changeme/internal/domain"
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

func (r *genreRepository) GetAll(ctx context.Context) ([]*domain.Genre, error) {
	var genres []*domain.Genre
	err := r.db.SelectContext(ctx, &genres, "SELECT * FROM genres ORDER BY name")
	if err != nil {
		return nil, fmt.Errorf("failed to get all genres: %w", err)
	}
	return genres, nil
}

func (r *genreRepository) Save(ctx context.Context, g *domain.Genre) error {
	_, err := r.db.NamedExecContext(ctx, "INSERT INTO genres (id, name) VALUES (:id, :name)", g)
	if err != nil {
		return fmt.Errorf("failed to save genre: %w", err)
	}
	return nil
}

func (r *genreRepository) Upsert(ctx context.Context, g *domain.Genre) error {
	_, err := r.db.NamedExecContext(ctx, "INSERT INTO genres (id, name) VALUES (:id, :name) ON CONFLICT(name) DO UPDATE SET id = id", g)
	if err != nil {
		return fmt.Errorf("failed to upsert genre: %w", err)
	}
	return nil
}
